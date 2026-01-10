package com.zkmigration.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import java.util.Map;

public class ChangeLog {
    private Map<String, List<String>> contextGroups;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> context;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> labels;

    private List<ChangeLogEntry> databaseChangeLog;

    public Map<String, List<String>> getContextGroups() {
        return contextGroups;
    }

    public void setContextGroups(Map<String, List<String>> contextGroups) {
        this.contextGroups = contextGroups;
    }

    public List<String> getContext() {
        return context;
    }

    public void setContext(List<String> context) {
        this.context = context;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public List<ChangeLogEntry> getDatabaseChangeLog() {
        return databaseChangeLog;
    }

    public void setDatabaseChangeLog(List<ChangeLogEntry> databaseChangeLog) {
        this.databaseChangeLog = databaseChangeLog;
    }
}
