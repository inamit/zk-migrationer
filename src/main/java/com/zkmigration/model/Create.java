package com.zkmigration.model;

import com.zkmigration.core.MigrationUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;

@Slf4j
@Setter
@Getter
public class Create extends Change {
    private String data;
    private String file;

    @Override
    public <T> T accept(ChangeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public void applyChange(CuratorFramework client) throws Exception {
        log.info("Creating node: {}", getPath());
        byte[] data = MigrationUtils.resolveData(getData(), getFile());
        client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(getPath(), data);
    }
}
