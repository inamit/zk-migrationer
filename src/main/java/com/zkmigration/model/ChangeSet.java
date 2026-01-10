package com.zkmigration.model;

import java.util.List;

public class ChangeSet implements ChangeLogEntry {
    private String id;
    private String author;
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
