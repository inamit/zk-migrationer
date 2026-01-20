package com.zkmigration.parser;

import com.zkmigration.model.ChangeLog;
import com.zkmigration.model.ChangeLogEntry;
import com.zkmigration.model.ChangeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeLogParserTest {

    @TempDir
    Path tempDir;

    // Helper to extract changeSets from log
    private List<ChangeSet> getChangeSets(ChangeLog log) {
        List<ChangeSet> list = new ArrayList<>();
        if (log.getZookeeperChangeLog() != null) {
            for (ChangeLogEntry entry : log.getZookeeperChangeLog()) {
                if (entry instanceof ChangeSet) {
                    list.add((ChangeSet) entry);
                }
            }
        }
        return list;
    }

    @Test
    void testParseYaml() throws IOException {
        String yaml = """
                zookeeperChangeLog:
                  - changeSet:
                      id: "1"
                      author: "test"
                      context: "dev"
                      labels: "label"
                      changes:
                        - create:
                            path: "/test"
                            data: "data"
                """;
        Path file = tempDir.resolve("changelog.yaml");
        Files.writeString(file, yaml);

        ChangeLogParser parser = new ChangeLogParser();
        ChangeLog log = parser.parse(file.toFile());
        List<ChangeSet> changeSets = getChangeSets(log);

        assertThat(changeSets).hasSize(1);
        assertThat(changeSets.get(0).getId()).isEqualTo("1");
        assertThat(changeSets.get(0).getAuthor()).isEqualTo("test");
        assertThat(changeSets.get(0).getChanges()).hasSize(1);
    }

    @Test
    void testParseJson() throws IOException {
        String json = """
                {
                  "zookeeperChangeLog": [
                    {
                      "changeSet": {
                        "id": "2",
                        "author": "test-json",
                        "context": ["dev"],
                        "labels": ["l1"],
                        "changes": [
                          {
                            "create": {
                              "path": "/json",
                              "data": "data"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;
        Path file = tempDir.resolve("changelog.json");
        Files.writeString(file, json);

        ChangeLogParser parser = new ChangeLogParser();
        ChangeLog log = parser.parse(file.toFile());
        List<ChangeSet> changeSets = getChangeSets(log);

        assertThat(changeSets).hasSize(1);
        assertThat(changeSets.get(0).getId()).isEqualTo("2");
    }

    @Test
    void testParseInclude() throws IOException {
        String includedYaml = """
                zookeeperChangeLog:
                  - changeSet:
                      id: "included-1"
                      author: "included"
                      context: "dev"
                      labels: "l1"
                      changes:
                        - create:
                            path: "/included"
                """;
        Path includedFile = tempDir.resolve("included.yaml");
        Files.writeString(includedFile, includedYaml);

        String mainYaml = """
                zookeeperChangeLog:
                  - include:
                      file: "included.yaml"
                """;
        Path mainFile = tempDir.resolve("main.yaml");
        Files.writeString(mainFile, mainYaml);

        ChangeLogParser parser = new ChangeLogParser();
        ChangeLog log = parser.parse(mainFile.toFile());
        List<ChangeSet> changeSets = getChangeSets(log);

        assertThat(changeSets).hasSize(1);
        assertThat(changeSets.get(0).getId()).isEqualTo("included-1");
    }

    @Test
    void testMissingContextThrowsException() throws IOException {
        String yaml = """
                zookeeperChangeLog:
                  - changeSet:
                      id: "invalid-1"
                      author: "test"
                      # missing context
                      labels: "label"
                      changes:
                        - create:
                            path: "/test"
                """;
        Path file = tempDir.resolve("invalid-context.yaml");
        Files.writeString(file, yaml);

        ChangeLogParser parser = new ChangeLogParser();
        assertThatThrownBy(() -> parser.parse(file.toFile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing mandatory context");
    }

    @Test
    void testMissingLabelsThrowsException() throws IOException {
        String yaml = """
                zookeeperChangeLog:
                  - changeSet:
                      id: "invalid-2"
                      author: "test"
                      context: "dev"
                      # missing labels
                      changes:
                        - create:
                            path: "/test"
                """;
        Path file = tempDir.resolve("invalid-labels.yaml");
        Files.writeString(file, yaml);

        ChangeLogParser parser = new ChangeLogParser();
        assertThatThrownBy(() -> parser.parse(file.toFile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing mandatory labels");
    }
}
