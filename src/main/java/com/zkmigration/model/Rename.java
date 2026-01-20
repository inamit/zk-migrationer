package com.zkmigration.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Rename extends Change {
    private String destination;

    @Override
    public <T> T accept(ChangeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
