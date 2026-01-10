package com.zkmigration.core;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationStateServiceTest {

    private TestingServer server;
    private CuratorFramework client;
    private MigrationStateService service;
    private static final String HISTORY_PATH = "/zookeeper-migrations/changelog";

    @BeforeEach
    void setUp() throws Exception {
        server = new TestingServer();
        client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        service = new MigrationStateService(client, HISTORY_PATH);
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.close();
    }

    @Test
    void testEnsureHistoryPathExists() throws Exception {
        service.ensureHistoryPathExists();
        assertThat(client.checkExists().forPath(HISTORY_PATH)).isNotNull();
    }

    @Test
    void testMarkChangeSetExecutedAndGetIds() throws Exception {
        String id1 = "test-1";
        String author1 = "user1";
        String id2 = "test-2";
        String author2 = "user2";

        service.markChangeSetExecuted(id1, author1, "desc1");
        service.markChangeSetExecuted(id2, author2, "desc2");

        List<String> executedIds = service.getExecutedChangeSetIds();
        assertThat(executedIds).containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    void testMarkChangeSetExecutedIdempotency() throws Exception {
        String id = "test-idemp";
        service.markChangeSetExecuted(id, "user", "desc");
        service.markChangeSetExecuted(id, "user", "desc-update");

        List<String> executedIds = service.getExecutedChangeSetIds();
        assertThat(executedIds).hasSize(1);
        assertThat(executedIds).contains(id);
    }

    @Test
    void testRemoveChangeSetExecution() throws Exception {
        String id = "test-remove";
        service.markChangeSetExecuted(id, "user", "desc");

        assertThat(service.getExecutedChangeSetIds()).contains(id);

        service.removeChangeSetExecution(id);

        assertThat(service.getExecutedChangeSetIds()).isEmpty();
    }

    @Test
    void testRemoveNonExistentChangeSet() throws Exception {
        service.removeChangeSetExecution("non-existent");
        // Should not throw exception
    }
}
