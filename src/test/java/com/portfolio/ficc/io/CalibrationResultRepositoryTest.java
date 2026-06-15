package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.CalibrationAlertHistoryResult;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalibrationResultRepositoryTest {

    private static final ModelConfig MODEL_CONFIG = new ModelConfig(
            4,
            1,
            "NAMRC",
            "NAMRC FICC Surveillance App",
            "FICC_WASH_TRADE",
            "FICC Wash Trade Surveillance Model",
            "com.portfolio.ficc.surveillance.FiccWashTradeModel"
    );

    @Mock
    private Connection connection;

    @Mock
    private CallableStatement thresholdStatement;

    @Mock
    private CallableStatement historyStatement;

    @Mock
    private CallableStatement detailStatement;

    @Mock
    private CallableStatement searchStatement;

    @Mock
    private CallableStatement deleteStatement;

    @Mock
    private ResultSet thresholdResultSet;

    @Mock
    private ResultSet generatedKeys;

    @Mock
    private ResultSet searchResultSet;

    @Mock
    private ResultSet deleteResultSet;

    @Test
    void saveIfNewPersistsCalibrationResultWithThresholdSnapshotAndDrillOutRows() throws Exception {
        long requestId = 24L;
        LocalDate businessDate = LocalDate.of(2026, 6, 8);
        Alert alert = cumulativeAlert();
        String alertPayload = "{\"alertId\":\"ficc_wash_alert_1\"}";

        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareCall("{CALL sp_get_surveillance_model_threshold_snapshot(?, ?, ?)}"))
                .thenReturn(thresholdStatement);
        when(thresholdStatement.executeQuery()).thenReturn(thresholdResultSet);
        when(thresholdResultSet.next()).thenReturn(true);
        when(thresholdResultSet.getBigDecimal("one_time_min_total_amount"))
                .thenReturn(new BigDecimal("100000000.000000"));
        when(thresholdResultSet.getBigDecimal("cumulative_min_total_amount"))
                .thenReturn(new BigDecimal("5000000.000000"));
        when(thresholdResultSet.getBigDecimal("quantity_tolerance_percent"))
                .thenReturn(new BigDecimal("5.000000"));
        when(thresholdResultSet.getBigDecimal("total_amount_tolerance_percent"))
                .thenReturn(new BigDecimal("5.000000"));
        when(thresholdResultSet.getInt("cumulative_lookup_days")).thenReturn(4);
        when(connection.prepareCall("{CALL sp_insert_ficc_wash_calibration_alert_history(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}"))
                .thenReturn(historyStatement);
        when(historyStatement.executeQuery()).thenReturn(generatedKeys);
        when(generatedKeys.next()).thenReturn(true);
        when(generatedKeys.getLong("calibration_alert_history_id")).thenReturn(99L);
        when(connection.prepareCall("{CALL sp_insert_ficc_wash_calibration_alert_history_trade(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}"))
                .thenReturn(detailStatement);
        when(detailStatement.executeUpdate()).thenReturn(1);

        CalibrationResultRepository repository = new ConnectionBackedCalibrationResultRepository(connection);

        boolean saved = repository.saveIfNew(requestId, MODEL_CONFIG, businessDate, alert, alertPayload);

        assertTrue(saved);
        verify(thresholdStatement).setInt(1, 4);
        verify(thresholdStatement).setInt(2, 1);
        verify(thresholdStatement).setString(3, "NAMRC");

        ArgumentCaptor<String> fingerprintCaptor = ArgumentCaptor.forClass(String.class);
        verify(historyStatement).setString(eq(1), fingerprintCaptor.capture());
        assertEquals(64, fingerprintCaptor.getValue().length());
        verify(historyStatement).setString(2, "ficc_wash_alert_1");
        verify(historyStatement).setLong(3, requestId);
        verify(historyStatement).setInt(4, 4);
        verify(historyStatement).setInt(5, 1);
        verify(historyStatement).setString(6, "NAMRC");
        verify(historyStatement).setString(7, "FICC_WASH_TRADE");
        verify(historyStatement).setString(8, "CUMULATIVE_TRANSACTION");
        verify(historyStatement).setDate(9, Date.valueOf(businessDate));
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
        verify(historyStatement).setBigDecimal(22, new BigDecimal("100000000.000000"));
        verify(historyStatement).setBigDecimal(23, new BigDecimal("5000000.000000"));
        verify(historyStatement).setBigDecimal(24, new BigDecimal("5.000000"));
        verify(historyStatement).setBigDecimal(25, new BigDecimal("5.000000"));
        verify(historyStatement).setInt(26, 4);
        verify(historyStatement).setString(27, "DISPATCHED");

        ArgumentCaptor<String> tradeIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(detailStatement, times(2)).setLong(1, 99L);
        verify(detailStatement, times(2)).setString(eq(3), tradeIdCaptor.capture());
        verify(detailStatement, times(2)).executeUpdate();
        verify(connection).commit();
        assertEquals(List.of("T-CUM-BUY-1", "T-CUM-SELL-1"), tradeIdCaptor.getAllValues());
    }

    @Test
    void findByRequestIdReturnsCalibrationAlertHistoryRows() throws Exception {
        when(connection.prepareCall("{CALL sp_find_ficc_wash_calibration_alert_history_by_request(?)}"))
                .thenReturn(searchStatement);
        when(searchStatement.executeQuery()).thenReturn(searchResultSet);
        when(searchResultSet.next()).thenReturn(true).thenReturn(false);
        when(searchResultSet.getLong("calibration_alert_history_id")).thenReturn(99L);
        when(searchResultSet.getString("alert_id")).thenReturn("ficc_wash_alert_1");
        when(searchResultSet.getLong("request_id")).thenReturn(24L);
        when(searchResultSet.getInt("appid")).thenReturn(4);
        when(searchResultSet.getInt("modelid")).thenReturn(1);
        when(searchResultSet.getString("region")).thenReturn("NAMRC");
        when(searchResultSet.getString("alert_type")).thenReturn("FICC_WASH_TRADE");
        when(searchResultSet.getString("match_type")).thenReturn("ONE_TIME_TRANSACTION");
        when(searchResultSet.getDate("business_date")).thenReturn(Date.valueOf("2026-06-08"));
        when(searchResultSet.getDate("first_trade_date")).thenReturn(Date.valueOf("2026-06-08"));
        when(searchResultSet.getDate("last_trade_date")).thenReturn(Date.valueOf("2026-06-08"));
        when(searchResultSet.getString("related_trade_ids")).thenReturn("T-1,T-2");
        when(searchResultSet.getString("alert_business_key_hash")).thenReturn("business-key-hash");
        when(searchResultSet.getDate("trade_date")).thenReturn(Date.valueOf("2026-06-08"));
        when(searchResultSet.getString("asset_class")).thenReturn("Fixed Income");
        when(searchResultSet.getString("instrument_id")).thenReturn("UST-10Y");
        when(searchResultSet.getDate("maturity_date")).thenReturn(Date.valueOf("2036-06-08"));
        when(searchResultSet.getString("currency")).thenReturn("USD");
        when(searchResultSet.getString("trader_id")).thenReturn("TRDR-NAMR-1");
        when(searchResultSet.getString("counterparty_id")).thenReturn("CP-NAMR-ALPHA");
        when(searchResultSet.getString("alert_payload")).thenReturn("{}");
        when(searchResultSet.getBigDecimal("one_time_min_total_amount"))
                .thenReturn(new BigDecimal("100000000.000000"));
        when(searchResultSet.getBigDecimal("cumulative_min_total_amount"))
                .thenReturn(new BigDecimal("5000000.000000"));
        when(searchResultSet.getBigDecimal("quantity_tolerance_percent"))
                .thenReturn(new BigDecimal("5.000000"));
        when(searchResultSet.getBigDecimal("total_amount_tolerance_percent"))
                .thenReturn(new BigDecimal("5.000000"));
        when(searchResultSet.getInt("cumulative_lookup_days")).thenReturn(4);
        when(searchResultSet.getString("dispatch_status")).thenReturn("DISPATCHED");
        when(searchResultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf("2026-06-08 09:45:00"));

        CalibrationResultRepository repository = new ConnectionBackedCalibrationResultRepository(connection);

        List<CalibrationAlertHistoryResult> results = repository.findByRequestId(24L);

        assertEquals(1, results.size());
        assertEquals(99L, results.get(0).calibrationAlertHistoryId());
        assertEquals("NAMRC", results.get(0).region());
        assertEquals("business-key-hash", results.get(0).alertBusinessKeyHash());
        assertEquals("UST-10Y", results.get(0).instrumentId());
        assertEquals(new BigDecimal("100000000.000000"), results.get(0).oneTimeMinTotalAmount());
        assertEquals(4, results.get(0).cumulativeLookupDays());
        verify(searchStatement).setLong(1, 24L);
    }

    @Test
    void deleteByRequestIdCallsRefreshProcedureAndReturnsDeletedAlertCount() throws Exception {
        when(connection.prepareCall("{CALL sp_delete_ficc_wash_calibration_alert_history_for_request(?)}"))
                .thenReturn(deleteStatement);
        when(deleteStatement.executeQuery()).thenReturn(deleteResultSet);
        when(deleteResultSet.next()).thenReturn(true);
        when(deleteResultSet.getInt("deleted_alert_count")).thenReturn(2);
        when(deleteResultSet.getInt("deleted_trade_count")).thenReturn(4);

        CalibrationResultRepository repository = new ConnectionBackedCalibrationResultRepository(connection);

        int deletedAlerts = repository.deleteByRequestId(24L);

        assertEquals(2, deletedAlerts);
        verify(deleteStatement).setLong(1, 24L);
    }

    private static Alert cumulativeAlert() {
        Trade buyTrade = trade("T-CUM-BUY-1", Side.BUY, LocalDateTime.of(2026, 6, 7, 9, 42));
        Trade sellTrade = trade("T-CUM-SELL-1", Side.SELL, LocalDateTime.of(2026, 6, 8, 9, 42, 20));
        return new Alert(
                "ficc_wash_alert_1",
                "FICC_WASH_TRADE",
                "CUMULATIVE_TRANSACTION",
                buyTrade,
                sellTrade,
                List.of(sellTrade, buyTrade),
                new BigDecimal("3000000"),
                new BigDecimal("3000000"),
                new BigDecimal("3300000.00000"),
                new BigDecimal("3300000.00000"),
                new BigDecimal("5000000"),
                List.of("unit-test cumulative alert"),
                Instant.parse("2026-06-08T00:00:00Z")
        );
    }

    private static Trade trade(String tradeId, Side side, LocalDateTime timestamp) {
        return new Trade(
                tradeId,
                timestamp,
                "Currencies",
                "EUR/USD-SPOT",
                LocalDate.of(2026, 6, 10),
                "USD",
                side,
                new BigDecimal("3000000"),
                new BigDecimal("1.10000"),
                "CP-BETA",
                "ACCT-FX-BETA",
                "Beta Macro Fund",
                "TRDR-42",
                "FX",
                "FX-SPOT-A",
                "BRKR-NY-9"
        );
    }

    private static class ConnectionBackedCalibrationResultRepository extends CalibrationResultRepository {

        private final Connection connection;

        ConnectionBackedCalibrationResultRepository(Connection connection) {
            super(new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""));
            this.connection = connection;
        }

        @Override
        protected Connection getConnection() throws SQLException {
            return connection;
        }
    }
}
