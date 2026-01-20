package com.zkmigration.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Include implements ChangeLogEntry {
    private String file;
    private boolean relativeToChangelogFile;

}
