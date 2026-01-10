package com.zkmigration.core;

import com.zkmigration.model.Change;
import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import com.zkmigration.model.Delete;
import com.zkmigration.model.Rename;
import com.zkmigration.model.Update;
import com.zkmigration.model.Upsert;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
            byte[] data = resolveData(create.getData(), create.getFile());
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(create.getPath(), data);
        } else if (change instanceof Update) {
            Update update = (Update) change;
            logger.info("Updating node: {}", update.getPath());
            byte[] data = resolveData(update.getData(), update.getFile());
            client.setData().forPath(update.getPath(), data);
        } else if (change instanceof Delete) {
            Delete delete = (Delete) change;
            logger.info("Deleting node: {}", delete.getPath());
            client.delete().forPath(delete.getPath());
        } else if (change instanceof Rename) {
            Rename rename = (Rename) change;
            logger.info("Renaming node from {} to {}", rename.getPath(), rename.getDestination());
            renameNode(rename.getPath(), rename.getDestination());
        } else if (change instanceof Upsert) {
            Upsert upsert = (Upsert) change;
            logger.info("Upserting node: {}", upsert.getPath());
            byte[] data = resolveData(upsert.getData(), upsert.getFile());
            if (client.checkExists().forPath(upsert.getPath()) != null) {
                client.setData().forPath(upsert.getPath(), data);
            } else {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(upsert.getPath(), data);
            }
        } else {
            throw new UnsupportedOperationException("Unknown change type: " + change.getClass().getName());
        }
    }

    private byte[] resolveData(String data, String file) throws IOException {
        if (data != null && file != null) {
            throw new IllegalArgumentException("Cannot provide both 'data' and 'file'");
        }
        if (file != null) {
            return Files.readAllBytes(Path.of(file));
        }
        return data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
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
