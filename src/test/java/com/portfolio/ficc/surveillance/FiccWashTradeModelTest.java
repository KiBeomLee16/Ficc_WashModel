package com.portfolio.ficc.surveillance;

import com.portfolio.ficc.io.DatabaseConfig;
import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.portfolio.ficc.TestConfigs.alertDispatcher;

class FiccWashTradeModelTest {

	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 6, 8);
	private static final ModelConfig MODEL_CONFIG = new ModelConfig(1, 1, "NAMR", "NAMR FICC Surveillance App",
			"FICC_WASH_TRADE", "FICC Wash Trade Surveillance Model",
			"com.portfolio.ficc.surveillance.FiccWashTradeModel");

	@Test
	void testAOneTimeTransactionGeneratesAlertFromCounterpartyQuantityAndTotalAmount() {
		TestableFiccWashTradeModel model = new TestableFiccWashTradeModel(
				Map.of("ONE_TIME_MIN_TOTAL_AMOUNT", new BigDecimal("100000000"), "CUMULATIVE_MIN_TOTAL_AMOUNT",
						new BigDecimal("1000000000"), "QUANTITY_TOLERANCE_PERCENT", new BigDecimal("5"),
						"TOTAL_AMOUNT_TOLERANCE_PERCENT", new BigDecimal("5")));

		List<Alert> alerts = model.evaluate(MODEL_CONFIG, List.of(oneTimeBuy(), oneTimeSell()), BUSINESS_DATE);

		assertEquals(1, alerts.size());

		Alert alert = alerts.get(0);
		assertEquals("ficc_wash_alert_1", alert.alertId());
		assertEquals("ONE_TIME_TRANSACTION", alert.matchType());
		assertEquals(new BigDecimal("10000000"), alert.totalBuyQuantity());
		assertEquals(new BigDecimal("9980000"), alert.totalSellQuantity());
		assertEquals(new BigDecimal("998125000.0000"), alert.totalBuyAmount());
		assertEquals(new BigDecimal("996133740.0000"), alert.totalSellAmount());
		assertEquals(new BigDecimal("100000000"), alert.thresholdAmount());
		assertEquals(2, alert.relatedTrades().size());
		assertEquals(List.of("One-time quantity tolerance: actual difference 0.2%, threshold 5%, within threshold.",
				"One-time total amount tolerance: actual difference 0.1995%, threshold 5%, within threshold.",
				"One-time minimum amount: matched amount 996133740.0000, threshold 100000000, above threshold."),
				alert.reasons());
	}

	@Test
	void testBCumulativeTransactionsGenerateGroupedAlert() {
		TestableFiccWashTradeModel model = new TestableFiccWashTradeModel(
				Map.of("ONE_TIME_MIN_TOTAL_AMOUNT", new BigDecimal("100000000"), "CUMULATIVE_MIN_TOTAL_AMOUNT",
						new BigDecimal("5000000"), "QUANTITY_TOLERANCE_PERCENT", new BigDecimal("5"),
						"TOTAL_AMOUNT_TOLERANCE_PERCENT", new BigDecimal("5")));

		List<Trade> trades = List.of(
				cumulativeTrade("T-CUM-BUY-1", Side.BUY, "3000000", "1.10000", LocalDate.of(2026, 6, 6), 0),
				cumulativeTrade("T-CUM-BUY-2", Side.BUY, "2000000", "1.10000", LocalDate.of(2026, 6, 7), 10),
				cumulativeTrade("T-CUM-SELL-1", Side.SELL, "2500000", "1.10000", LocalDate.of(2026, 6, 8), 20),
				cumulativeTrade("T-CUM-SELL-2", Side.SELL, "2450000", "1.10000", LocalDate.of(2026, 6, 8), 30));

		List<Alert> alerts = model.evaluate(MODEL_CONFIG, trades, BUSINESS_DATE);

		assertEquals(1, alerts.size());

		Alert alert = alerts.get(0);
		assertEquals("CUMULATIVE_TRANSACTION", alert.matchType());
		assertEquals(new BigDecimal("5000000"), alert.totalBuyQuantity());
		assertEquals(new BigDecimal("4950000"), alert.totalSellQuantity());
		assertEquals(new BigDecimal("5500000.00000"), alert.totalBuyAmount());
		assertEquals(new BigDecimal("5445000.00000"), alert.totalSellAmount());
		assertEquals(new BigDecimal("5000000"), alert.thresholdAmount());
		assertEquals(4, alert.relatedTrades().size());
		assertEquals(
				List.of("Aggregate quantity tolerance: actual difference 1%, threshold 5%, within threshold.",
						"Aggregate total amount tolerance: actual difference 1%, threshold 5%, within threshold.",
						"Aggregate minimum amount: matched amount 5445000.00000, threshold 5000000, above threshold."),
				alert.reasons());
	}

	@Test
	void evaluateDoesNotAlertWhenCounterpartyDoesNotMatch() {
		TestableFiccWashTradeModel model = new TestableFiccWashTradeModel(
				Map.of("ONE_TIME_MIN_TOTAL_AMOUNT", new BigDecimal("100000000"), "CUMULATIVE_MIN_TOTAL_AMOUNT",
						new BigDecimal("5000000"), "QUANTITY_TOLERANCE_PERCENT", new BigDecimal("5"),
						"TOTAL_AMOUNT_TOLERANCE_PERCENT", new BigDecimal("5")));

		List<Alert> alerts = model.evaluate(MODEL_CONFIG,
				List.of(oneTimeBuy(), trade("T-DIFFERENT-CP", Side.SELL, "CP-OMEGA", "9980000", "99.8130", 3)),
				BUSINESS_DATE);

		assertTrue(alerts.isEmpty());
	}

	@Test
	void generateJsonReturnsReportPayloadAndHandlesNullAlert() {
		TestableFiccWashTradeModel model = new TestableFiccWashTradeModel(
				Map.of("ONE_TIME_MIN_TOTAL_AMOUNT", new BigDecimal("100000000"), "CUMULATIVE_MIN_TOTAL_AMOUNT",
						new BigDecimal("1000000000"), "QUANTITY_TOLERANCE_PERCENT", new BigDecimal("5"),
						"TOTAL_AMOUNT_TOLERANCE_PERCENT", new BigDecimal("5")));
		Alert alert = model.evaluate(MODEL_CONFIG, List.of(oneTimeBuy(), oneTimeSell()), BUSINESS_DATE).get(0);

		String payload = model.generateJson(alert);

		assertTrue(payload.contains("\"alertId\": \"ficc_wash_alert_1\""));
		assertTrue(payload.contains("\"alertType\": \"FICC_WASH_TRADE\""));
		assertTrue(payload.contains("\"matchType\": \"ONE_TIME_TRANSACTION\""));
		assertTrue(payload.contains("\"counterpartyId\": \"CP-ALPHA\""));
		assertTrue(payload.contains("\"thresholdAmount\": 100000000"));
		assertFalse(payload.contains("\"score\""));
		assertEquals("{}", model.generateJson(null));
	}

	private static class TestableFiccWashTradeModel extends FiccWashTradeModel {

		private final Map<String, BigDecimal> thresholdsByName;

		TestableFiccWashTradeModel(Map<String, BigDecimal> thresholdsByName) {
			super(new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""), alertDispatcher());
			this.thresholdsByName = thresholdsByName;
		}

		@Override
		protected BigDecimal getAlertThreshold(ModelConfig modelConfig, String thresholdName, LocalDate businessDate) {
			assertEquals(BUSINESS_DATE, businessDate);
			BigDecimal threshold = thresholdsByName.get(thresholdName);
			if (threshold == null) {
				throw new IllegalArgumentException("Missing test threshold " + thresholdName);
			}
			return threshold;
		}

		@Override
		protected int getThresholdLookupDays(ModelConfig modelConfig, String thresholdName, LocalDate businessDate) {
			assertEquals(BUSINESS_DATE, businessDate);
			return "CUMULATIVE_MIN_TOTAL_AMOUNT".equals(thresholdName) ? 2 : 0;
		}
	}

	private Trade oneTimeBuy() {
		return trade("T-NAMR-UST-001", Side.BUY, "CP-ALPHA", "10000000", "99.8125", 0);
	}

	private Trade oneTimeSell() {
		return trade("T-NAMR-UST-002", Side.SELL, "CP-ALPHA", "9980000", "99.8130", 3);
	}

	private Trade cumulativeTrade(String tradeId, Side side, String quantity, String price, LocalDate businessDate,
			int secondsOffset) {
		return new Trade(tradeId, businessDate.atTime(9, 42).plusSeconds(secondsOffset), "Currencies", "EUR/USD-SPOT",
				LocalDate.of(2026, 6, 10), "USD", side, new BigDecimal(quantity), new BigDecimal(price), "CP-BETA",
				side == Side.BUY ? "ACCT-FX-BETA" : "ACCT-FX-OMEGA",
				side == Side.BUY ? "Beta Macro Fund" : "Omega Global Fund", "TRDR-42", "FX",
				side == Side.BUY ? "FX-SPOT-A" : "FX-SPOT-B", "BRKR-NY-9");
	}

	private Trade trade(String tradeId, Side side, String counterpartyId, String quantity, String price,
			int secondsOffset) {
		return new Trade(tradeId, LocalDateTime.of(2026, 6, 8, 9, 30).plusSeconds(secondsOffset), "Fixed Income",
				"UST-10Y", LocalDate.of(2036, 5, 15), "USD", side, new BigDecimal(quantity), new BigDecimal(price),
				counterpartyId, "ACCT-RATES-ALPHA", "Alpha Capital Master Fund", "TRDR-17", "Rates", "GOVT-RATES-A",
				"BRKR-NY-1");
	}
}
