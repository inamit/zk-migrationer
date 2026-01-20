package com.zkmigration.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Create.class, name = "create"),
    @JsonSubTypes.Type(value = Update.class, name = "update"),
    @JsonSubTypes.Type(value = Delete.class, name = "delete"),
    @JsonSubTypes.Type(value = Rename.class, name = "rename"),
    @JsonSubTypes.Type(value = Upsert.class, name = "upsert")
})
public abstract class Change {
    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public abstract <T> T accept(ChangeVisitor<T> visitor);
}
