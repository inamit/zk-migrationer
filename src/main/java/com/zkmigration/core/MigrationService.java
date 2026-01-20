package com.zkmigration.core;

import com.zkmigration.model.ChangeLog;
import com.zkmigration.model.ChangeLogEntry;
import com.zkmigration.model.ChangeSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MigrationService {
    private final CuratorFramework client;
    private final MigrationStateService stateService;
    private final MigrationExecutor executor;
    private final String lockPath;

    public MigrationService(CuratorFramework client, String rootPath) {
        this.client = client;
        String historyPath = rootPath + "/changelog";
        this.lockPath = rootPath + "/lock";
        this.stateService = new MigrationStateService(client, historyPath);
        this.executor = new MigrationExecutor(client);
    }

    public void update(ChangeLog changeLog, String executionEnvironment, List<String> executionLabels) throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, lockPath);

        if (!lock.acquire(60, TimeUnit.SECONDS)) {
            throw new RuntimeException("Could not acquire lock at " + lockPath);
        }

        try {
            log.info("Lock acquired. Checking for migrations...");
            Map<String, MigrationStateService.ExecutedChangeSet> executedMap = stateService.getExecutedChangeSets();
            Set<String> executedInThisRun = new HashSet<>();

            List<ChangeSet> changeSets = extractChangeSets(changeLog);

            for (ChangeSet cs : changeSets) {
                // Check for duplicate ID in current run
                if (executedInThisRun.contains(cs.getId())) {
                    throw new DuplicateChangeSetIdException("Duplicate ChangeSet ID detected in this run: " + cs.getId());
                }

                // Calculate Checksum
                String currentChecksum = ChecksumUtil.calculateChecksum(cs);

                // Check if already executed (in history)
                if (executedMap.containsKey(cs.getId())) {
                    MigrationStateService.ExecutedChangeSet executed = executedMap.get(cs.getId());

                    // Verify Checksum
                    verifyChecksum(cs, currentChecksum, executed.checksum);

                    log.debug("ChangeSet {} already executed. Skipping.", cs.getId());
                    // Even if skipped, we mark it as seen in this run to prevent duplicate ID re-use
                    executedInThisRun.add(cs.getId());
                    continue;
                }

                // Check Environment and Labels
                if (!shouldRun(cs, executionEnvironment, executionLabels, changeLog.getEnvironmentsGroups())) {
                    log.debug("ChangeSet {} ignored due to environment/label mismatch.", cs.getId());
                    continue;
                }

                log.info("Applying ChangeSet: {}", cs.getId());
                try {
                    executor.execute(cs);
                    stateService.markChangeSetExecuted(cs.getId(), cs.getAuthor(), "Executed by ZkMigration", currentChecksum);

                    // Add to tracked sets
                    executedInThisRun.add(cs.getId());
                    executedMap.put(cs.getId(), new MigrationStateService.ExecutedChangeSet(cs.getId(), cs.getAuthor(), System.currentTimeMillis(), currentChecksum));

                    log.info("ChangeSet {} applied successfully.", cs.getId());
                } catch (Exception e) {
                    log.error("Failed to apply ChangeSet {}", cs.getId(), e);
                    throw e;
                }
            }
        } finally {
            lock.release();
        }
    }

    public void rollback(ChangeLog changeLog, int count) throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, lockPath);

        if (!lock.acquire(60, TimeUnit.SECONDS)) {
            throw new RuntimeException("Could not acquire lock at " + lockPath);
        }

        try {
            log.info("Lock acquired. Processing rollback...");
            List<ChangeSet> toRollback = getChangesetsToRollback(changeLog, count);

            if (toRollback.isEmpty()) {
                log.info("No executed changesets found to rollback.");
                return;
            }

            for (ChangeSet cs : toRollback) {
                log.info("Rolling back ChangeSet: {}", cs.getId());
                try {
                    executor.rollback(cs);
                    stateService.removeChangeSetExecution(cs.getId());
                    log.info("ChangeSet {} rolled back successfully.", cs.getId());
                } catch (Exception e) {
                    log.error("Failed to rollback ChangeSet {}", cs.getId(), e);
                    throw e;
                }
            }

        } finally {
            lock.release();
        }
    }

    public boolean previewUpdate(ChangeLog changeLog, String executionEnvironment, List<String> executionLabels) throws Exception {
        Map<String, MigrationStateService.ExecutedChangeSet> executedMap = stateService.getExecutedChangeSets();
        Set<String> executedInThisRun = new HashSet<>();
        List<ChangeSet> changeSets = extractChangeSets(changeLog);

        boolean hasChanges = false;
        MigrationInspector inspector = new MigrationInspector(client);
        System.out.println("PREVIEW: UPCOMING MIGRATIONS");
        System.out.println("============================");

        for (ChangeSet cs : changeSets) {
            if (executedInThisRun.contains(cs.getId())) {
                System.out.println("DUPLICATE ID (preview): " + cs.getId());
                continue;
            }

            if (executedMap.containsKey(cs.getId())) {
                // Already executed, verify checksum
                MigrationStateService.ExecutedChangeSet executed = executedMap.get(cs.getId());
                String currentChecksum = ChecksumUtil.calculateChecksum(cs);
                try {
                    verifyChecksum(cs, currentChecksum, executed.checksum);
                } catch (RuntimeException e) {
                    System.out.println("VALIDATION ERROR: " + e.getMessage());
                }
                executedInThisRun.add(cs.getId());
                continue;
            }

            if (!shouldRun(cs, executionEnvironment, executionLabels, changeLog.getEnvironmentsGroups())) {
                continue;
            }

            // Pending ChangeSet
            System.out.println(inspector.inspect(cs, false));
            hasChanges = true;
            executedInThisRun.add(cs.getId());
        }

        if (!hasChanges) {
            System.out.println("No pending changes found.");
        }
        return hasChanges;
    }

    public boolean previewRollback(ChangeLog changeLog, int count) throws Exception {
        List<ChangeSet> toRollback = getChangesetsToRollback(changeLog, count);

        if (toRollback.isEmpty()) {
            System.out.println("No executed changesets found to rollback.");
            return false;
        }

        MigrationInspector inspector = new MigrationInspector(client);
        System.out.println("PREVIEW: ROLLBACK MIGRATIONS");
        System.out.println("============================");

        for (ChangeSet cs : toRollback) {
            System.out.println(inspector.inspect(cs, true));
        }
        return true;
    }

    private List<ChangeSet> getChangesetsToRollback(ChangeLog changeLog, int numberOfChangesetsToRollback) throws Exception {
        Map<String, MigrationStateService.ExecutedChangeSet> executedMap = stateService.getExecutedChangeSets();
        List<ChangeSet> changeSets = extractChangeSets(changeLog);

        List<ChangeSet> toRollback = new ArrayList<>();
        for (int i = changeSets.size() - 1; i >= 0; i--) {
            ChangeSet cs = changeSets.get(i);
            if (executedMap.containsKey(cs.getId())) {
                toRollback.add(cs);
                if (toRollback.size() >= numberOfChangesetsToRollback) {
                    break;
                }
            }
        }

        return toRollback;
    }

    private void verifyChecksum(ChangeSet cs, String currentChecksum, String storedChecksum) {
        if (storedChecksum == null) {
            log.warn("ChangeSet {} has no stored checksum. Skipping validation.", cs.getId());
            return;
        }

        if (storedChecksum.equals(currentChecksum)) {
            return;
        }

        // Check validCheckSum
        if (cs.getValidCheckSum() != null) {
            for (String valid : cs.getValidCheckSum()) {
                if (valid.equalsIgnoreCase(currentChecksum)) {
                    return; // Matches valid override
                }
            }
        }

        throw new RuntimeException(String.format("Validation Failed: Checksum mismatch for ChangeSet %s. Stored: %s, Calculated: %s",
                cs.getId(), storedChecksum, currentChecksum));
    }

    private boolean shouldRun(ChangeSet cs, String executionEnvironment, List<String> executionLabels, Map<String, List<String>> environmentsGroups) {
        // Environment Check
        boolean environmentMatch = false;

        if (cs.getEnvironments() != null) {
            for (String env : cs.getEnvironments()) {
                // 1. Check "All"
                if ("All".equalsIgnoreCase(env)) {
                    environmentMatch = true;
                    break;
                }

                // 2. Check direct match
                if (env.equalsIgnoreCase(executionEnvironment)) {
                    environmentMatch = true;
                    break;
                }

                // 3. Check Environments Group match
                if (environmentsGroups != null && environmentsGroups.containsKey(env)) {
                    List<String> groupMembers = environmentsGroups.get(env);
                    if (groupMembers != null && groupMembers.contains(executionEnvironment)) {
                        environmentMatch = true;
                        break;
                    }
                }
            }
        }

        if (!environmentMatch) {
            return false;
        }

        // Label Check
        if (executionLabels == null || executionLabels.isEmpty()) {
            return false;
        }

        if (cs.getLabels() != null) {
            for (String label : cs.getLabels()) {
                if (executionLabels.contains(label)) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<ChangeSet> extractChangeSets(ChangeLog changeLog) {
        List<ChangeSet> changeSets = new ArrayList<>();
        if (changeLog.getZookeeperChangeLog() != null) {
            for (ChangeLogEntry entry : changeLog.getZookeeperChangeLog()) {
                if (entry instanceof ChangeSet) {
                    changeSets.add((ChangeSet) entry);
                }
            }
        }
        return changeSets;
    }
}
