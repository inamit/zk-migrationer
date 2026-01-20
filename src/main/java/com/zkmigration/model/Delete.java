package com.zkmigration.model;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

@Slf4j
public class Delete extends Change {
    @Override
    public <T> T accept(ChangeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public void applyChange(CuratorFramework client) throws Exception {
        log.info("Deleting node: {}", getPath());
        client.delete().forPath(getPath());
    }
}
