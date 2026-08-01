package com.incident;

public interface IncidentSink {
    void write(Incident incident);
}