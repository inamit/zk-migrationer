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
            change.applyChange(client);
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
            change.applyChange(client);
        }
    }
}
