package com.incident;

@FunctionalInterface
public interface IncidentListener {
    void onIncident(Incident incident);
}