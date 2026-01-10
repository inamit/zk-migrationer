package com.zkmigration.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

public class ChangeSet implements ChangeLogEntry {
    private String id;
    private String author;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> context;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> labels;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> validCheckSum;

    private List<Change> changes;
    private List<Change> rollback;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
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

    public List<String> getValidCheckSum() {
        return validCheckSum;
    }

    public void setValidCheckSum(List<String> validCheckSum) {
        this.validCheckSum = validCheckSum;
    }

    public List<Change> getChanges() {
        return changes;
    }

    public void setChanges(List<Change> changes) {
        this.changes = changes;
    }

    public List<Change> getRollback() {
        return rollback;
    }

    public void setRollback(List<Change> rollback) {
        this.rollback = rollback;
    }
}
