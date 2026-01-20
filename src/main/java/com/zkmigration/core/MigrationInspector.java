package com.zkmigration.core;

import com.zkmigration.model.*;
import lombok.extern.slf4j.Slf4j;
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

        ChangeVisitor<String> visitor = new InspectionVisitor(client);
        for (Change change : changes) {
            report.append(change.accept(visitor)).append("\n");
        }

        return report.toString();
    }

    @Slf4j
    private record InspectionVisitor(CuratorFramework client) implements ChangeVisitor<String> {

        @Override
        public String visit(Create create) {
            StringBuilder out = new StringBuilder();
            try {
                out.append("CREATE ").append(create.getPath()).append("\n");
                if (client.checkExists().forPath(create.getPath()) != null) {
                    out.append("WARNING: Node already exists!\n");
                }
                byte[] newData = MigrationUtils.resolveData(create.getData(), create.getFile());
                out.append(DiffGenerator.generateDiff(null, newData));
            } catch (Exception e) {
                log.error("Error inspecting Create", e);
                out.append("Error inspecting Create: ").append(e.getMessage());
            }
            return out.toString();
        }

        @Override
        public String visit(Update update) {
            StringBuilder out = new StringBuilder();
            try {
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
            } catch (Exception e) {
                log.error("Error inspecting Update", e);
                out.append("Error inspecting Update: ").append(e.getMessage());
            }
            return out.toString();
        }

        @Override
        public String visit(Delete delete) {
            StringBuilder out = new StringBuilder();
            try {
                out.append("DELETE ").append(delete.getPath()).append("\n");
                if (client.checkExists().forPath(delete.getPath()) == null) {
                    out.append("WARNING: Node does not exist!\n");
                } else {
                    byte[] oldData = client.getData().forPath(delete.getPath());
                    out.append(DiffGenerator.generateDiff(oldData, null));
                }
            } catch (Exception e) {
                log.error("Error inspecting Delete", e);
                out.append("Error inspecting Delete: ").append(e.getMessage());
            }
            return out.toString();
        }

        @Override
        public String visit(Rename rename) {
            StringBuilder out = new StringBuilder();
            try {
                out.append("RENAME ").append(rename.getPath()).append(" -> ").append(rename.getDestination()).append("\n");
                if (client.checkExists().forPath(rename.getPath()) == null) {
                    out.append("WARNING: Source node does not exist!\n");
                }
                if (client.checkExists().forPath(rename.getDestination()) != null) {
                    out.append("WARNING: Destination node already exists!\n");
                }
            } catch (Exception e) {
                log.error("Error inspecting Rename", e);
                out.append("Error inspecting Rename: ").append(e.getMessage());
            }
            return out.toString();
        }

        @Override
        public String visit(Upsert upsert) {
            StringBuilder out = new StringBuilder();
            try {
                out.append("UPSERT ").append(upsert.getPath()).append("\n");
                byte[] newData = MigrationUtils.resolveData(upsert.getData(), upsert.getFile());
                if (client.checkExists().forPath(upsert.getPath()) != null) {
                    byte[] oldData = client.getData().forPath(upsert.getPath());
                    out.append(DiffGenerator.generateDiff(oldData, newData));
                } else {
                    out.append(DiffGenerator.generateDiff(null, newData));
                }
            } catch (Exception e) {
                log.error("Error inspecting Upsert", e);
                out.append("Error inspecting Upsert: ").append(e.getMessage());
            }
            return out.toString();
        }
    }
}
