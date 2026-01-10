package com.zkmigration.core;

import com.zkmigration.model.ChangeSet;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MigrationService {
    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);
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

    public void update(List<ChangeSet> changeSets) throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, lockPath);
        if (!lock.acquire(60, TimeUnit.SECONDS)) {
            throw new RuntimeException("Could not acquire lock at " + lockPath);
        }
        try {
            logger.info("Lock acquired. Checking for migrations...");
            List<String> executedIds = stateService.getExecutedChangeSetIds();
            Set<String> executedSet = new HashSet<>(executedIds);

            for (ChangeSet cs : changeSets) {
                if (executedSet.contains(cs.getId())) {
                    logger.debug("ChangeSet {} already executed. Skipping.", cs.getId());
                    continue;
                }

                logger.info("Applying ChangeSet: {}", cs.getId());
                try {
                    executor.execute(cs);
                    stateService.markChangeSetExecuted(cs.getId(), cs.getAuthor(), "Executed by ZkMigration");
                    logger.info("ChangeSet {} applied successfully.", cs.getId());
                } catch (Exception e) {
                    logger.error("Failed to apply ChangeSet {}", cs.getId(), e);
                    throw e;
                }
            }
        } finally {
            lock.release();
        }
    }

    public void rollback(List<ChangeSet> changeSets, int count) throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, lockPath);
        if (!lock.acquire(60, TimeUnit.SECONDS)) {
            throw new RuntimeException("Could not acquire lock at " + lockPath);
        }
        try {
            logger.info("Lock acquired. Processing rollback...");
            List<String> executedIds = stateService.getExecutedChangeSetIds();
            Set<String> executedSet = new HashSet<>(executedIds);

            List<ChangeSet> toRollback = new ArrayList<>();
            // Iterate reverse
            for (int i = changeSets.size() - 1; i >= 0; i--) {
                ChangeSet cs = changeSets.get(i);
                if (executedSet.contains(cs.getId())) {
                    toRollback.add(cs);
                    if (toRollback.size() >= count) {
                        break;
                    }
                }
            }

            if (toRollback.isEmpty()) {
                logger.info("No executed changesets found to rollback.");
                return;
            }

            for (ChangeSet cs : toRollback) {
                logger.info("Rolling back ChangeSet: {}", cs.getId());
                try {
                    executor.rollback(cs);
                    stateService.removeChangeSetExecution(cs.getId());
                    logger.info("ChangeSet {} rolled back successfully.", cs.getId());
                } catch (Exception e) {
                    logger.error("Failed to rollback ChangeSet {}", cs.getId(), e);
                    throw e;
                }
            }

        } finally {
            lock.release();
        }
    }
}
