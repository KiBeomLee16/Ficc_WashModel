package com.portfolio.ficc.surveillance;

import com.portfolio.ficc.io.AlertDispatcher;
import com.portfolio.ficc.io.DatabaseConfig;
import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractSurveillanceModel {

	private static final String THRESHOLD_PROCEDURE = "sp_get_surveillance_model_threshold";
	private static final String DEFAULT_JSON_VALUE = "";

	private final DatabaseConfig databaseConfig;
	private final AlertDispatcher alertDispatcher;
	private final AtomicInteger alertIdSequence = new AtomicInteger(1);

	protected AbstractSurveillanceModel(DatabaseConfig databaseConfig, AlertDispatcher alertDispatcher) {
		this.databaseConfig = Objects.requireNonNull(databaseConfig, "databaseConfig is required");
		this.alertDispatcher = Objects.requireNonNull(alertDispatcher, "alertDispatcher is required");
	}

	public abstract String modelCode();

	/**
	 * 1. Load trades for the selected model.
	 */
	public abstract List<Trade> getTrades(ModelConfig modelConfig, String region, LocalDate businessDate);

	/**
	 * 2. Evaluate trades and return model-specific alerts.
	 */
	public abstract List<Alert> evaluate(ModelConfig modelConfig, List<Trade> trades, LocalDate businessDate);

	/**
	 * 3. Generate a simple alert ID backed by an atomic integer sequence.
	 */
	public String generateAlertId(Trade tradeA, Trade tradeB) {
		return alertIdPrefix() + alertIdSequence.getAndIncrement();
	}

	/**
	 * 4. Convert an alert into a JSON payload.
	 */
	public String generateJson(Alert alert) {
		if (alert == null) {
			return "{}";
		}

		StringBuilder json = new StringBuilder();
		json.append("{\n");
		appendStringField(json, 2, "alertId", alert.alertId(), true);
		appendStringField(json, 2, "alertType", alert.alertType(), true);
		appendStringField(json, 2, "matchType", alert.matchType(), true);
		appendTradeField(json, 2, "tradeA", alert.tradeA(), true);
		appendTradeField(json, 2, "tradeB", alert.tradeB(), true);
		appendRelatedTrades(json, 2, alert.relatedTrades(), true);
		appendBigDecimalField(json, 2, "totalBuyQuantity", alert.totalBuyQuantity(), true);
		appendBigDecimalField(json, 2, "totalSellQuantity", alert.totalSellQuantity(), true);
		appendBigDecimalField(json, 2, "totalBuyAmount", alert.totalBuyAmount(), true);
		appendBigDecimalField(json, 2, "totalSellAmount", alert.totalSellAmount(), true);
		appendBigDecimalField(json, 2, "thresholdAmount", alert.thresholdAmount(), true);
		appendReasons(json, 2, alert.reasons(), true);
		appendStringField(json, 2, "createdAt", alert.createdAt() == null ? null : alert.createdAt().toString(), false);
		json.append("}");
		return json.toString();
	}

	/**
	 * 5. Dispatch the alert payload after JSON generation.
	 */
	public boolean dispatchAlert(long requestId, ModelConfig modelConfig, LocalDate businessDate, Alert alert,
			String alertPayload) {
		return alertDispatcher.dispatch(requestId, modelConfig, businessDate, alert, alertPayload);
	}

	public boolean dispatchCalibrationResult(long requestId, ModelConfig modelConfig, LocalDate businessDate,
			Alert alert, String alertPayload) {
		return alertDispatcher.dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, alertPayload);
	}

	public int clearAlertHistory(ModelConfig modelConfig, LocalDate businessDate) {
		return alertDispatcher.clearHistory(modelConfig, businessDate);
	}

	public int clearCalibrationResults(long requestId) {
		return alertDispatcher.clearCalibrationResults(requestId);
	}

	protected Connection getConnection() throws SQLException {
		return databaseConfig.getConnection();
	}

	protected BigDecimal getAlertThreshold(ModelConfig modelConfig, String thresholdName, LocalDate businessDate) {
		Objects.requireNonNull(modelConfig, "modelConfig is required");
		Objects.requireNonNull(businessDate, "businessDate is required");
		String normalizedThresholdName = thresholdName.toUpperCase();
		String callSql = "{CALL " + THRESHOLD_PROCEDURE + "(?, ?, ?, ?)}";

		try (Connection connection = getConnection(); CallableStatement statement = connection.prepareCall(callSql)) {

			statement.setInt(1, modelConfig.appId());
			statement.setInt(2, modelConfig.modelId());
			statement.setString(3, modelConfig.region());
			statement.setString(4, normalizedThresholdName);

			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					BigDecimal threshold = resultSet.getBigDecimal("threshold_value");
					if (threshold.signum() < 0) {
						throw new IllegalStateException("Threshold cannot be negative for appid=" + modelConfig.appId()
								+ ", modelid=" + modelConfig.modelId() + ", region=" + modelConfig.region()
								+ ", thresholdName=" + normalizedThresholdName + ", businessDate=" + businessDate);
					}
					return threshold;
				}
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to load threshold for appid=" + modelConfig.appId() + ", modelid="
					+ modelConfig.modelId() + ", region=" + modelConfig.region() + ", thresholdName="
					+ normalizedThresholdName + ", businessDate=" + businessDate, exception);
		}

		throw new IllegalArgumentException("No active threshold found for appid=" + modelConfig.appId() + ", modelid="
				+ modelConfig.modelId() + ", region=" + modelConfig.region() + ", thresholdName="
				+ normalizedThresholdName + ", businessDate=" + businessDate);
	}

	protected int getThresholdLookupDays(ModelConfig modelConfig, String thresholdName, LocalDate businessDate) {
		Objects.requireNonNull(modelConfig, "modelConfig is required");
		Objects.requireNonNull(businessDate, "businessDate is required");
		String normalizedThresholdName = thresholdName.toUpperCase();
		String callSql = "{CALL " + THRESHOLD_PROCEDURE + "(?, ?, ?, ?)}";

		try (Connection connection = getConnection(); CallableStatement statement = connection.prepareCall(callSql)) {

			statement.setInt(1, modelConfig.appId());
			statement.setInt(2, modelConfig.modelId());
			statement.setString(3, modelConfig.region());
			statement.setString(4, normalizedThresholdName);

			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					int lookupDays = resultSet.getInt("lookup_days");
					if (resultSet.wasNull()) {
						lookupDays = 0;
					}
					if (lookupDays < 0) {
						throw new IllegalStateException("lookup_days cannot be negative for appid="
								+ modelConfig.appId() + ", modelid=" + modelConfig.modelId() + ", region="
								+ modelConfig.region() + ", thresholdName=" + normalizedThresholdName
								+ ", businessDate=" + businessDate);
					}
					return lookupDays;
				}
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to load threshold lookup days for appid=" + modelConfig.appId()
					+ ", modelid=" + modelConfig.modelId() + ", region=" + modelConfig.region() + ", thresholdName="
					+ normalizedThresholdName + ", businessDate=" + businessDate, exception);
		}

		throw new IllegalArgumentException("No active threshold found for appid=" + modelConfig.appId() + ", modelid="
				+ modelConfig.modelId() + ", region=" + modelConfig.region() + ", thresholdName="
				+ normalizedThresholdName + ", businessDate=" + businessDate);
	}

	protected Trade toTrade(ResultSet resultSet) throws SQLException {
		return new Trade(resultSet.getString("trade_id"), resultSet.getTimestamp("trade_timestamp").toLocalDateTime(),
				resultSet.getString("asset_class"), resultSet.getString("instrument_id"),
				resultSet.getDate("maturity").toLocalDate(), resultSet.getString("currency"),
				Side.valueOf(resultSet.getString("side").toUpperCase()), resultSet.getBigDecimal("quantity"),
				resultSet.getBigDecimal("price"), resultSet.getString("counterparty_id"),
				resultSet.getString("account_id"), resultSet.getString("beneficial_owner"),
				resultSet.getString("trader_id"), resultSet.getString("desk"), resultSet.getString("book"),
				resultSet.getString("broker"));
	}

	protected String alertIdPrefix() {
		return "ficc_wash_alert_";
	}

	private void appendTradeField(StringBuilder json, int spaces, String fieldName, Trade trade, boolean includeComma) {
		indent(json, spaces).append(quote(fieldName)).append(": ");
		json.append(tradeToJson(trade, spaces));
		appendCommaAndNewLine(json, includeComma);
	}

	private String tradeToJson(Trade trade, int parentSpaces) {
		if (trade == null) {
			return "{}";
		}

		int spaces = parentSpaces + 2;
		StringBuilder json = new StringBuilder();
		json.append("{\n");
		appendStringField(json, spaces, "tradeId", trade.tradeId(), true);
		appendStringField(json, spaces, "timestamp", trade.timestamp() == null ? null : trade.timestamp().toString(),
				true);
		appendStringField(json, spaces, "assetClass", trade.assetClass(), true);
		appendStringField(json, spaces, "instrumentId", trade.instrumentId(), true);
		appendStringField(json, spaces, "maturity", trade.maturity() == null ? null : trade.maturity().toString(),
				true);
		appendStringField(json, spaces, "currency", trade.currency(), true);
		appendStringField(json, spaces, "side", trade.side() == null ? null : trade.side().name(), true);
		appendBigDecimalField(json, spaces, "quantity", trade.quantity(), true);
		appendBigDecimalField(json, spaces, "price", trade.price(), true);
		appendBigDecimalField(json, spaces, "totalAmount", trade.totalAmount(), true);
		appendStringField(json, spaces, "counterpartyId", trade.counterpartyId(), true);
		appendStringField(json, spaces, "accountId", trade.accountId(), true);
		appendStringField(json, spaces, "beneficialOwner", trade.beneficialOwner(), true);
		appendStringField(json, spaces, "traderId", trade.traderId(), true);
		appendStringField(json, spaces, "desk", trade.desk(), true);
		appendStringField(json, spaces, "book", trade.book(), true);
		appendStringField(json, spaces, "broker", trade.broker(), false);
		indent(json, parentSpaces).append("}");
		return json.toString();
	}

	private void appendRelatedTrades(StringBuilder json, int spaces, List<Trade> relatedTrades, boolean includeComma) {
		indent(json, spaces).append(quote("relatedTrades")).append(": [\n");
		List<Trade> safeTrades = relatedTrades == null ? List.of() : relatedTrades;
		for (int i = 0; i < safeTrades.size(); i++) {
			indent(json, spaces + 2).append(tradeToJson(safeTrades.get(i), spaces + 2));
			appendCommaAndNewLine(json, i < safeTrades.size() - 1);
		}
		indent(json, spaces).append("]");
		appendCommaAndNewLine(json, includeComma);
	}

	private void appendReasons(StringBuilder json, int spaces, List<String> reasons, boolean includeComma) {
		indent(json, spaces).append(quote("reasons")).append(": [\n");
		List<String> safeReasons = reasons == null ? List.of() : reasons;
		for (int i = 0; i < safeReasons.size(); i++) {
			indent(json, spaces + 2).append(quote(safeReasons.get(i)));
			appendCommaAndNewLine(json, i < safeReasons.size() - 1);
		}
		indent(json, spaces).append("]");
		appendCommaAndNewLine(json, includeComma);
	}

	private void appendStringField(StringBuilder json, int spaces, String fieldName, String value,
			boolean includeComma) {
		indent(json, spaces).append(quote(fieldName)).append(": ").append(quote(value));
		appendCommaAndNewLine(json, includeComma);
	}

	private void appendBigDecimalField(StringBuilder json, int spaces, String fieldName, BigDecimal value,
			boolean includeComma) {
		String jsonValue = value == null ? "0" : value.toPlainString();
		indent(json, spaces).append(quote(fieldName)).append(": ").append(jsonValue);
		appendCommaAndNewLine(json, includeComma);
	}

	private void appendCommaAndNewLine(StringBuilder json, boolean includeComma) {
		if (includeComma) {
			json.append(",");
		}
		json.append("\n");
	}

	private StringBuilder indent(StringBuilder json, int spaces) {
		return json.append(" ".repeat(spaces));
	}

	private String quote(String value) {
		return "\"" + escapeJson(value == null ? DEFAULT_JSON_VALUE : value) + "\"";
	}

	private String escapeJson(String value) {
		StringBuilder escaped = new StringBuilder();
		for (char character : value.toCharArray()) {
			switch (character) {
			case '"' -> escaped.append("\\\"");
			case '\\' -> escaped.append("\\\\");
			case '\b' -> escaped.append("\\b");
			case '\f' -> escaped.append("\\f");
			case '\n' -> escaped.append("\\n");
			case '\r' -> escaped.append("\\r");
			case '\t' -> escaped.append("\\t");
			default -> escaped.append(character);
			}
		}
		return escaped.toString();
	}
}
