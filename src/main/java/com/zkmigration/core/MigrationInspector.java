package com.zkmigration.core;

import com.zkmigration.model.Change;
import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Create;
import com.zkmigration.model.Delete;
import com.zkmigration.model.Rename;
import com.zkmigration.model.Update;
import com.zkmigration.model.Upsert;
import org.apache.curator.framework.CuratorFramework;

import java.util.List;

public class MigrationInspector {
    private final CuratorFramework client;

    public MigrationInspector(CuratorFramework client) {
        this.client = client;
    }

    public String inspect(ChangeSet changeSet, boolean isRollback) throws Exception {
        StringBuilder report = new StringBuilder();
        report.append("ChangeSet ID: ").append(changeSet.getId()).append("\n");
        report.append("Author: ").append(changeSet.getAuthor()).append("\n");
        report.append("Type: ").append(isRollback ? "ROLLBACK" : "UPDATE").append("\n");
        report.append("--------------------------------------------------\n");

        List<Change> changes = isRollback ? changeSet.getRollback() : changeSet.getChanges();
        if (changes == null || changes.isEmpty()) {
            report.append("No changes defined.\n");
            return report.toString();
        }

        for (Change change : changes) {
            report.append(inspectChange(change)).append("\n");
        }

        return report.toString();
    }

    private String inspectChange(Change change) throws Exception {
        StringBuilder out = new StringBuilder();
        if (change instanceof Create) {
            Create create = (Create) change;
            out.append("CREATE ").append(create.getPath()).append("\n");
            if (client.checkExists().forPath(create.getPath()) != null) {
                out.append("WARNING: Node already exists!\n");
            }
            byte[] newData = MigrationUtils.resolveData(create.getData(), create.getFile());
            out.append(DiffGenerator.generateDiff(null, newData));

        } else if (change instanceof Update) {
            Update update = (Update) change;
            out.append("UPDATE ").append(update.getPath()).append("\n");
            if (client.checkExists().forPath(update.getPath()) == null) {
                out.append("WARNING: Node does not exist!\n");
                byte[] newData = MigrationUtils.resolveData(update.getData(), update.getFile());
                out.append(DiffGenerator.generateDiff(null, newData));
            } else {
                byte[] oldData = client.getData().forPath(update.getPath());
                byte[] newData = MigrationUtils.resolveData(update.getData(), update.getFile());
                out.append(DiffGenerator.generateDiff(oldData, newData));
            }

        } else if (change instanceof Delete) {
            Delete delete = (Delete) change;
            out.append("DELETE ").append(delete.getPath()).append("\n");
            if (client.checkExists().forPath(delete.getPath()) == null) {
                out.append("WARNING: Node does not exist!\n");
            } else {
                byte[] oldData = client.getData().forPath(delete.getPath());
                // Show what is being deleted as "Old" vs "Empty New"?
                // DiffGenerator(old, null) -> shows deletions as "-"
                out.append(DiffGenerator.generateDiff(oldData, null));
            }

        } else if (change instanceof Rename) {
            Rename rename = (Rename) change;
            out.append("RENAME ").append(rename.getPath()).append(" -> ").append(rename.getDestination()).append("\n");
            if (client.checkExists().forPath(rename.getPath()) == null) {
                out.append("WARNING: Source node does not exist!\n");
            }
            if (client.checkExists().forPath(rename.getDestination()) != null) {
                out.append("WARNING: Destination node already exists!\n");
            }
            // For rename, maybe we don't show diff of content unless it's modified?
            // Rename is copy+delete. So content remains same.
            // Just listing the action is enough per requirements "Show the changes in path"

        } else if (change instanceof Upsert) {
            Upsert upsert = (Upsert) change;
            out.append("UPSERT ").append(upsert.getPath()).append("\n");
            byte[] newData = MigrationUtils.resolveData(upsert.getData(), upsert.getFile());
            if (client.checkExists().forPath(upsert.getPath()) != null) {
                byte[] oldData = client.getData().forPath(upsert.getPath());
                out.append(DiffGenerator.generateDiff(oldData, newData));
            } else {
                out.append(DiffGenerator.generateDiff(null, newData));
            }
        } else {
            out.append("UNKNOWN CHANGE: ").append(change.getClass().getSimpleName());
        }
        return out.toString();
    }
}
