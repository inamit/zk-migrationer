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

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationServiceTest {
    private TestingServer testingServer;
    private CuratorFramework client;
    private MigrationService service;

    @BeforeEach
    public void setUp() throws Exception {
        testingServer = new TestingServer();
        client = CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new ExponentialBackoffRetry(1000, 3));
        client.start();
        service = new MigrationService(client, "/test-migrations");
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (client != null) client.close();
        if (testingServer != null) testingServer.close();
    }

    @Test
    public void testPreviewUpdate() throws Exception {
        ChangeLog changeLog = new ChangeLog();
        ChangeSet cs = new ChangeSet();
        cs.setId("1");
        cs.setAuthor("test");
        cs.setEnvironments(Collections.singletonList("test"));
        cs.setLabels(Collections.singletonList("label"));
        Create create = new Create();
        create.setPath("/test");
        create.setData("data");
        cs.setChanges(Collections.singletonList(create));
        changeLog.setZookeeperChangeLog(Collections.singletonList(cs));

        // Preview should show changes
        boolean hasChanges = service.previewUpdate(changeLog, "test", Collections.singletonList("label"));
        assertThat(hasChanges).isTrue();

        // Apply changes
        service.update(changeLog, "test", Collections.singletonList("label"));

        // Preview again should show no changes
        hasChanges = service.previewUpdate(changeLog, "test", Collections.singletonList("label"));
        assertThat(hasChanges).isFalse();
    }

    @Test
    public void testPreviewRollback() throws Exception {
        ChangeLog changeLog = new ChangeLog();
        ChangeSet cs = new ChangeSet();
        cs.setId("1");
        cs.setAuthor("test");
        cs.setEnvironments(Collections.singletonList("test"));
        cs.setLabels(Collections.singletonList("label"));
        Create create = new Create();
        create.setPath("/test");
        create.setData("data");
        cs.setChanges(Collections.singletonList(create));
        cs.setRollback(Collections.singletonList(new com.zkmigration.model.Delete()));
        cs.getRollback().get(0).setPath("/test");

        changeLog.setZookeeperChangeLog(Collections.singletonList(cs));

        // Nothing to rollback yet
        boolean hasChanges = service.previewRollback(changeLog, 1);
        assertThat(hasChanges).isFalse();

        // Apply
        service.update(changeLog, "test", Collections.singletonList("label"));

        // Now preview rollback
        hasChanges = service.previewRollback(changeLog, 1);
        assertThat(hasChanges).isTrue();
    }
}
