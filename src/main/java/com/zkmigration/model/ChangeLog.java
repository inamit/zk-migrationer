package com.zkmigration.model;

import java.util.List;

public class ChangeLog {
    private List<ChangeLogEntry> databaseChangeLog;

    public List<ChangeLogEntry> getDatabaseChangeLog() {
        return databaseChangeLog;
    }

    public void setDatabaseChangeLog(List<ChangeLogEntry> databaseChangeLog) {
        this.databaseChangeLog = databaseChangeLog;
    }
}
