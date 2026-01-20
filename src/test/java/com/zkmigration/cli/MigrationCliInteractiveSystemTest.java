package com.zkmigration.cli;

import com.zkmigration.core.MigrationService;
import com.zkmigration.model.ChangeLog;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationCliInteractiveSystemTest {
    private TestingServer testingServer;
    private File changelogFile;

    @BeforeEach
    public void setUp() throws Exception {
        testingServer = new TestingServer();
        changelogFile = File.createTempFile("changelog", ".json");
        try (FileWriter writer = new FileWriter(changelogFile)) {
            writer.write("{\"databaseChangeLog\": [{\"changeSet\": {\"id\": \"1\", \"author\": \"test\", \"context\": [\"test\"], \"labels\": [\"l1\"], \"changes\": [{\"create\": {\"path\": \"/interactive\", \"data\": \"val\"}}]}}]}");
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (testingServer != null) testingServer.close();
        if (changelogFile != null) changelogFile.delete();
    }

    @Test
    public void testInteractiveUpdateConfirm() throws Exception {
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream("y\n".getBytes(StandardCharsets.UTF_8)));

        try {
            int exitCode = new CommandLine(new MigrationCli()).execute(
                    "update",
                    "-c", testingServer.getConnectString(),
                    "-f", changelogFile.getAbsolutePath(),
                    "--context", "test",
                    "--labels", "l1",
                    "-i"
            );
            assertThat(exitCode).isEqualTo(0);

            // Verify node created
            try (CuratorFramework client = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new ExponentialBackoffRetry(1000, 3))) {
                client.start();
                assertThat(client.checkExists().forPath("/interactive")).isNotNull();
            }
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    public void testInteractiveUpdateAbort() throws Exception {
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream("n\n".getBytes(StandardCharsets.UTF_8)));

        try {
            int exitCode = new CommandLine(new MigrationCli()).execute(
                    "update",
                    "-c", testingServer.getConnectString(),
                    "-f", changelogFile.getAbsolutePath(),
                    "--context", "test",
                    "--labels", "l1",
                    "-i"
            );
            assertThat(exitCode).isEqualTo(0); // Exit 0 even on abort? Code says returns 0.

            // Verify node NOT created
            try (CuratorFramework client = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new ExponentialBackoffRetry(1000, 3))) {
                client.start();
                assertThat(client.checkExists().forPath("/interactive")).isNull();
            }
        } finally {
            System.setIn(originalIn);
        }
    }
}
