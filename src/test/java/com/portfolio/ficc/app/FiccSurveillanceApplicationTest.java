package com.portfolio.ficc.app;

import com.portfolio.ficc.io.DatabaseConfig;
import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.RunSummary;
import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;
import com.portfolio.ficc.surveillance.AbstractSurveillanceModel;
import com.portfolio.ficc.surveillance.SurveillanceModelRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiccSurveillanceApplicationTest {

	@Mock
	private Connection connection;

	@Mock
	private CallableStatement statement;

	@Mock
	private ResultSet resultSet;

	@Mock
	private AbstractSurveillanceModel model;

	@Mock
	private SurveillanceModelRegistry modelRegistry;

	@Test
	void runExecutesSelectedModelPipelineInSeparateSteps() {
		long requestId = 18L;
		ModelConfig modelConfig = modelConfig("FICC_WASH_TRADE");
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		Trade tradeA = trade("T-RUN-001", Side.BUY);
		Trade tradeB = trade("T-RUN-002", Side.SELL);
		List<Trade> trades = List.of(tradeA, tradeB);
		Alert alert = new Alert("ficc_wash_alert_1", "FICC_WASH_TRADE", "ONE_TIME_TRANSACTION", tradeA, tradeB,
				List.of(tradeA, tradeB), tradeA.quantity(), tradeB.quantity(), tradeA.totalAmount(),
				tradeB.totalAmount(), new BigDecimal("100000000"), List.of("unit-test reason"),
				Instant.parse("2026-06-08T00:00:00Z"));
		String alertPayload = "{\"alertId\":\"ficc_wash_alert_1\"}";

		when(modelRegistry.getModel(modelConfig.modelClassName())).thenReturn(model);
		when(model.modelCode()).thenReturn("FICC_WASH_TRADE");
		when(model.getTrades(modelConfig, "NAMR", businessDate)).thenReturn(trades);
		when(model.evaluate(modelConfig, trades, businessDate)).thenReturn(List.of(alert));
		when(model.generateJson(alert)).thenReturn(alertPayload);
		when(model.dispatchAlert(requestId, modelConfig, businessDate, alert, alertPayload)).thenReturn(true);
		when(model.dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, alertPayload))
				.thenReturn(true);

		PipelineApplication application = new PipelineApplication(modelConfig, modelRegistry);

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		PrintStream originalOut = System.out;
		try {
			System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
			RunSummary summary = application.run(requestId, 2, "emea", businessDate);
			assertEquals(2, summary.tradesProcessed());
			assertEquals(1, summary.alertsGenerated());
			assertEquals(1, summary.alertsDispatched());
			assertEquals(0, summary.duplicateAlerts());
		} finally {
			System.setOut(originalOut);
		}

		assertEquals(2, application.requestedAppId);
		assertEquals("emea", application.requestedRegion);

		InOrder order = inOrder(modelRegistry, model);
		order.verify(modelRegistry).getModel(modelConfig.modelClassName());
		order.verify(model).modelCode();
		order.verify(model).getTrades(modelConfig, "NAMR", businessDate);
		order.verify(model).evaluate(modelConfig, trades, businessDate);
		order.verify(model).clearCalibrationResults(requestId);
		order.verify(model).clearAlertHistory(modelConfig, businessDate);
		order.verify(model).generateJson(alert);
		order.verify(model).dispatchAlert(requestId, modelConfig, businessDate, alert, alertPayload);
		order.verify(model).dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, alertPayload);
	}

	@Test
	void runSkipsDispatchWhenAlertHistoryAlreadyExists() {
		long requestId = 19L;
		ModelConfig modelConfig = modelConfig("FICC_WASH_TRADE");
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		Trade tradeA = trade("T-RUN-001", Side.BUY);
		Trade tradeB = trade("T-RUN-002", Side.SELL);
		List<Trade> trades = List.of(tradeA, tradeB);
		Alert alert = new Alert("ficc_wash_alert_1", "FICC_WASH_TRADE", "CUMULATIVE_TRANSACTION", tradeA, tradeB,
				List.of(tradeA, tradeB), tradeA.quantity(), tradeB.quantity(), tradeA.totalAmount(),
				tradeB.totalAmount(), new BigDecimal("100000000"), List.of("duplicate cumulative report"),
				Instant.parse("2026-06-08T00:00:00Z"));
		String alertPayload = "{\"alertId\":\"ficc_wash_alert_1\"}";

		when(modelRegistry.getModel(modelConfig.modelClassName())).thenReturn(model);
		when(model.modelCode()).thenReturn("FICC_WASH_TRADE");
		when(model.getTrades(modelConfig, "NAMR", businessDate)).thenReturn(trades);
		when(model.evaluate(modelConfig, trades, businessDate)).thenReturn(List.of(alert));
		when(model.generateJson(alert)).thenReturn(alertPayload);
		when(model.dispatchAlert(requestId, modelConfig, businessDate, alert, alertPayload)).thenReturn(false);
		when(model.dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, alertPayload))
				.thenReturn(false);

		PipelineApplication application = new PipelineApplication(modelConfig, modelRegistry);

		RunSummary summary = application.run(requestId, 1, "NAMR", businessDate);

		assertEquals(0, summary.alertsDispatched());
		assertEquals(1, summary.duplicateAlerts());
		verify(model).dispatchAlert(requestId, modelConfig, businessDate, alert, alertPayload);
		verify(model).dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, alertPayload);
	}

	@Test
	void runReturnsZeroAlertSummaryAndDoesNotDispatchWhenNoAlerts() {
		ModelConfig modelConfig = modelConfig("FICC_WASH_TRADE");
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		List<Trade> trades = List.of(trade("T-RUN-001", Side.BUY));

		when(modelRegistry.getModel(modelConfig.modelClassName())).thenReturn(model);
		when(model.modelCode()).thenReturn("FICC_WASH_TRADE");
		when(model.getTrades(modelConfig, "NAMR", businessDate)).thenReturn(trades);
		when(model.evaluate(modelConfig, trades, businessDate)).thenReturn(List.of());

		PipelineApplication application = new PipelineApplication(modelConfig, modelRegistry);

		RunSummary summary = application.run(20L, 1, "NAMR", businessDate);

		assertEquals(1, summary.tradesProcessed());
		assertEquals(0, summary.alertsGenerated());
		assertEquals(0, summary.alertsDispatched());
		assertEquals(0, summary.duplicateAlerts());
		verify(model).clearCalibrationResults(20L);
		verify(model).clearAlertHistory(modelConfig, businessDate);
		verify(model, never()).generateJson(any(Alert.class));
		verify(model, never()).dispatchAlert(anyLong(), any(ModelConfig.class), any(LocalDate.class), any(Alert.class),
				any(String.class));
		verify(model, never()).dispatchCalibrationResult(anyLong(), any(ModelConfig.class), any(LocalDate.class),
				any(Alert.class), any(String.class));
	}

	@Test
	void runCalibrationModelWritesOnlyCalibrationResults() {
		long requestId = 22L;
		ModelConfig modelConfig = modelConfig(4, "NAMRC", "FICC_WASH_TRADE");
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		Trade tradeA = trade("T-RUN-001", Side.BUY);
		Trade tradeB = trade("T-RUN-002", Side.SELL);
		List<Trade> trades = List.of(tradeA, tradeB);
		Alert alert = new Alert("ficc_wash_alert_1", "FICC_WASH_TRADE", "ONE_TIME_TRANSACTION", tradeA, tradeB,
				List.of(tradeA, tradeB), tradeA.quantity(), tradeB.quantity(), tradeA.totalAmount(),
				tradeB.totalAmount(), new BigDecimal("100000000"), List.of("calibration result"),
				Instant.parse("2026-06-08T00:00:00Z"));
		String alertPayload = "{\"alertId\":\"ficc_wash_alert_1\"}";

		when(modelRegistry.getModel(modelConfig.modelClassName())).thenReturn(model);
		when(model.modelCode()).thenReturn("FICC_WASH_TRADE");
		when(model.getTrades(modelConfig, "NAMRC", businessDate)).thenReturn(trades);
		when(model.evaluate(modelConfig, trades, businessDate)).thenReturn(List.of(alert));
		when(model.generateJson(alert)).thenReturn(alertPayload);
		when(model.dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, alertPayload))
				.thenReturn(true);

		PipelineApplication application = new PipelineApplication(modelConfig, modelRegistry);

		RunSummary summary = application.run(requestId, 4, "NAMRC", businessDate);

		assertEquals(1, summary.alertsDispatched());
		assertEquals(0, summary.duplicateAlerts());
		verify(model, never()).clearCalibrationResults(requestId);
		verify(model, never()).clearAlertHistory(modelConfig, businessDate);
		verify(model, never()).dispatchAlert(requestId, modelConfig, businessDate, alert, alertPayload);
		verify(model).dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, alertPayload);
	}

	@Test
	void runApacProductionModelWritesProductionAndCalibrationMirror() {
		long requestId = 23L;
		ModelConfig modelConfig = modelConfig(3, "APAC", "FICC_WASH_TRADE");
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		Trade tradeA = trade("T-APAC-001", Side.BUY);
		Trade tradeB = trade("T-APAC-002", Side.SELL);
		List<Trade> trades = List.of(tradeA, tradeB);
		Alert alert = new Alert("ficc_wash_alert_1", "FICC_WASH_TRADE", "ONE_TIME_TRANSACTION", tradeA, tradeB,
				List.of(tradeA, tradeB), tradeA.quantity(), tradeB.quantity(), tradeA.totalAmount(),
				tradeB.totalAmount(), new BigDecimal("100000000"), List.of("apac production result"),
				Instant.parse("2026-06-08T00:00:00Z"));
		String alertPayload = "{\"alertId\":\"ficc_wash_alert_1\"}";

		when(modelRegistry.getModel(modelConfig.modelClassName())).thenReturn(model);
		when(model.modelCode()).thenReturn("FICC_WASH_TRADE");
		when(model.getTrades(modelConfig, "APAC", businessDate)).thenReturn(trades);
		when(model.evaluate(modelConfig, trades, businessDate)).thenReturn(List.of(alert));
		when(model.generateJson(alert)).thenReturn(alertPayload);
		when(model.dispatchAlert(requestId, modelConfig, businessDate, alert, alertPayload)).thenReturn(true);
		when(model.dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, alertPayload))
				.thenReturn(true);

		PipelineApplication application = new PipelineApplication(modelConfig, modelRegistry);

		RunSummary summary = application.run(requestId, 3, "APAC", businessDate);

		assertEquals(1, summary.alertsDispatched());
		verify(model).clearCalibrationResults(requestId);
		verify(model).clearAlertHistory(modelConfig, businessDate);
		verify(model).dispatchAlert(requestId, modelConfig, businessDate, alert, alertPayload);
		verify(model).dispatchCalibrationResult(requestId, modelConfig, businessDate, alert, alertPayload);
	}

	@Test
	void runRejectsModelCodeMismatchFromPrivateModelLookup() {
		ModelConfig modelConfig = modelConfig("UNEXPECTED_MODEL");
		when(modelRegistry.getModel(modelConfig.modelClassName())).thenReturn(model);
		when(model.modelCode()).thenReturn("FICC_WASH_TRADE");

		PipelineApplication application = new PipelineApplication(modelConfig, modelRegistry);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> application.run(21L, 1, "NAMR", LocalDate.of(2026, 6, 8)));

		assertEquals("Configured modelCode=UNEXPECTED_MODEL does not match registered modelCode=FICC_WASH_TRADE "
				+ "for class=com.portfolio.ficc.surveillance.FiccWashTradeModel", exception.getMessage());
	}

	@Test
	void getSpecificModelCallsConfigStoredProcedureAndMapsResult() throws Exception {
		when(connection.prepareCall("{CALL sp_get_surveillance_model_config(?, ?)}")).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(true);
		when(resultSet.getInt("appid")).thenReturn(1);
		when(resultSet.getInt("modelid")).thenReturn(10);
		when(resultSet.getString("region")).thenReturn("NAMR");
		when(resultSet.getString("app_name")).thenReturn("NAMR FICC Surveillance App");
		when(resultSet.getString("model_code")).thenReturn("FICC_WASH_TRADE");
		when(resultSet.getString("model_name")).thenReturn("FICC Wash Trade Surveillance Model");
		when(resultSet.getString("model_class_name")).thenReturn("com.portfolio.ficc.surveillance.FiccWashTradeModel");

		FiccSurveillanceApplication application = new ConnectionBackedApplication(connection);

		ModelConfig modelConfig = application.getSpecificModel(1, "namr");

		assertEquals(1, modelConfig.appId());
		assertEquals(10, modelConfig.modelId());
		assertEquals("NAMR", modelConfig.region());
		assertEquals("NAMR FICC Surveillance App", modelConfig.appName());
		assertEquals("FICC_WASH_TRADE", modelConfig.modelCode());
		assertEquals("FICC Wash Trade Surveillance Model", modelConfig.modelName());
		assertEquals("com.portfolio.ficc.surveillance.FiccWashTradeModel", modelConfig.modelClassName());

		verify(statement).setInt(1, 1);
		verify(statement).setString(2, "NAMR");
	}

	@Test
	void getSpecificModelFailsWhenConfigProcedureReturnsNoRows() throws Exception {
		when(connection.prepareCall("{CALL sp_get_surveillance_model_config(?, ?)}")).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(false);

		FiccSurveillanceApplication application = new ConnectionBackedApplication(connection);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> application.getSpecificModel(1, "emea"));

		assertEquals("No active model config found for appid=1, region=EMEA", exception.getMessage());
		verify(statement).setString(2, "EMEA");
	}

	private static ModelConfig modelConfig(String modelCode) {
		return modelConfig(1, "NAMR", modelCode);
	}

	private static ModelConfig modelConfig(int appId, String region, String modelCode) {
		return new ModelConfig(appId, 1, region, "NAMR FICC Surveillance App", modelCode,
				"FICC Wash Trade Surveillance Model", "com.portfolio.ficc.surveillance.FiccWashTradeModel");
	}

	private static Trade trade(String tradeId, Side side) {
		return new Trade(tradeId, LocalDateTime.of(2026, 6, 8, 9, 30), "Fixed Income", "UST-10Y",
				LocalDate.of(2036, 5, 15), "USD", side, new BigDecimal("10000000"), new BigDecimal("99.8125"),
				"CP-ALPHA", "ACCT-RATES-ALPHA", "Alpha Capital Master Fund", "TRDR-17", "Rates", "GOVT-RATES-A",
				"BRKR-NY-1");
	}

	private static class PipelineApplication extends FiccSurveillanceApplication {

		private final ModelConfig modelConfig;
		private int requestedAppId;
		private String requestedRegion;

		PipelineApplication(ModelConfig modelConfig, SurveillanceModelRegistry modelRegistry) {
			super(new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""), modelRegistry);
			this.modelConfig = modelConfig;
		}

		@Override
		public ModelConfig getSpecificModel(int appId, String region) {
			this.requestedAppId = appId;
			this.requestedRegion = region;
			return modelConfig;
		}
	}

	private static class ConnectionBackedApplication extends FiccSurveillanceApplication {

		private final Connection connection;

		ConnectionBackedApplication(Connection connection) {
			super(new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""),
					new SurveillanceModelRegistry(List.of()));
			this.connection = connection;
		}

		@Override
		protected Connection getConnection() throws SQLException {
			return connection;
		}
	}
}
