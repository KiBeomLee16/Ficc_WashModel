package com.portfolio.ficc.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CalibrationAlertHistoryResult(
        long calibrationAlertHistoryId,
        String alertId,
        long requestId,
        int appId,
        int modelId,
        String region,
        String alertType,
        String matchType,
        LocalDate businessDate,
        LocalDate firstTradeDate,
        LocalDate lastTradeDate,
        String relatedTradeIds,
        String alertBusinessKeyHash,
        LocalDate tradeDate,
        String assetClass,
        String instrumentId,
        LocalDate maturityDate,
        String currency,
        String traderId,
        String counterpartyId,
        String alertPayload,
        BigDecimal oneTimeMinTotalAmount,
        BigDecimal cumulativeMinTotalAmount,
        BigDecimal quantityTolerancePercent,
        BigDecimal totalAmountTolerancePercent,
        int cumulativeLookupDays,
        String dispatchStatus,
        LocalDateTime createdAt
) {
}
