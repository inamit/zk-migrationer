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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangeLogParser {
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public ChangeLogParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.jsonMapper = new ObjectMapper();
    }

    public ChangeLog parse(File file) throws IOException {
        return parse(file, new HashMap<>(), new ArrayList<>(), new ArrayList<>());
    }

    private ChangeLog parse(File file, Map<String, List<String>> inheritedContextGroups,
                            List<String> inheritedContext, List<String> inheritedLabels) throws IOException {
        ChangeLog changeLog;
        if (file.getName().endsWith(".json")) {
            changeLog = jsonMapper.readValue(file, ChangeLog.class);
        } else {
            changeLog = yamlMapper.readValue(file, ChangeLog.class);
        }

        // Merge/Override context groups?
        // User said: "Context groups should be defined at the top level changelog file".
        // It's ambiguous if included files can define groups. I'll assume they can and we merge them,
        // or just keep the ones from the parsing context if we wanted.
        // But the requirement says "at the top level changelog file".
        // However, I need to propagate context/labels.

        if (changeLog.getContextGroups() == null) {
            changeLog.setContextGroups(new HashMap<>());
        }
        // If we are recursing, maybe we shouldn't overwrite inherited groups?
        // But for simplicity, let's accumulate them in the root object we are building?
        // Actually, the recursion logic below flattens everything into the current 'changeLog' object's list.

        if (inheritedContextGroups != null) {
            changeLog.getContextGroups().putAll(inheritedContextGroups);
        }

        // Determine current file's global context/labels
        List<String> effectiveContext = new ArrayList<>();
        if (inheritedContext != null) effectiveContext.addAll(inheritedContext);
        if (changeLog.getContext() != null) effectiveContext.addAll(changeLog.getContext());

        List<String> effectiveLabels = new ArrayList<>();
        if (inheritedLabels != null) effectiveLabels.addAll(inheritedLabels);
        if (changeLog.getLabels() != null) effectiveLabels.addAll(changeLog.getLabels());

        List<ChangeLogEntry> flatEntries = new ArrayList<>();
        if (changeLog.getZookeeperChangeLog() != null) {
            for (ChangeLogEntry entry : changeLog.getZookeeperChangeLog()) {
                if (entry instanceof ChangeSet) {
                    ChangeSet cs = (ChangeSet) entry;

                    // Apply inheritance
                    if (cs.getContext() == null) {
                        cs.setContext(new ArrayList<>());
                    }
                    cs.getContext().addAll(effectiveContext);

                    if (cs.getLabels() == null) {
                        cs.setLabels(new ArrayList<>());
                    }
                    cs.getLabels().addAll(effectiveLabels);

                    // Validation
                    if (cs.getContext().isEmpty()) {
                        throw new IllegalArgumentException("ChangeSet " + cs.getId() + " is missing mandatory context");
                    }
                    if (cs.getLabels().isEmpty()) {
                        throw new IllegalArgumentException("ChangeSet " + cs.getId() + " is missing mandatory labels");
                    }

                    flatEntries.add(cs);
                } else if (entry instanceof Include) {
                    Include include = (Include) entry;
                    File includedFile = new File(file.getParent(), include.getFile());

                    // Recursive parse
                    ChangeLog includedLog = parse(includedFile, changeLog.getContextGroups(), effectiveContext, effectiveLabels);

                    // Merge results
                    if (includedLog.getZookeeperChangeLog() != null) {
                        for (ChangeLogEntry includedEntry : includedLog.getZookeeperChangeLog()) {
                             if (includedEntry instanceof ChangeSet) {
                                 flatEntries.add(includedEntry);
                             }
                             // Note: nested includes are already flattened by recursive call
                        }
                    }
                    // Merge back any new groups found in included file?
                    if (includedLog.getContextGroups() != null) {
                        changeLog.getContextGroups().putAll(includedLog.getContextGroups());
                    }
                }
            }
        }

        changeLog.setZookeeperChangeLog(flatEntries);
        return changeLog;
    }
}
