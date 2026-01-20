package com.zkmigration.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Create extends Change {
    private String data;
    private String file;

    @Override
    public <T> T accept(ChangeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
