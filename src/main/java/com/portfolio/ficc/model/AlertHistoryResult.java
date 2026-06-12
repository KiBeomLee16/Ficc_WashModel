package com.portfolio.ficc.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AlertHistoryResult(
        long alertHistoryId,
        String alertId,
        int appId,
        int modelId,
        String region,
        String alertType,
        String matchType,
        LocalDate businessDate,
        LocalDate firstTradeDate,
        LocalDate lastTradeDate,
        String relatedTradeIds,
        String alertPayload,
        String dispatchStatus,
        LocalDateTime createdAt
) {
}
