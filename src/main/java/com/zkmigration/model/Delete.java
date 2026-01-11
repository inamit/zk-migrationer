package com.zkmigration.model;

public class Delete extends Change {
    @Override
    public <T> T accept(ChangeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
