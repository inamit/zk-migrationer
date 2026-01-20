package com.zkmigration.model;

import com.zkmigration.core.MigrationUtils;
import com.zkmigration.core.VariableSubstitutor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;

import java.util.Map;

@Slf4j
@Setter
@Getter
public class Upsert extends Change {
    private String data;
    private String file;

    @Override
    public <T> T accept(ChangeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public void applyChange(CuratorFramework client, Map<String, String> variables) throws Exception {
        String resolvedPath = VariableSubstitutor.replace(getPath(), variables);
        String resolvedData = VariableSubstitutor.replace(getData(), variables);
        String resolvedFile = VariableSubstitutor.replace(getFile(), variables);

        log.info("Upserting node: {}", resolvedPath);
        byte[] data = MigrationUtils.resolveData(resolvedData, resolvedFile);
        if (client.checkExists().forPath(resolvedPath) != null) {
            client.setData().forPath(resolvedPath, data);
        } else {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(resolvedPath, data);
        }
    }
}
