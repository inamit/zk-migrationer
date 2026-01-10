package com.zkmigration.cli;

import com.zkmigration.core.MigrationStateService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationCliSystemTest {

    private TestingServer server;
    private CuratorFramework client;

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
        // 1. Create a changelog file
        String yaml = """
                databaseChangeLog:
                  - changeSet:
                      id: "cli-1"
                      author: "system-test"
                      changes:
                        - create:
                            path: "/cli-test"
                            data: "cli-data"
                      rollback:
                        - delete:
                            path: "/cli-test"
                """;
        File file = File.createTempFile("cli-test", ".yaml");
        Files.writeString(file.toPath(), yaml);

        // 2. Run UPDATE command
        String[] args = {
            "update",
            "--connection", server.getConnectString(),
            "--file", file.getAbsolutePath()
        };

        int updateExitCode = new picocli.CommandLine(new MigrationCli()).execute(args);

        assertThat(updateExitCode).isEqualTo(0);

        // 3. Verify Zookeeper State
        assertThat(client.checkExists().forPath("/cli-test")).isNotNull();
        byte[] data = client.getData().forPath("/cli-test");
        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("cli-data");

        // Verify history exists (ID is base64 encoded, so we just check if any child exists and matches)
        List<String> historyChildren = client.getChildren().forPath("/zookeeper-migrations/changelog");
        assertThat(historyChildren).hasSize(1);
        String encodedId = historyChildren.get(0);
        String decodedId = new String(java.util.Base64.getUrlDecoder().decode(encodedId), StandardCharsets.UTF_8);
        assertThat(decodedId).isEqualTo("cli-1");

        // 4. Run ROLLBACK command
        String[] rollbackArgs = {
            "rollback",
            "--connection", server.getConnectString(),
            "--file", file.getAbsolutePath(),
            "--count", "1"
        };

        int rollbackExitCode = new picocli.CommandLine(new MigrationCli()).execute(rollbackArgs);
        assertThat(rollbackExitCode).isEqualTo(0);

        // 5. Verify Rollback
        assertThat(client.checkExists().forPath("/cli-test")).isNull();
        // The migration record should be removed
        assertThat(client.checkExists().forPath("/zookeeper-migrations/changelog/" + encodedId)).isNull();
    }
}
