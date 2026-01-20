package com.zkmigration.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
public class MigrationStateService {
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
                log.warn("Found invalid node in history path: {}", child);
            }
        }
        return decodedIds;
    }

    // New method to retrieve full execution details, mapped by ID
    public Map<String, ExecutedChangeSet> getExecutedChangeSets() throws Exception {
        ensureHistoryPathExists();
        List<String> children = client.getChildren().forPath(historyPath);
        Map<String, ExecutedChangeSet> executedMap = new HashMap<>();

        for (String child : children) {
             try {
                byte[] bytes = java.util.Base64.getUrlDecoder().decode(child);
                String id = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

                byte[] data = client.getData().forPath(historyPath + "/" + child);
                ExecutedChangeSet executed = mapper.readValue(data, ExecutedChangeSet.class);
                executedMap.put(id, executed);
            } catch (Exception e) {
                log.warn("Failed to read history node: {}", child, e);
            }
        }
        return executedMap;
    }

    public void markChangeSetExecuted(String id, String author, String description) throws Exception {
        markChangeSetExecuted(id, author, description, null);
    }

    public void markChangeSetExecuted(String id, String author, String description, String checksum) throws Exception {
        ensureHistoryPathExists();
        String nodePath = historyPath + "/" + encodeId(id);
        ExecutedChangeSet executed = new ExecutedChangeSet(id, author, System.currentTimeMillis(), checksum);
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
        public String checksum;

        public ExecutedChangeSet() {}
        public ExecutedChangeSet(String id, String author, long executedAt) {
            this(id, author, executedAt, null);
        }
        public ExecutedChangeSet(String id, String author, long executedAt, String checksum) {
            this.id = id;
            this.author = author;
            this.executedAt = executedAt;
            this.checksum = checksum;
        }
    }
}
