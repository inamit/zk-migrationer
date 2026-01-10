package com.zkmigration.core;

import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import com.zkmigration.model.Delete;
import com.zkmigration.model.Rename;
import com.zkmigration.model.Update;
import com.zkmigration.model.Upsert;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void testExecuteRename() throws Exception {
        // Create initial node with data
        client.create().creatingParentsIfNeeded().forPath("/test/rename/source", "source-data".getBytes(StandardCharsets.UTF_8));
        // Create a child node to test recursive rename
        client.create().creatingParentsIfNeeded().forPath("/test/rename/source/child", "child-data".getBytes(StandardCharsets.UTF_8));

        Rename rename = new Rename();
        rename.setPath("/test/rename/source");
        rename.setDestination("/test/rename/destination");

        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("6");
        changeSet.setChanges(List.of(rename));

        executor.execute(changeSet);

        // Verify source is gone
        assertThat(client.checkExists().forPath("/test/rename/source")).isNull();

        // Verify destination exists with data
        byte[] data = client.getData().forPath("/test/rename/destination");
        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("source-data");

        // Verify child moved
        byte[] childData = client.getData().forPath("/test/rename/destination/child");
        assertThat(new String(childData, StandardCharsets.UTF_8)).isEqualTo("child-data");
    }

    @Test
    void testExecuteUpsertCreates() throws Exception {
        Upsert upsert = new Upsert();
        upsert.setPath("/test/upsert/create");
        upsert.setData("upsert-create-data");

        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("7");
        changeSet.setChanges(List.of(upsert));

        executor.execute(changeSet);

        byte[] data = client.getData().forPath("/test/upsert/create");
        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("upsert-create-data");
    }

    @Test
    void testExecuteUpsertUpdates() throws Exception {
        client.create().creatingParentsIfNeeded().forPath("/test/upsert/update", "initial-data".getBytes(StandardCharsets.UTF_8));

        Upsert upsert = new Upsert();
        upsert.setPath("/test/upsert/update");
        upsert.setData("upsert-update-data");

        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("8");
        changeSet.setChanges(List.of(upsert));

        executor.execute(changeSet);

        byte[] data = client.getData().forPath("/test/upsert/update");
        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("upsert-update-data");
    }

    @Test
    void testExecuteCreateWithFile() throws Exception {
        // Create a temporary file
        Path tempFile = Files.createTempFile("test-create", ".txt");
        Files.writeString(tempFile, "file-data");

        Create create = new Create();
        create.setPath("/test/create-file");
        create.setFile(tempFile.toAbsolutePath().toString());

        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("9");
        changeSet.setChanges(List.of(create));

        executor.execute(changeSet);

        byte[] data = client.getData().forPath("/test/create-file");
        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("file-data");

        // Clean up
        Files.delete(tempFile);
    }

    @Test
    void testExecuteWithBothDataAndFileThrows() {
        Create create = new Create();
        create.setPath("/test/error");
        create.setData("some-data");
        create.setFile("some-file");

        ChangeSet changeSet = new ChangeSet();
        changeSet.setId("10");
        changeSet.setChanges(List.of(create));

        assertThatThrownBy(() -> executor.execute(changeSet))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot provide both 'data' and 'file'");
    }
}
