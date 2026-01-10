package com.zkmigration.core;

import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import com.zkmigration.model.Delete;
import com.zkmigration.model.Update;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationExecutorTest {

    private TestingServer server;
    private CuratorFramework client;
    private MigrationExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestingServer();
        client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        executor = new MigrationExecutor(client);
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.close();
    }

    @Test
    void testExecuteCreate() throws Exception {
        Create create = new Create();
        create.setPath("/test/create");
        create.setData("test-data");

        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("1");
        changeSet.setChanges(List.of(create));

        executor.execute(changeSet);

        assertThat(client.checkExists().forPath("/test/create")).isNotNull();
        byte[] data = client.getData().forPath("/test/create");
        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("test-data");
    }

    @Test
    void testExecuteUpdate() throws Exception {
        client.create().creatingParentsIfNeeded().forPath("/test/update", "initial".getBytes(StandardCharsets.UTF_8));

        Update update = new Update();
        update.setPath("/test/update");
        update.setData("updated-data");

        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("2");
        changeSet.setChanges(List.of(update));

        executor.execute(changeSet);

        byte[] data = client.getData().forPath("/test/update");
        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("updated-data");
    }

    @Test
    void testExecuteDelete() throws Exception {
        client.create().creatingParentsIfNeeded().forPath("/test/delete");

        Delete delete = new Delete();
        delete.setPath("/test/delete");

        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("3");
        changeSet.setChanges(List.of(delete));

        executor.execute(changeSet);

        assertThat(client.checkExists().forPath("/test/delete")).isNull();
    }

    @Test
    void testRollback() throws Exception {
        // Setup initial state
        client.create().creatingParentsIfNeeded().forPath("/test/rollback", "initial".getBytes(StandardCharsets.UTF_8));

        // Define rollback as deleting the node
        Delete delete = new Delete();
        delete.setPath("/test/rollback");

        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("4");
        changeSet.setRollback(List.of(delete));

        executor.rollback(changeSet);

        assertThat(client.checkExists().forPath("/test/rollback")).isNull();
    }

    @Test
    void testRollbackWithNoInstructions() throws Exception {
        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("5");
        // No rollback defined

        executor.rollback(changeSet);
        // Should log warning and do nothing, no exception
    }
}
