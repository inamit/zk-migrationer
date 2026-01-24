package com.zkmigration.model;

import com.zkmigration.core.VariableSubstitutor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;

import java.util.List;
import java.util.Map;

@Slf4j
@Setter
@Getter
public class Rename extends Change {
    private String destination;

    @Override
    public <T> T accept(ChangeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public void applyChange(CuratorFramework client, Map<String, String> variables) throws Exception {
        String resolvedSource = VariableSubstitutor.replace(getPath(), variables);
        String resolvedDestination = VariableSubstitutor.replace(getDestination(), variables);

        log.info("Renaming node from {} to {}", resolvedSource, resolvedDestination);
        renameNode(client, resolvedSource, resolvedDestination);
    }

    private void renameNode(CuratorFramework client, String sourcePath, String destinationPath) throws Exception {
        byte[] data = client.getData().forPath(sourcePath);
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(destinationPath, data);

        List<String> children = client.getChildren().forPath(sourcePath);
        for (String child : children) {
            renameNode(client, sourcePath + "/" + child, destinationPath + "/" + child);
        }
        client.delete().forPath(sourcePath);
    }
}
