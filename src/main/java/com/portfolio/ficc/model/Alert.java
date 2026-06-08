package com.portfolio.ficc.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Alert(
        String alertId,
        String alertType,
        String matchType,
        Trade tradeA,
        Trade tradeB,
        List<Trade> relatedTrades,
        BigDecimal totalBuyQuantity,
        BigDecimal totalSellQuantity,
        BigDecimal totalBuyAmount,
        BigDecimal totalSellAmount,
        BigDecimal thresholdAmount,
        List<String> reasons,
        Instant createdAt
) {

    public Alert {
        alertId = requireText(alertId, "alertId");
        alertType = requireText(alertType, "alertType");
        matchType = requireText(matchType, "matchType");
        Objects.requireNonNull(tradeA, "tradeA is required");
        Objects.requireNonNull(tradeB, "tradeB is required");
        relatedTrades = List.copyOf(Objects.requireNonNull(relatedTrades, "relatedTrades is required"));
        totalBuyQuantity = requireNonNegative(totalBuyQuantity, "totalBuyQuantity");
        totalSellQuantity = requireNonNegative(totalSellQuantity, "totalSellQuantity");
        totalBuyAmount = requireNonNegative(totalBuyAmount, "totalBuyAmount");
        totalSellAmount = requireNonNegative(totalSellAmount, "totalSellAmount");
        thresholdAmount = requireNonNegative(thresholdAmount, "thresholdAmount");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons is required"));
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return value;
    }
}
