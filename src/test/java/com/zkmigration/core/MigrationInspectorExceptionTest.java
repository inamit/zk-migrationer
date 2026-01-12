package com.zkmigration.core;

import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationInspectorExceptionTest {
    private TestingServer testingServer;
    private CuratorFramework client;
    private MigrationInspector inspector;

    @BeforeEach
    public void setUp() throws Exception {
        testingServer = new TestingServer();
        // Create a client but close it immediately to force exceptions during use
        client = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        client.start();
        inspector = new MigrationInspector(client);
        client.close(); // Force closed state
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (testingServer != null) testingServer.close();
    }

    @Test
    public void testInspectExceptionHandling() throws Exception {
        ChangeSet cs = new ChangeSet();
        cs.setId("1");
        cs.setAuthor("test");
        Create create = new Create();
        create.setPath("/path");
        create.setData("data");
        cs.setChanges(Collections.singletonList(create));

        // This should not throw, but catch exception and log it in the output string
        String report = inspector.inspect(cs, false);

        assertThat(report).contains("Error inspecting Create");
    }
}
