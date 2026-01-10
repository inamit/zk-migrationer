package com.zkmigration.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MigrationStateService {
    private static final Logger logger = LoggerFactory.getLogger(MigrationStateService.class);
    private final CuratorFramework client;
    private final String historyPath;
    private final ObjectMapper mapper;

    public MigrationStateService(CuratorFramework client, String historyPath) {
        this.client = client;
        this.historyPath = historyPath;
        this.mapper = new ObjectMapper();
    }

    public void ensureHistoryPathExists() throws Exception {
        if (client.checkExists().forPath(historyPath) == null) {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(historyPath);
        }
    }

    public List<String> getExecutedChangeSetIds() throws Exception {
        ensureHistoryPathExists();
        List<String> children = client.getChildren().forPath(historyPath);
        java.util.List<String> decodedIds = new java.util.ArrayList<>();
        for (String child : children) {
            try {
                byte[] bytes = java.util.Base64.getUrlDecoder().decode(child);
                decodedIds.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                logger.warn("Found invalid node in history path: {}", child);
            }
        }
        return decodedIds;
    }

    public void markChangeSetExecuted(String id, String author, String description) throws Exception {
        ensureHistoryPathExists();
        String nodePath = historyPath + "/" + encodeId(id);
        ExecutedChangeSet executed = new ExecutedChangeSet(id, author, System.currentTimeMillis());
        byte[] data = mapper.writeValueAsBytes(executed);

        try {
            client.create().withMode(CreateMode.PERSISTENT).forPath(nodePath, data);
        } catch (KeeperException.NodeExistsException e) {
            client.setData().forPath(nodePath, data);
        }
    }

    public void removeChangeSetExecution(String id) throws Exception {
        String nodePath = historyPath + "/" + encodeId(id);
        try {
            client.delete().forPath(nodePath);
        } catch (KeeperException.NoNodeException e) {
            // Ignored
        }
    }

    private String encodeId(String id) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static class ExecutedChangeSet {
        public String id;
        public String author;
        public long executedAt;

        public ExecutedChangeSet() {}
        public ExecutedChangeSet(String id, String author, long executedAt) {
            this.id = id;
            this.author = author;
            this.executedAt = executedAt;
        }
    }
}
