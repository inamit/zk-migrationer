package com.zkmigration.core;

import com.zkmigration.model.Change;
import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import com.zkmigration.model.Delete;
import com.zkmigration.model.Update;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MigrationExecutor {
    private static final Logger logger = LoggerFactory.getLogger(MigrationExecutor.class);
    private final CuratorFramework client;

    public MigrationExecutor(CuratorFramework client) {
        this.client = client;
    }

    public void execute(ChangeSet changeSet) throws Exception {
        logger.info("Executing ChangeSet: {}", changeSet.getId());
        for (Change change : changeSet.getChanges()) {
            applyChange(change);
        }
    }

    public void rollback(ChangeSet changeSet) throws Exception {
        logger.info("Rolling back ChangeSet: {}", changeSet.getId());
        List<Change> rollbackChanges = changeSet.getRollback();
        if (rollbackChanges == null || rollbackChanges.isEmpty()) {
            logger.warn("No rollback defined for ChangeSet: {}", changeSet.getId());
            return;
        }

        for (Change change : rollbackChanges) {
            applyChange(change);
        }
    }

    private void applyChange(Change change) throws Exception {
        if (change instanceof Create) {
            Create create = (Create) change;
            logger.info("Creating node: {}", create.getPath());
            byte[] data = create.getData() != null ? create.getData().getBytes(StandardCharsets.UTF_8) : new byte[0];
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(create.getPath(), data);
        } else if (change instanceof Update) {
            Update update = (Update) change;
            logger.info("Updating node: {}", update.getPath());
            byte[] data = update.getData() != null ? update.getData().getBytes(StandardCharsets.UTF_8) : new byte[0];
            client.setData().forPath(update.getPath(), data);
        } else if (change instanceof Delete) {
            Delete delete = (Delete) change;
            logger.info("Deleting node: {}", delete.getPath());
            client.delete().forPath(delete.getPath());
        } else {
            throw new UnsupportedOperationException("Unknown change type: " + change.getClass().getName());
        }
    }
}
