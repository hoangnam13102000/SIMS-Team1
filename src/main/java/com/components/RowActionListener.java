package com.components;

public interface RowActionListener {
    default void onView(int modelRow) {}
    default void onEdit(int modelRow) {}
    default void onDelete(int modelRow) {}
}