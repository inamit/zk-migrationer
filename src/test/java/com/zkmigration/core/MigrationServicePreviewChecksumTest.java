package com.zkmigration.core;

import com.zkmigration.model.ChangeLog;
import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationServicePreviewChecksumTest {
    private TestingServer testingServer;
    private CuratorFramework client;
    private MigrationService service;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    public void setUp() throws Exception {
        testingServer = new TestingServer();
        client = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        client.start();
        service = new MigrationService(client, "/test-migrations");
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void tearDown() throws Exception {
        System.setOut(originalOut);
        if (client != null) client.close();
        if (testingServer != null) testingServer.close();
    }

    @Test
    public void testPreviewUpdateShowsChecksumError() throws Exception {
        // 1. Setup ChangeLog
        ChangeLog changeLog = new ChangeLog();
        ChangeSet cs = new ChangeSet();
        cs.setId("1");
        cs.setAuthor("test");
        cs.setContext(Collections.singletonList("test"));
        cs.setLabels(Collections.singletonList("label"));
        Create create = new Create();
        create.setPath("/test");
        create.setData("data");
        cs.setChanges(Collections.singletonList(create));
        changeLog.setZookeeperChangeLog(Collections.singletonList(cs));

        // 2. Execute it first
        service.update(changeLog, "test", Collections.singletonList("label"));

        // 3. Modify the changeset (simulating checksum change)
        create.setData("modified-data"); // This changes checksum

        // 4. Preview Update again
        service.previewUpdate(changeLog, "test", Collections.singletonList("label"));

        // 5. Verify Output
        assertThat(outContent.toString()).contains("VALIDATION ERROR: Validation Failed: Checksum mismatch");
    }
}
