package com.portfolio.ficc.model;

import java.math.BigDecimal;
import java.util.Objects;

public record ThresholdSnapshot(
        BigDecimal oneTimeMinTotalAmount,
        BigDecimal cumulativeMinTotalAmount,
        BigDecimal quantityTolerancePercent,
        BigDecimal totalAmountTolerancePercent,
        int cumulativeLookupDays
) {

    public ThresholdSnapshot {
        oneTimeMinTotalAmount = requireNonNegative(oneTimeMinTotalAmount, "oneTimeMinTotalAmount");
        cumulativeMinTotalAmount = requireNonNegative(cumulativeMinTotalAmount, "cumulativeMinTotalAmount");
        quantityTolerancePercent = requireNonNegative(quantityTolerancePercent, "quantityTolerancePercent");
        totalAmountTolerancePercent = requireNonNegative(totalAmountTolerancePercent, "totalAmountTolerancePercent");
        if (cumulativeLookupDays < 0) {
            throw new IllegalArgumentException("cumulativeLookupDays cannot be negative");
        }
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return value;
    }
}
