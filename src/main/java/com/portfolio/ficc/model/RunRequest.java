package com.portfolio.ficc.model;

import java.time.LocalDate;
import java.util.Objects;

public record RunRequest(
        long requestId,
        int appId,
        String region,
        LocalDate businessDate,
        String status,
        int attemptCount
) {

    public RunRequest {
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        if (appId <= 0) {
            throw new IllegalArgumentException("appId must be positive");
        }
        region = requireText(region, "region").toUpperCase();
        Objects.requireNonNull(businessDate, "businessDate is required");
        status = requireText(status, "status").toUpperCase();
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount cannot be negative");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
