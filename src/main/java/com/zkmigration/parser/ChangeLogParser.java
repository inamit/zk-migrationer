package com.zkmigration.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.zkmigration.model.ChangeLog;
import com.zkmigration.model.ChangeLogEntry;
import com.zkmigration.model.ChangeSet;
import com.zkmigration.model.Include;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeLogParser {
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public ChangeLogParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.jsonMapper = new ObjectMapper();
    }

    public List<ChangeSet> parse(File file) throws IOException {
        ChangeLog changeLog;
        if (file.getName().endsWith(".json")) {
            changeLog = jsonMapper.readValue(file, ChangeLog.class);
        } else {
            changeLog = yamlMapper.readValue(file, ChangeLog.class);
        }

        List<ChangeSet> result = new ArrayList<>();
        if (changeLog.getDatabaseChangeLog() != null) {
            for (ChangeLogEntry entry : changeLog.getDatabaseChangeLog()) {
                if (entry instanceof ChangeSet) {
                    result.add((ChangeSet) entry);
                } else if (entry instanceof Include) {
                    Include include = (Include) entry;
                    File includedFile = new File(file.getParent(), include.getFile());
                    result.addAll(parse(includedFile));
                }
            }
        }
        return result;
    }
}
