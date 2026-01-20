package com.zkmigration.cli;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationCliSystemTest {

    private TestingServer server;
    private CuratorFramework client;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestingServer();
        client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.close();
    }

    @Test
    void testCliUpdateAndRollback() throws Exception {
        // 1. Create a changelog file with context and labels
        String yaml = """
                databaseChangeLog:
                  - changeSet:
                      id: "cli-1"
                      author: "system-test"
                      context: "test"
                      labels: "test"
                      changes:
                        - create:
                            path: "/cli-test"
                            data: "cli-data"
                      rollback:
                        - delete:
                            path: "/cli-test"
                """;
        Path file = tempDir.resolve("cli-test.yaml");
        Files.writeString(file, yaml);

        // 2. Run UPDATE command with context and labels
        String[] args = {
            "update",
            "--connection", server.getConnectString(),
            "--file", file.toAbsolutePath().toString(),
            "--context", "test",
            "--labels", "test"
        };

        int updateExitCode = new picocli.CommandLine(new MigrationCli()).execute(args);

        assertThat(updateExitCode).isEqualTo(0);

        // 3. Verify Zookeeper State
        assertThat(client.checkExists().forPath("/cli-test")).isNotNull();
        byte[] data = client.getData().forPath("/cli-test");
        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("cli-data");

        // Verify history exists
        List<String> historyChildren = client.getChildren().forPath("/zookeeper-migrations/changelog");
        assertThat(historyChildren).hasSize(1);

        // 4. Run ROLLBACK command (Rollback doesn't strictly require context/labels in logic, but CLI parsing might if shared?
        // No, RollbackCommand struct doesn't have them in my implementation.
        // But let's check RollbackCommand in MigrationCli.java... it extends BaseCommand but doesn't add context/labels. So it should be fine.
        String[] rollbackArgs = {
            "rollback",
            "--connection", server.getConnectString(),
            "--file", file.toAbsolutePath().toString(),
            "--count", "1"
        };

        int rollbackExitCode = new picocli.CommandLine(new MigrationCli()).execute(rollbackArgs);
        assertThat(rollbackExitCode).isEqualTo(0);

        // 5. Verify Rollback
        assertThat(client.checkExists().forPath("/cli-test")).isNull();
        assertThat(client.getChildren().forPath("/zookeeper-migrations/changelog")).isEmpty();
    }

    @Test
    void testMultipleChangeSetsAndPartialRollback() throws Exception {
        String yaml = """
                databaseChangeLog:
                  - changeSet:
                      id: "1"
                      author: "test"
                      context: "test"
                      labels: "test"
                      changes:
                        - create:
                            path: "/node1"
                      rollback:
                        - delete:
                            path: "/node1"
                  - changeSet:
                      id: "2"
                      author: "test"
                      context: "test"
                      labels: "test"
                      changes:
                        - create:
                            path: "/node2"
                      rollback:
                        - delete:
                            path: "/node2"
                """;
        Path file = tempDir.resolve("multi.yaml");
        Files.writeString(file, yaml);

        // Apply both
        new picocli.CommandLine(new MigrationCli()).execute("update",
            "--connection", server.getConnectString(),
            "--file", file.toAbsolutePath().toString(),
            "--context", "test",
            "--labels", "test");

        assertThat(client.checkExists().forPath("/node1")).isNotNull();
        assertThat(client.checkExists().forPath("/node2")).isNotNull();
        assertThat(client.getChildren().forPath("/zookeeper-migrations/changelog")).hasSize(2);

        // Rollback only the last one
        new picocli.CommandLine(new MigrationCli()).execute("rollback",
            "--connection", server.getConnectString(),
            "--file", file.toAbsolutePath().toString(),
            "--count", "1");

        assertThat(client.checkExists().forPath("/node1")).isNotNull();
        assertThat(client.checkExists().forPath("/node2")).isNull();
        assertThat(client.getChildren().forPath("/zookeeper-migrations/changelog")).hasSize(1);
    }

    @Test
    void testMissingFile() {
        int exitCode = new picocli.CommandLine(new MigrationCli()).execute("update",
            "--connection", server.getConnectString(),
            "--file", "nonexistent.yaml",
            "--context", "test",
            "--labels", "test");
        assertThat(exitCode).isNotEqualTo(0);
    }

    @Test
    public void testHelp() throws Exception {
        MigrationCli cli = new MigrationCli();
        assertThat(cli.call()).isEqualTo(0);
    }
}
