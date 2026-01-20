package com.zkmigration.core;

import com.zkmigration.model.Change;
import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import com.zkmigration.model.Delete;
import com.zkmigration.model.Rename;
import com.zkmigration.model.Update;
import com.zkmigration.model.Upsert;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;

import java.util.List;

@Slf4j
public class MigrationExecutor {
    private final CuratorFramework client;

    public MigrationExecutor(CuratorFramework client) {
        this.client = client;
    }

    public void execute(ChangeSet changeSet) throws Exception {
        log.info("Executing ChangeSet: {}", changeSet.getId());
        for (Change change : changeSet.getChanges()) {
            applyChange(change);
        }
    }

    public void rollback(ChangeSet changeSet) throws Exception {
        log.info("Rolling back ChangeSet: {}", changeSet.getId());
        List<Change> rollbackChanges = changeSet.getRollback();
        if (rollbackChanges == null || rollbackChanges.isEmpty()) {
            log.warn("No rollback defined for ChangeSet: {}", changeSet.getId());
            return;
        }

        for (Change change : rollbackChanges) {
            applyChange(change);
        }
    }

    private void applyChange(Change change) throws Exception {
        if (change instanceof Create create) {
            log.info("Creating node: {}", create.getPath());
            byte[] data = MigrationUtils.resolveData(create.getData(), create.getFile());
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(create.getPath(), data);
        } else if (change instanceof Update update) {
            log.info("Updating node: {}", update.getPath());
            byte[] data = MigrationUtils.resolveData(update.getData(), update.getFile());
            client.setData().forPath(update.getPath(), data);
        } else if (change instanceof Delete delete) {
            log.info("Deleting node: {}", delete.getPath());
            client.delete().forPath(delete.getPath());
        } else if (change instanceof Rename rename) {
            log.info("Renaming node from {} to {}", rename.getPath(), rename.getDestination());
            renameNode(rename.getPath(), rename.getDestination());
        } else if (change instanceof Upsert upsert) {
            log.info("Upserting node: {}", upsert.getPath());
            byte[] data = MigrationUtils.resolveData(upsert.getData(), upsert.getFile());
            if (client.checkExists().forPath(upsert.getPath()) != null) {
                client.setData().forPath(upsert.getPath(), data);
            } else {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(upsert.getPath(), data);
            }
        } else {
            throw new UnsupportedOperationException("Unknown change type: " + change.getClass().getName());
        }
    }

    private void renameNode(String sourcePath, String destinationPath) throws Exception {
        byte[] data = client.getData().forPath(sourcePath);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(destinationPath, data);

        List<String> children = client.getChildren().forPath(sourcePath);
        for (String child : children) {
            renameNode(sourcePath + "/" + child, destinationPath + "/" + child);
        }
        client.delete().forPath(sourcePath);
    }
}
