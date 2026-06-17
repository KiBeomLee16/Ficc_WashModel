package com.portfolio.ficc.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public record AlertBusinessKey(LocalDate tradeDate, String assetClass, String instrumentId, LocalDate maturityDate,
		String currency, String traderId, String counterpartyId) {

	public AlertBusinessKey {
		Objects.requireNonNull(tradeDate, "tradeDate is required");
		assetClass = requireText(assetClass, "assetClass");
		instrumentId = requireText(instrumentId, "instrumentId");
		Objects.requireNonNull(maturityDate, "maturityDate is required");
		currency = requireText(currency, "currency");
		traderId = requireText(traderId, "traderId");
		counterpartyId = requireText(counterpartyId, "counterpartyId");
	}

	public static AlertBusinessKey from(Alert alert) {
		Objects.requireNonNull(alert, "alert is required");
		List<Trade> trades = alert.relatedTrades().stream()
				.sorted(Comparator.comparing(Trade::timestamp).thenComparing(Trade::tradeId)).toList();
		if (trades.isEmpty()) {
			throw new IllegalArgumentException("alert must contain at least one related trade");
		}

		Trade firstTrade = trades.get(0);
		Trade lastTrade = trades.get(trades.size() - 1);
		return new AlertBusinessKey(lastTrade.timestamp().toLocalDate(), firstTrade.assetClass(),
				firstTrade.instrumentId(), firstTrade.maturity(), firstTrade.currency(),
				distinctJoined(trades, Trade::traderId), distinctJoined(trades, Trade::counterpartyId));
	}

	public String hash(String matchType) {
		String input = normalize(matchType) + "|" + tradeDate + "|" + normalize(assetClass) + "|"
				+ normalize(instrumentId) + "|" + maturityDate + "|" + normalize(currency) + "|" + normalize(traderId)
				+ "|" + normalize(counterpartyId);
		return sha256Hex(input);
	}

	private static String distinctJoined(List<Trade> trades, Function<Trade, String> field) {
		return trades.stream().map(field).map(String::trim).distinct().sorted(String.CASE_INSENSITIVE_ORDER)
				.collect(Collectors.joining(","));
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		return value;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private static String sha256Hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte current : hash) {
				hex.append(String.format("%02x", current));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
