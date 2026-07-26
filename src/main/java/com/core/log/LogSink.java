package com.core.log;

/** Interface for actual log persistence (DB, file, console, etc.) */
public interface LogSink {
    void write(LogEntry entry);
}