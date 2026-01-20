package com.zkmigration.model;

public interface ChangeVisitor<T> {
    T visit(Create create);
    T visit(Update update);
    T visit(Delete delete);
    T visit(Rename rename);
    T visit(Upsert upsert);
}
