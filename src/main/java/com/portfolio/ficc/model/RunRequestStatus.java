package com.portfolio.ficc.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RunRequestStatus(
        long requestId,
        int appId,
        String region,
        LocalDate businessDate,
        String status,
        int alertsGenerated,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage
) {
}
