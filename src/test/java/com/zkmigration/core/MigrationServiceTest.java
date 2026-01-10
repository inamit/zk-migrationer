package com.zkmigration.core;

import com.zkmigration.model.ChangeLog;
import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationServiceTest {

    private TestingServer server;
    private CuratorFramework client;
    private MigrationService migrationService;
    private MigrationStateService stateService;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestingServer();
        client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        migrationService = new MigrationService(client, "/zookeeper-migrations");
        stateService = new MigrationStateService(client, "/zookeeper-migrations/changelog");
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.close();
    }

    // Helper to create valid changeset with mandatory fields
    private ChangeSet createChangeSet(String id) {
        ChangeSet cs = new ChangeSet();
        cs.setId(id);
        cs.setAuthor("author");
        cs.setContext(List.of("test"));
        cs.setLabels(List.of("test"));
        return cs;
    }

    @Test
    void testUpdateDoesNotReapplyExecutedChangeSets() throws Exception {
        // Manually mark a changeset as executed
        // Note: Legacy markChangeSetExecuted didn't have checksum.
        // We use the new one or rely on null/empty handling?
        // The service logic now verifies checksum. If we manually mark it without checksum,
        // the verification logic logs a warning but proceeds (doesn't fail) and considers it executed.
        stateService.markChangeSetExecuted("1", "author", "desc");

        ChangeSet changeSet = createChangeSet("1");
        changeSet.setChanges(List.of(create("/test/1", "data")));

        ChangeLog changeLog = new ChangeLog();
        changeLog.setDatabaseChangeLog(List.of(changeSet));
        // Execute update
        migrationService.update(changeLog, "test", List.of("test"));

        // Verify the change was NOT applied (node should not exist)
        assertThat(client.checkExists().forPath("/test/1")).isNull();
    }

    @Test
    void testUpdateAppliesNewChangeSets() throws Exception {
        ChangeSet changeSet = createChangeSet("2");
        changeSet.setChanges(List.of(create("/test/2", "data")));

        ChangeLog changeLog = new ChangeLog();
        changeLog.setDatabaseChangeLog(List.of(changeSet));

        migrationService.update(changeLog, "test", List.of("test"));

        assertThat(client.checkExists().forPath("/test/2")).isNotNull();
        assertThat(stateService.getExecutedChangeSetIds()).contains("2");
    }

    @Test
    void testRollback() throws Exception {
        // Setup: Apply a changeset
        ChangeSet changeSet = createChangeSet("3");
        changeSet.setChanges(List.of(create("/test/3", "data")));

        com.zkmigration.model.Delete delete = new com.zkmigration.model.Delete();
        delete.setPath("/test/3");
        changeSet.setRollback(List.of(delete));

        ChangeLog changeLog = new ChangeLog();
        changeLog.setDatabaseChangeLog(List.of(changeSet));

        migrationService.update(changeLog, "test", List.of("test"));
        assertThat(client.checkExists().forPath("/test/3")).isNotNull();

        // Perform Rollback
        migrationService.rollback(changeLog, 1);

        assertThat(client.checkExists().forPath("/test/3")).isNull();
        assertThat(stateService.getExecutedChangeSetIds()).doesNotContain("3");
    }

    @Test
    void testRollbackSkipsUnexecutedChangeSets() throws Exception {
        ChangeSet cs1 = createChangeSet("4");
        cs1.setChanges(List.of(create("/test/4", "data")));
        // cs1 is not executed
        ChangeLog changeLog = new ChangeLog();
        changeLog.setDatabaseChangeLog(List.of(cs1));


        // Calling rollback should do nothing for cs1
        migrationService.rollback(changeLog, 1);

        // Just verify no exception
    }

    @Test
    void testLocking() throws Exception {
        ChangeSet changeSet = createChangeSet("lock-test");
        changeSet.setChanges(List.of(create("/test/lock", "data")));

        ChangeLog changeLog = new ChangeLog();
        changeLog.setDatabaseChangeLog(List.of(changeSet));

        migrationService.update(changeLog, "test", List.of("test"));

        // Lock should be released
        // verify we can acquire it
        org.apache.curator.framework.recipes.locks.InterProcessMutex lock =
            new org.apache.curator.framework.recipes.locks.InterProcessMutex(client, "/zookeeper-migrations/lock");

        assertThat(lock.acquire(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        lock.release();
    }

    private Create create(String path, String data) {
        Create create = new Create();
        create.setPath(path);
        create.setData(data);
        return create;
    }
}
