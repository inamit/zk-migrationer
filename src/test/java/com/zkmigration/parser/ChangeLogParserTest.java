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

class ChangeLogParserTest {

    @TempDir
    Path tempDir;

    // Helper to extract changeSets from log
    private List<ChangeSet> getChangeSets(ChangeLog log) {
        List<ChangeSet> list = new ArrayList<>();
        if (log.getDatabaseChangeLog() != null) {
            for (ChangeLogEntry entry : log.getDatabaseChangeLog()) {
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
                databaseChangeLog:
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
                  "databaseChangeLog": [
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
                databaseChangeLog:
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
                databaseChangeLog:
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
}
