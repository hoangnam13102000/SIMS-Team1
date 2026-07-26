package com.components.crud;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TrashConfig<T> {

    private final Supplier<List<T>> fetchDeleted;
    private final Function<T, Boolean> restore;
    private final Function<T, Boolean> hardDelete; // co the null

    public TrashConfig(Supplier<List<T>> fetchDeleted, Function<T, Boolean> restore, Function<T, Boolean> hardDelete) {
        this.fetchDeleted = fetchDeleted;
        this.restore = restore;
        this.hardDelete = hardDelete;
    }

    /** Tien ich: khong cho phep xoa vinh vien tu dialog Thung rac. */
    public TrashConfig(Supplier<List<T>> fetchDeleted, Function<T, Boolean> restore) {
        this(fetchDeleted, restore, null);
    }

    public List<T> fetchDeleted() { return fetchDeleted.get(); }
    public Function<T, Boolean> restore() { return restore; }
    public Function<T, Boolean> hardDelete() { return hardDelete; } // null neu khong ho tro
}