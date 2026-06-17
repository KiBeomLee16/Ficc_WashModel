package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertDispatcherTest {

	@Mock
	private AlertHistoryRepository alertHistoryRepository;

	@Mock
	private CalibrationResultRepository calibrationResultRepository;

	@Test
	void dispatchStoresAlertPayloadInHistoryDb() {
		long requestId = 18L;
		ModelConfig modelConfig = modelConfig();
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		Alert alert = alert();
		String payload = "{\"alertId\":\"ficc_wash_alert_1\"}";
		when(alertHistoryRepository.saveIfNew(requestId, modelConfig, businessDate, alert, payload)).thenReturn(true);

		AlertDispatcher dispatcher = new AlertDispatcher(alertHistoryRepository, calibrationResultRepository);

		boolean dispatched = dispatcher.dispatch(requestId, modelConfig, businessDate, alert, payload);

		assertTrue(dispatched);
		verify(alertHistoryRepository).saveIfNew(requestId, modelConfig, businessDate, alert, payload);
	}

	@Test
	void dispatchRejectsNullPayload() {
		AlertDispatcher dispatcher = new AlertDispatcher(alertHistoryRepository, calibrationResultRepository);

		NullPointerException exception = assertThrows(NullPointerException.class,
				() -> dispatcher.dispatch(18L, modelConfig(), LocalDate.of(2026, 6, 8), alert(), null));

		assertEquals("alertPayload is required", exception.getMessage());
	}

	@Test
	void dispatchCalibrationResultStoresPayloadInCalibrationHistoryDb() {
		long requestId = 24L;
		ModelConfig modelConfig = modelConfig();
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		Alert alert = alert();
		String payload = "{\"alertId\":\"ficc_wash_alert_1\"}";
		when(calibrationResultRepository.saveIfNew(requestId, modelConfig, businessDate, alert, payload))
				.thenReturn(true);

		AlertDispatcher dispatcher = new AlertDispatcher(alertHistoryRepository, calibrationResultRepository);

		boolean dispatched = dispatcher.dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, payload);

		assertTrue(dispatched);
		verify(calibrationResultRepository).saveIfNew(requestId, modelConfig, businessDate, alert, payload);
	}

	@Test
	void clearHistoryDeletesExistingRunAlertHistory() {
		ModelConfig modelConfig = modelConfig();
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(alertHistoryRepository.deleteByRunCriteria(modelConfig, businessDate)).thenReturn(2);

		AlertDispatcher dispatcher = new AlertDispatcher(alertHistoryRepository, calibrationResultRepository);

		int deletedAlerts = dispatcher.clearHistory(modelConfig, businessDate);

		assertEquals(2, deletedAlerts);
		verify(alertHistoryRepository).deleteByRunCriteria(modelConfig, businessDate);
	}

	@Test
	void clearCalibrationResultsDeletesExistingRequestResultRows() {
		when(calibrationResultRepository.deleteByRequestId(24L)).thenReturn(2);

		AlertDispatcher dispatcher = new AlertDispatcher(alertHistoryRepository, calibrationResultRepository);

		int deletedAlerts = dispatcher.clearCalibrationResults(24L);

		assertEquals(2, deletedAlerts);
		verify(calibrationResultRepository).deleteByRequestId(24L);
	}

	private static ModelConfig modelConfig() {
		return new ModelConfig(1, 1, "NAMR", "NAMR FICC Surveillance App", "FICC_WASH_TRADE",
				"FICC Wash Trade Surveillance Model", "com.portfolio.ficc.surveillance.FiccWashTradeModel");
	}

	private static Alert alert() {
		Trade buyTrade = trade("T-BUY", Side.BUY);
		Trade sellTrade = trade("T-SELL", Side.SELL);
		return new Alert("ficc_wash_alert_1", "FICC_WASH_TRADE", "ONE_TIME_TRANSACTION", buyTrade, sellTrade,
				List.of(buyTrade, sellTrade), buyTrade.quantity(), sellTrade.quantity(), buyTrade.totalAmount(),
				sellTrade.totalAmount(), new BigDecimal("100000000"), List.of("unit-test reason"),
				Instant.parse("2026-06-08T00:00:00Z"));
	}

	private static Trade trade(String tradeId, Side side) {
		return new Trade(tradeId, LocalDateTime.of(2026, 6, 8, 9, 30), "Fixed Income", "UST-10Y",
				LocalDate.of(2036, 5, 15), "USD", side, new BigDecimal("10000000"), new BigDecimal("99.8125"),
				"CP-ALPHA", "ACCT-RATES-ALPHA", "Alpha Capital Master Fund", "TRDR-17", "Rates", "GOVT-RATES-A",
				"BRKR-NY-1");
	}
}
