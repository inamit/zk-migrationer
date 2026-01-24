package com.zkmigration.core;

import com.zkmigration.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MigrationInspector {
    private final CuratorFramework client;

    public MigrationInspector(CuratorFramework client) {
        this.client = client;
    }

    public String inspect(ChangeSet changeSet, boolean isRollback) throws Exception {
        return inspect(changeSet, isRollback, Collections.emptyMap());
    }

    public String inspect(ChangeSet changeSet, boolean isRollback, Map<String, String> variables) throws Exception {
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

        ChangeVisitor<String> visitor = new InspectionVisitor(client, variables);
        for (Change change : changes) {
            report.append(change.accept(visitor)).append("\n");
        }

        return report.toString();
    }

    @Slf4j
    private record InspectionVisitor(CuratorFramework client, Map<String, String> variables) implements ChangeVisitor<String> {

        @Override
        public String visit(Create create) {
            StringBuilder out = new StringBuilder();
            try {
                String resolvedPath = VariableSubstitutor.replace(create.getPath(), variables);
                String resolvedData = VariableSubstitutor.replace(create.getData(), variables);
                String resolvedFile = VariableSubstitutor.replace(create.getFile(), variables);

                out.append("CREATE ").append(resolvedPath).append("\n");
                if (client.checkExists().forPath(resolvedPath) != null) {
                    out.append("WARNING: Node already exists!\n");
                }
                byte[] newData = MigrationUtils.resolveData(resolvedData, resolvedFile);
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
                String resolvedPath = VariableSubstitutor.replace(update.getPath(), variables);
                String resolvedData = VariableSubstitutor.replace(update.getData(), variables);
                String resolvedFile = VariableSubstitutor.replace(update.getFile(), variables);

                out.append("UPDATE ").append(resolvedPath).append("\n");
                if (client.checkExists().forPath(resolvedPath) == null) {
                    out.append("WARNING: Node does not exist!\n");
                    byte[] newData = MigrationUtils.resolveData(resolvedData, resolvedFile);
                    out.append(DiffGenerator.generateDiff(null, newData));
                } else {
                    byte[] oldData = client.getData().forPath(resolvedPath);
                    byte[] newData = MigrationUtils.resolveData(resolvedData, resolvedFile);
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
                String resolvedPath = VariableSubstitutor.replace(delete.getPath(), variables);
                out.append("DELETE ").append(resolvedPath).append("\n");
                if (client.checkExists().forPath(resolvedPath) == null) {
                    out.append("WARNING: Node does not exist!\n");
                } else {
                    byte[] oldData = client.getData().forPath(resolvedPath);
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
                String resolvedPath = VariableSubstitutor.replace(rename.getPath(), variables);
                String resolvedDestination = VariableSubstitutor.replace(rename.getDestination(), variables);

                out.append("RENAME ").append(resolvedPath).append(" -> ").append(resolvedDestination).append("\n");
                if (client.checkExists().forPath(resolvedPath) == null) {
                    out.append("WARNING: Source node does not exist!\n");
                }
                if (client.checkExists().forPath(resolvedDestination) != null) {
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
                String resolvedPath = VariableSubstitutor.replace(upsert.getPath(), variables);
                String resolvedData = VariableSubstitutor.replace(upsert.getData(), variables);
                String resolvedFile = VariableSubstitutor.replace(upsert.getFile(), variables);

                out.append("UPSERT ").append(resolvedPath).append("\n");
                byte[] newData = MigrationUtils.resolveData(resolvedData, resolvedFile);
                if (client.checkExists().forPath(resolvedPath) != null) {
                    byte[] oldData = client.getData().forPath(resolvedPath);
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
