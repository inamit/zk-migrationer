package com.zkmigration.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ChangeSet.class, name = "changeSet"),
    @JsonSubTypes.Type(value = Include.class, name = "include")
})
public interface ChangeLogEntry {
}
