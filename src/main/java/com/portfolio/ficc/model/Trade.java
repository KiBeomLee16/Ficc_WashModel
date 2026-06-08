package com.portfolio.ficc.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public record Trade(
        String tradeId,
        LocalDateTime timestamp,
        String assetClass,
        String instrumentId,
        LocalDate maturity,
        String currency,
        Side side,
        BigDecimal quantity,
        BigDecimal price,
        String counterpartyId,
        String accountId,
        String beneficialOwner,
        String traderId,
        String desk,
        String book,
        String broker
) {

    public Trade {
        tradeId = requireText(tradeId, "tradeId");
        Objects.requireNonNull(timestamp, "timestamp is required");
        assetClass = requireText(assetClass, "assetClass");
        instrumentId = requireText(instrumentId, "instrumentId");
        Objects.requireNonNull(maturity, "maturity is required");
        currency = requireText(currency, "currency");
        Objects.requireNonNull(side, "side is required");
        quantity = requirePositive(quantity, "quantity");
        price = requirePositive(price, "price");
        counterpartyId = requireText(counterpartyId, "counterpartyId");
        accountId = requireText(accountId, "accountId");
        beneficialOwner = requireText(beneficialOwner, "beneficialOwner");
        traderId = requireText(traderId, "traderId");
        desk = requireText(desk, "desk");
        book = requireText(book, "book");
        broker = requireText(broker, "broker");
    }

    public BigDecimal totalAmount() {
        return quantity.multiply(price);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
