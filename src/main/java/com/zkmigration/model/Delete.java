package com.zkmigration.model;

import com.zkmigration.core.VariableSubstitutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

import java.util.Map;

@Slf4j
public class Delete extends Change {
    @Override
    public <T> T accept(ChangeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public void applyChange(CuratorFramework client, Map<String, String> variables) throws Exception {
        String resolvedPath = VariableSubstitutor.replace(getPath(), variables);
        log.info("Deleting node: {}", resolvedPath);
        client.delete().forPath(resolvedPath);
    }
}
