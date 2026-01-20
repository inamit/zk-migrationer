package com.zkmigration.core;

import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import com.zkmigration.model.Delete;
import com.zkmigration.model.Rename;
import com.zkmigration.model.Update;
import com.zkmigration.model.Upsert;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationInspectorTest {
    private TestingServer testingServer;
    private CuratorFramework client;
    private MigrationInspector inspector;

    @BeforeEach
    public void setUp() throws Exception {
        testingServer = new TestingServer();
        client = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        client.start();
        inspector = new MigrationInspector(client);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (client != null) client.close();
        if (testingServer != null) testingServer.close();
    }

    @Test
    public void testInspectCreate() throws Exception {
        ChangeSet cs = new ChangeSet();
        cs.setId("1");
        cs.setAuthor("test");
        Create create = new Create();
        create.setPath("/new/path");
        create.setData("data");
        cs.setChanges(Collections.singletonList(create));

        String report = inspector.inspect(cs, false);
        assertThat(report).contains("CREATE /new/path");
        assertThat(report).contains("+ data");
        assertThat(report).doesNotContain("WARNING");

        // Simulate pre-existing node
        client.create().creatingParentsIfNeeded().forPath("/new/path", "old".getBytes());
        report = inspector.inspect(cs, false);
        assertThat(report).contains("WARNING: Node already exists!");
    }

    @Test
    public void testInspectUpdate() throws Exception {
        ChangeSet cs = new ChangeSet();
        cs.setId("2");
        cs.setAuthor("test");
        Update update = new Update();
        update.setPath("/existing/path");
        update.setData("newdata");
        cs.setChanges(Collections.singletonList(update));

        // Node missing
        String report = inspector.inspect(cs, false);
        assertThat(report).contains("UPDATE /existing/path");
        assertThat(report).contains("WARNING: Node does not exist!");

        // Node exists
        client.create().creatingParentsIfNeeded().forPath("/existing/path", "olddata".getBytes());
        report = inspector.inspect(cs, false);
        assertThat(report).doesNotContain("WARNING");
        // Updated to use word diff format for modifications
        assertThat(report).contains("* [-olddata-] {+newdata+}");
    }

    @Test
    public void testInspectDelete() throws Exception {
        ChangeSet cs = new ChangeSet();
        cs.setId("3");
        cs.setAuthor("test");
        Delete delete = new Delete();
        delete.setPath("/to/delete");
        cs.setChanges(Collections.singletonList(delete));

        // Node missing
        String report = inspector.inspect(cs, false);
        assertThat(report).contains("DELETE /to/delete");
        assertThat(report).contains("WARNING: Node does not exist!");

        // Node exists
        client.create().creatingParentsIfNeeded().forPath("/to/delete", "content".getBytes());
        report = inspector.inspect(cs, false);
        assertThat(report).doesNotContain("WARNING");
        // Check if it shows what is being deleted (DiffGenerator(old, null) -> all deleted)
        assertThat(report).contains("- content");
    }

    @Test
    public void testInspectRename() throws Exception {
        ChangeSet cs = new ChangeSet();
        cs.setId("4");
        cs.setAuthor("test");
        Rename rename = new Rename();
        rename.setPath("/src");
        rename.setDestination("/dest");
        cs.setChanges(Collections.singletonList(rename));

        // Source missing
        String report = inspector.inspect(cs, false);
        assertThat(report).contains("RENAME /src -> /dest");
        assertThat(report).contains("WARNING: Source node does not exist!");

        // Dest exists
        client.create().creatingParentsIfNeeded().forPath("/src", "data".getBytes());
        client.create().creatingParentsIfNeeded().forPath("/dest", "data".getBytes());
        report = inspector.inspect(cs, false);
        assertThat(report).contains("WARNING: Destination node already exists!");
    }

    @Test
    public void testInspectUpsert() throws Exception {
        ChangeSet cs = new ChangeSet();
        cs.setId("5");
        cs.setAuthor("test");
        Upsert upsert = new Upsert();
        upsert.setPath("/upsert/path");
        upsert.setData("val");
        cs.setChanges(Collections.singletonList(upsert));

        // Create case (missing)
        String report = inspector.inspect(cs, false);
        assertThat(report).contains("UPSERT /upsert/path");
        assertThat(report).contains("+ val");
        assertThat(report).doesNotContain("- "); // No old value

        // Update case (exists)
        client.create().creatingParentsIfNeeded().forPath("/upsert/path", "old".getBytes());
        report = inspector.inspect(cs, false);
        // Updated to use word diff format for modifications
        assertThat(report).contains("* [-old-] {+val+}");
    }

    @Test
    public void testRollbackPreview() throws Exception {
        ChangeSet cs = new ChangeSet();
        cs.setId("6");
        cs.setAuthor("test");
        Delete delete = new Delete();
        delete.setPath("/path");
        cs.setRollback(Collections.singletonList(delete));

        String report = inspector.inspect(cs, true);
        assertThat(report).contains("Type: ROLLBACK");
        assertThat(report).contains("DELETE /path");
    }
}
