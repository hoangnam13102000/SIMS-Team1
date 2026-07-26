package com.importer;

/** Ket qua xu ly 1 dong du lieu khi import. */
public final class ImportRowResult {

    private static final ImportRowResult SUCCESS = new ImportRowResult(true, null);

    private final boolean success;
    private final String errorMessage;

    private ImportRowResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static ImportRowResult success() { return SUCCESS; }

    public static ImportRowResult failure(String message) {
        return new ImportRowResult(false, message);
    }

    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
}