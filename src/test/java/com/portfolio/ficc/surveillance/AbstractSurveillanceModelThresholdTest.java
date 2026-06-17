package com.portfolio.ficc.surveillance;

import com.portfolio.ficc.io.DatabaseConfig;
import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.portfolio.ficc.TestConfigs.alertDispatcher;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractSurveillanceModelThresholdTest {

	private static final ModelConfig MODEL_CONFIG = new ModelConfig(1, 1, "NAMR", "NAMR FICC Surveillance App",
			"FICC_WASH_TRADE", "FICC Wash Trade Surveillance Model",
			"com.portfolio.ficc.surveillance.FiccWashTradeModel");

	@Mock
	private Connection connection;

	@Mock
	private CallableStatement statement;

	@Mock
	private ResultSet resultSet;

	@Test
	void getAlertThresholdCallsThresholdStoredProcedureAndReturnsValue() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(connection.prepareCall("{CALL sp_get_surveillance_model_threshold(?, ?, ?, ?)}")).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(true);
		when(resultSet.getBigDecimal("threshold_value")).thenReturn(new BigDecimal("100000000.000000"));

		ThresholdProbeModel model = new ThresholdProbeModel(connection);

		BigDecimal threshold = model.loadThreshold(MODEL_CONFIG, "one_time_min_total_amount", businessDate);

		assertEquals(new BigDecimal("100000000.000000"), threshold);
		verify(statement).setInt(1, 1);
		verify(statement).setInt(2, 1);
		verify(statement).setString(3, "NAMR");
		verify(statement).setString(4, "ONE_TIME_MIN_TOTAL_AMOUNT");
	}

	@Test
	void getAlertThresholdFailsWhenProcedureReturnsNoRows() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(connection.prepareCall("{CALL sp_get_surveillance_model_threshold(?, ?, ?, ?)}")).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(false);

		ThresholdProbeModel model = new ThresholdProbeModel(connection);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> model.loadThreshold(MODEL_CONFIG, "ONE_TIME_MIN_TOTAL_AMOUNT", businessDate));

		assertEquals("No active threshold found for appid=1, modelid=1, region=NAMR, "
				+ "thresholdName=ONE_TIME_MIN_TOTAL_AMOUNT, businessDate=2026-06-08", exception.getMessage());
	}

	@Test
	void getAlertThresholdRejectsNegativeThresholdValue() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(connection.prepareCall("{CALL sp_get_surveillance_model_threshold(?, ?, ?, ?)}")).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(true);
		when(resultSet.getBigDecimal("threshold_value")).thenReturn(new BigDecimal("-1.000000"));

		ThresholdProbeModel model = new ThresholdProbeModel(connection);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> model.loadThreshold(MODEL_CONFIG, "ONE_TIME_MIN_TOTAL_AMOUNT", businessDate));

		assertEquals("Threshold cannot be negative for appid=1, modelid=1, region=NAMR, "
				+ "thresholdName=ONE_TIME_MIN_TOTAL_AMOUNT, businessDate=2026-06-08", exception.getMessage());
	}

	@Test
	void getThresholdLookupDaysCallsThresholdProcedureAndReturnsLookupWindow() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(connection.prepareCall("{CALL sp_get_surveillance_model_threshold(?, ?, ?, ?)}")).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(resultSet);
		when(resultSet.next()).thenReturn(true);
		when(resultSet.getInt("lookup_days")).thenReturn(2);
		when(resultSet.wasNull()).thenReturn(false);

		ThresholdProbeModel model = new ThresholdProbeModel(connection);

		int lookupDays = model.loadLookupDays(MODEL_CONFIG, "cumulative_min_total_amount", businessDate);

		assertEquals(2, lookupDays);
		verify(statement).setInt(1, 1);
		verify(statement).setInt(2, 1);
		verify(statement).setString(3, "NAMR");
		verify(statement).setString(4, "CUMULATIVE_MIN_TOTAL_AMOUNT");
	}

	private static class ThresholdProbeModel extends AbstractSurveillanceModel {

		private final Connection connection;

		ThresholdProbeModel(Connection connection) {
			super(new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""), alertDispatcher());
			this.connection = connection;
		}

		BigDecimal loadThreshold(ModelConfig modelConfig, String thresholdName, LocalDate businessDate) {
			return getAlertThreshold(modelConfig, thresholdName, businessDate);
		}

		int loadLookupDays(ModelConfig modelConfig, String thresholdName, LocalDate businessDate) {
			return getThresholdLookupDays(modelConfig, thresholdName, businessDate);
		}

		@Override
		public String modelCode() {
			return "TEST_MODEL";
		}

		@Override
		public List<Trade> getTrades(ModelConfig modelConfig, String region, LocalDate businessDate) {
			return List.of();
		}

		@Override
		public List<Alert> evaluate(ModelConfig modelConfig, List<Trade> trades, LocalDate businessDate) {
			return List.of();
		}

		@Override
		protected Connection getConnection() {
			return connection;
		}
	}
}
