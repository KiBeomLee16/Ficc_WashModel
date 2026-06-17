package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.AlertHistoryResult;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertHistoryRepositoryTest {

	private static final ModelConfig MODEL_CONFIG = new ModelConfig(1, 1, "NAMR", "NAMR FICC Surveillance App",
			"FICC_WASH_TRADE", "FICC Wash Trade Surveillance Model",
			"com.portfolio.ficc.surveillance.FiccWashTradeModel");

	@Mock
	private Connection connection;

	@Mock
	private CallableStatement historyStatement;

	@Mock
	private CallableStatement detailStatement;

	@Mock
	private CallableStatement searchStatement;

	@Mock
	private CallableStatement deleteStatement;

	@Mock
	private ResultSet generatedKeys;

	@Mock
	private ResultSet searchResultSet;

	@Mock
	private ResultSet deleteResultSet;

	@Test
	void saveIfNewPersistsAlertHistoryAndDrillOutRowsAndReturnsTrue() throws Exception {
		long requestId = 18L;
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		Alert alert = cumulativeAlert();
		String alertPayload = "{\"alertId\":\"ficc_wash_alert_1\"}";

		when(connection.getAutoCommit()).thenReturn(true);
		when(connection.prepareCall(
				"{CALL sp_insert_ficc_wash_alert_history(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}"))
				.thenReturn(historyStatement);
		when(historyStatement.executeQuery()).thenReturn(generatedKeys);
		when(generatedKeys.next()).thenReturn(true);
		when(generatedKeys.getLong("alert_history_id")).thenReturn(42L);
		when(connection.prepareCall(
				"{CALL sp_insert_ficc_wash_alert_drill_out(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}"))
				.thenReturn(detailStatement);
		when(detailStatement.executeUpdate()).thenReturn(1);

		AlertHistoryRepository repository = new ConnectionBackedAlertHistoryRepository(connection);

		boolean saved = repository.saveIfNew(requestId, MODEL_CONFIG, businessDate, alert, alertPayload);

		assertTrue(saved);

		ArgumentCaptor<String> fingerprintCaptor = ArgumentCaptor.forClass(String.class);
		verify(historyStatement).setString(eq(1), fingerprintCaptor.capture());
		assertEquals(64, fingerprintCaptor.getValue().length());
		verify(historyStatement).setString(2, "ficc_wash_alert_1");
		verify(historyStatement).setLong(3, requestId);
		verify(historyStatement).setInt(4, 1);
		verify(historyStatement).setInt(5, 1);
		verify(historyStatement).setString(6, "NAMR");
		verify(historyStatement).setString(7, "FICC_WASH_TRADE");
		verify(historyStatement).setString(8, "CUMULATIVE_TRANSACTION");
		verify(historyStatement).setDate(9, Date.valueOf(businessDate));
		verify(historyStatement).setDate(10, Date.valueOf(LocalDate.of(2026, 6, 7)));
		verify(historyStatement).setDate(11, Date.valueOf(LocalDate.of(2026, 6, 8)));
		verify(historyStatement).setString(12, "T-CUM-BUY-1,T-CUM-SELL-1");
		ArgumentCaptor<String> businessKeyCaptor = ArgumentCaptor.forClass(String.class);
		verify(historyStatement).setString(eq(13), businessKeyCaptor.capture());
		assertEquals(64, businessKeyCaptor.getValue().length());
		verify(historyStatement).setDate(14, Date.valueOf(LocalDate.of(2026, 6, 8)));
		verify(historyStatement).setString(15, "Currencies");
		verify(historyStatement).setString(16, "EUR/USD-SPOT");
		verify(historyStatement).setDate(17, Date.valueOf(LocalDate.of(2026, 6, 10)));
		verify(historyStatement).setString(18, "USD");
		verify(historyStatement).setString(19, "TRDR-42");
		verify(historyStatement).setString(20, "CP-BETA");
		verify(historyStatement).setString(21, alertPayload);
		verify(historyStatement).setString(22, "DISPATCHED");
		verify(historyStatement).executeQuery();

		ArgumentCaptor<Integer> sequenceCaptor = ArgumentCaptor.forClass(Integer.class);
		ArgumentCaptor<String> tradeIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Date> tradeDateCaptor = ArgumentCaptor.forClass(Date.class);
		ArgumentCaptor<Timestamp> timestampCaptor = ArgumentCaptor.forClass(Timestamp.class);
		ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);
		verify(detailStatement, times(2)).setLong(1, 42L);
		verify(detailStatement, times(2)).setInt(eq(2), sequenceCaptor.capture());
		verify(detailStatement, times(2)).setString(eq(3), tradeIdCaptor.capture());
		verify(detailStatement, times(2)).setDate(eq(4), tradeDateCaptor.capture());
		verify(detailStatement, times(2)).setTimestamp(eq(5), timestampCaptor.capture());
		verify(detailStatement, times(2)).setString(eq(21), roleCaptor.capture());
		verify(detailStatement, times(2)).executeUpdate();
		verify(connection).commit();
		verify(connection).setAutoCommit(false);
		verify(connection).setAutoCommit(true);

		assertEquals(List.of(1, 2), sequenceCaptor.getAllValues());
		assertEquals(List.of("T-CUM-BUY-1", "T-CUM-SELL-1"), tradeIdCaptor.getAllValues());
		assertEquals(List.of(Date.valueOf("2026-06-07"), Date.valueOf("2026-06-08")), tradeDateCaptor.getAllValues());
		assertEquals(List.of(Timestamp.valueOf("2026-06-07 09:42:00"), Timestamp.valueOf("2026-06-08 09:42:20")),
				timestampCaptor.getAllValues());
		assertEquals(List.of("BUY_LEG", "SELL_LEG"), roleCaptor.getAllValues());
	}

	@Test
	void saveIfNewReturnsFalseWhenDuplicateHistoryExists() throws Exception {
		long requestId = 18L;
		Alert alert = cumulativeAlert();
		String alertPayload = "{\"alertId\":\"ficc_wash_alert_1\"}";

		when(connection.getAutoCommit()).thenReturn(true);
		when(connection.prepareCall(
				"{CALL sp_insert_ficc_wash_alert_history(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}"))
				.thenReturn(historyStatement);
		when(historyStatement.executeQuery()).thenThrow(new SQLIntegrityConstraintViolationException("duplicate"));

		AlertHistoryRepository repository = new ConnectionBackedAlertHistoryRepository(connection);

		boolean saved = repository.saveIfNew(requestId, MODEL_CONFIG, LocalDate.of(2026, 6, 9), alert, alertPayload);

		assertFalse(saved);
		verify(connection).rollback();
		verify(connection, never()).commit();
		verify(connection, never()).prepareCall(
				"{CALL sp_insert_ficc_wash_alert_drill_out(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}");
	}

	@Test
	void findByRunCriteriaReturnsAlertHistoryRowsForSelectedAppRegionAndDate() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(connection.prepareCall("{CALL sp_find_ficc_wash_alert_history(?, ?, ?)}")).thenReturn(searchStatement);
		when(searchStatement.executeQuery()).thenReturn(searchResultSet);
		when(searchResultSet.next()).thenReturn(true).thenReturn(false);
		when(searchResultSet.getLong("alert_history_id")).thenReturn(11L);
		when(searchResultSet.getLong("request_id")).thenReturn(18L);
		when(searchResultSet.getString("alert_id")).thenReturn("ficc_wash_alert_11");
		when(searchResultSet.getInt("appid")).thenReturn(3);
		when(searchResultSet.getInt("modelid")).thenReturn(1);
		when(searchResultSet.getString("region")).thenReturn("APAC");
		when(searchResultSet.getString("alert_type")).thenReturn("FICC_WASH_TRADE");
		when(searchResultSet.getString("match_type")).thenReturn("ONE_TIME_TRANSACTION");
		when(searchResultSet.getDate("business_date")).thenReturn(Date.valueOf(businessDate));
		when(searchResultSet.getDate("first_trade_date")).thenReturn(Date.valueOf(businessDate));
		when(searchResultSet.getDate("last_trade_date")).thenReturn(Date.valueOf(businessDate));
		when(searchResultSet.getString("related_trade_ids")).thenReturn("T-UST-001,T-UST-002");
		when(searchResultSet.getString("alert_business_key_hash")).thenReturn("business-key-hash");
		when(searchResultSet.getDate("trade_date")).thenReturn(Date.valueOf(businessDate));
		when(searchResultSet.getString("asset_class")).thenReturn("Fixed Income");
		when(searchResultSet.getString("instrument_id")).thenReturn("UST-10Y");
		when(searchResultSet.getDate("maturity_date")).thenReturn(Date.valueOf("2036-06-08"));
		when(searchResultSet.getString("currency")).thenReturn("USD");
		when(searchResultSet.getString("trader_id")).thenReturn("TRDR-APAC-1");
		when(searchResultSet.getString("counterparty_id")).thenReturn("CP-APAC-ALPHA");
		when(searchResultSet.getString("alert_payload")).thenReturn("{\"reasons\":[\"same counterparty\"]}");
		when(searchResultSet.getString("dispatch_status")).thenReturn("DISPATCHED");
		when(searchResultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf("2026-06-08 09:45:00"));

		AlertHistoryRepository repository = new ConnectionBackedAlertHistoryRepository(connection);

		List<AlertHistoryResult> results = repository.findByRunCriteria(3, "apac", businessDate);

		assertEquals(1, results.size());
		assertEquals(11L, results.get(0).alertHistoryId());
		assertEquals(18L, results.get(0).requestId());
		assertEquals("ficc_wash_alert_11", results.get(0).alertId());
		assertEquals(3, results.get(0).appId());
		assertEquals("APAC", results.get(0).region());
		assertEquals("ONE_TIME_TRANSACTION", results.get(0).matchType());
		assertEquals("T-UST-001,T-UST-002", results.get(0).relatedTradeIds());
		assertEquals("business-key-hash", results.get(0).alertBusinessKeyHash());
		assertEquals("UST-10Y", results.get(0).instrumentId());
		assertEquals("TRDR-APAC-1", results.get(0).traderId());

		verify(searchStatement).setInt(1, 3);
		verify(searchStatement).setString(2, "APAC");
		verify(searchStatement).setDate(3, Date.valueOf(businessDate));
	}

	@Test
	void deleteByRunCriteriaCallsRefreshProcedureAndReturnsDeletedAlertCount() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(connection.prepareCall("{CALL sp_delete_ficc_wash_alert_history_for_run(?, ?, ?, ?)}"))
				.thenReturn(deleteStatement);
		when(deleteStatement.executeQuery()).thenReturn(deleteResultSet);
		when(deleteResultSet.next()).thenReturn(true);
		when(deleteResultSet.getInt("deleted_alert_count")).thenReturn(2);
		when(deleteResultSet.getInt("deleted_trade_count")).thenReturn(6);

		AlertHistoryRepository repository = new ConnectionBackedAlertHistoryRepository(connection);

		int deletedAlerts = repository.deleteByRunCriteria(MODEL_CONFIG, businessDate);

		assertEquals(2, deletedAlerts);
		verify(deleteStatement).setInt(1, 1);
		verify(deleteStatement).setInt(2, 1);
		verify(deleteStatement).setString(3, "NAMR");
		verify(deleteStatement).setDate(4, Date.valueOf(businessDate));
	}

	private static Alert cumulativeAlert() {
		Trade buyTrade = trade("T-CUM-BUY-1", Side.BUY, LocalDateTime.of(2026, 6, 7, 9, 42));
		Trade sellTrade = trade("T-CUM-SELL-1", Side.SELL, LocalDateTime.of(2026, 6, 8, 9, 42, 20));
		return new Alert("ficc_wash_alert_1", "FICC_WASH_TRADE", "CUMULATIVE_TRANSACTION", buyTrade, sellTrade,
				List.of(sellTrade, buyTrade), new BigDecimal("3000000"), new BigDecimal("3000000"),
				new BigDecimal("3300000.00000"), new BigDecimal("3300000.00000"), new BigDecimal("5000000"),
				List.of("unit-test cumulative alert"), Instant.parse("2026-06-08T00:00:00Z"));
	}

	private static Trade trade(String tradeId, Side side, LocalDateTime timestamp) {
		return new Trade(tradeId, timestamp, "Currencies", "EUR/USD-SPOT", LocalDate.of(2026, 6, 10), "USD", side,
				new BigDecimal("3000000"), new BigDecimal("1.10000"), "CP-BETA", "ACCT-FX-BETA", "Beta Macro Fund",
				"TRDR-42", "FX", "FX-SPOT-A", "BRKR-NY-9");
	}

	private static class ConnectionBackedAlertHistoryRepository extends AlertHistoryRepository {

		private final Connection connection;

		ConnectionBackedAlertHistoryRepository(Connection connection) {
			super(new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""));
			this.connection = connection;
		}

		@Override
		protected Connection getConnection() throws SQLException {
			return connection;
		}
	}
}
