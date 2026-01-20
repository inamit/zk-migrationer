package com.zkmigration.cli;

import com.zkmigration.core.MigrationService;
import com.zkmigration.model.ChangeLog;

@FunctionalInterface
public interface MigrationAction {
    void execute(MigrationService service, ChangeLog changeLog) throws Exception;
}
