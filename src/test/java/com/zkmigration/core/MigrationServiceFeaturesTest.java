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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationServiceFeaturesTest {

    private TestingServer server;
    private CuratorFramework client;
    private MigrationService migrationService;

    @BeforeEach
    void setUp() throws Exception {
        server = new TestingServer();
        client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        migrationService = new MigrationService(client, "/zookeeper-migrations");
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
        server.close();
    }

    private ChangeSet createChangeSet(String id, String environment, String label) {
        ChangeSet cs = new ChangeSet();
        cs.setId(id);
        cs.setAuthor("test");
        cs.setEnvironments(environment != null ? List.of(environment) : List.of());
        cs.setLabels(label != null ? List.of(label) : List.of());
        Create create = new Create();
        create.setPath("/test/" + id);
        create.setData("v1");
        cs.setChanges(List.of(create));
        return cs;
    }

    @Test
    void testEnvironmentFiltering() throws Exception {
        ChangeSet csDev = createChangeSet("dev1", "dev", "app");
        ChangeSet csProd = createChangeSet("prod1", "prod", "app");
        ChangeSet csAll = createChangeSet("all1", "All", "app");

        ChangeLog log = new ChangeLog();
        log.setZookeeperChangeLog(List.of(csDev, csProd, csAll));

        // Run with environment=dev
        migrationService.update(log, "dev", List.of("app"));

        assertThat(client.checkExists().forPath("/test/dev1")).isNotNull();
        assertThat(client.checkExists().forPath("/test/prod1")).isNull();
        assertThat(client.checkExists().forPath("/test/all1")).isNotNull();
    }

    @Test
    void testEnvironmentMismatch() throws Exception {
        ChangeSet csDev = createChangeSet("dev2", "dev", "app");
        ChangeLog log = new ChangeLog();
        log.setZookeeperChangeLog(List.of(csDev));

        // Run with environment=prod, should skip
        migrationService.update(log, "prod", List.of("app"));
        assertThat(client.checkExists().forPath("/test/dev2")).isNull();
    }

    @Test
    void testEnvironmentsGroup() throws Exception {
        ChangeSet csK8s = createChangeSet("k8s1", "k8s", "app");

        ChangeLog log = new ChangeLog();
        Map<String, List<String>> groups = new HashMap<>();
        groups.put("k8s", List.of("dev", "staging"));
        log.setEnvironmentsGroups(groups);
        log.setZookeeperChangeLog(List.of(csK8s));

        // Run with environment=dev (which is in k8s group)
        // Logic: cs has 'k8s'. 'dev' is in 'k8s'. So should run.
        migrationService.update(log, "dev", List.of("app"));

        assertThat(client.checkExists().forPath("/test/k8s1")).isNotNull();
    }

    @Test
    void testLabelFiltering() throws Exception {
        ChangeSet csA = createChangeSet("A", "test", "micro-a");
        ChangeSet csB = createChangeSet("B", "test", "micro-b");

        ChangeLog log = new ChangeLog();
        log.setZookeeperChangeLog(List.of(csA, csB));

        migrationService.update(log, "test", List.of("micro-a"));

        assertThat(client.checkExists().forPath("/test/A")).isNotNull();
        assertThat(client.checkExists().forPath("/test/B")).isNull();
    }

    @Test
    void testLabelMismatch() throws Exception {
        ChangeSet csA = createChangeSet("C", "test", "micro-a");
        ChangeLog log = new ChangeLog();
        log.setZookeeperChangeLog(List.of(csA));

        // Run with labels=micro-b
        migrationService.update(log, "test", List.of("micro-b"));
        assertThat(client.checkExists().forPath("/test/C")).isNull();
    }

    @Test
    void testChecksumValidationFail() throws Exception {
        ChangeSet cs = createChangeSet("chk1", "test", "app");
        ChangeLog log = new ChangeLog();
        log.setZookeeperChangeLog(List.of(cs));

        // First run
        migrationService.update(log, "test", List.of("app"));

        // Modify changeset content
        ((Create)cs.getChanges().get(0)).setData("modified");

        // Second run should fail
        assertThatThrownBy(() -> migrationService.update(log, "test", List.of("app")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Checksum mismatch");
    }

    @Test
    void testValidCheckSumBypass() throws Exception {
        ChangeSet cs = createChangeSet("chk2", "test", "app");
        ChangeLog log = new ChangeLog();
        log.setZookeeperChangeLog(List.of(cs));

        // First run
        migrationService.update(log, "test", List.of("app"));

        // Modify
        ((Create)cs.getChanges().get(0)).setData("modified");
        String newSum = ChecksumUtil.calculateChecksum(cs);

        // Add new checksum as valid
        cs.setValidCheckSum(List.of(newSum));

        // Second run should pass
        migrationService.update(log, "test", List.of("app"));
    }

    @Test
    void testDuplicateIdsExecutesOnce() throws Exception {
        // Create two changesets with same ID
        ChangeSet cs1 = createChangeSet("dup1", "test", "app");
        cs1.getChanges().get(0).setPath("/test/dup1-a");

        ChangeSet cs2 = createChangeSet("dup1", "test", "app");
        cs2.getChanges().get(0).setPath("/test/dup1-b");

        ChangeLog log = new ChangeLog();
        log.setZookeeperChangeLog(List.of(cs1, cs2));

        // Run should throw DuplicateChangeSetIdException
        assertThatThrownBy(() -> migrationService.update(log, "test", List.of("app")))
                .isInstanceOf(DuplicateChangeSetIdException.class)
                .hasMessageContaining("Duplicate ChangeSet ID detected");
    }
}
