package com.portfolio.ficc.model;

import java.time.LocalDate;

public record RunSummary(
        int appId,
        int modelId,
        String modelCode,
        String region,
        LocalDate businessDate,
        int tradesProcessed,
        int alertsGenerated,
        int alertsDispatched,
        int duplicateAlerts
) {

    public RunSummary {
        if (appId <= 0) {
            throw new IllegalArgumentException("appId must be positive");
        }
        if (modelId <= 0) {
            throw new IllegalArgumentException("modelId must be positive");
        }
        if (modelCode == null || modelCode.isBlank()) {
            throw new IllegalArgumentException("modelCode is required");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("region is required");
        }
        if (businessDate == null) {
            throw new IllegalArgumentException("businessDate is required");
        }
        if (tradesProcessed < 0 || alertsGenerated < 0 || alertsDispatched < 0 || duplicateAlerts < 0) {
            throw new IllegalArgumentException("run counts cannot be negative");
        }
        modelCode = modelCode.trim().toUpperCase();
        region = region.trim().toUpperCase();
    }
}
