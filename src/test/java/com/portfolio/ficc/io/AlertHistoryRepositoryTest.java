package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertHistoryRepositoryTest {

    private static final ModelConfig MODEL_CONFIG = new ModelConfig(
            1,
            1,
            "NAMR",
            "NAMR FICC Surveillance App",
            "FICC_WASH_TRADE",
            "FICC Wash Trade Surveillance Model",
            "com.portfolio.ficc.surveillance.FiccWashTradeModel"
    );

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement historyStatement;

    @Mock
    private PreparedStatement detailStatement;

    @Mock
    private ResultSet generatedKeys;

    @Test
    void saveIfNewPersistsAlertHistoryAndDrillOutRowsAndReturnsTrue() throws Exception {
        LocalDate businessDate = LocalDate.of(2026, 6, 8);
        Alert alert = cumulativeAlert();
        String alertPayload = "{\"alertId\":\"ficc_wash_alert_1\"}";

        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(
                contains("INSERT INTO ficc_wash_alert_history ("),
                eq(Statement.RETURN_GENERATED_KEYS)
        )).thenReturn(historyStatement);
        when(historyStatement.executeUpdate()).thenReturn(1);
        when(historyStatement.getGeneratedKeys()).thenReturn(generatedKeys);
        when(generatedKeys.next()).thenReturn(true);
        when(generatedKeys.getLong(1)).thenReturn(42L);
        when(connection.prepareStatement(contains("INSERT INTO ficc_wash_alert_history_trade")))
                .thenReturn(detailStatement);
        when(detailStatement.executeBatch()).thenReturn(new int[]{1, 1});

        AlertHistoryRepository repository = new ConnectionBackedAlertHistoryRepository(connection);

        boolean saved = repository.saveIfNew(MODEL_CONFIG, businessDate, alert, alertPayload);

        assertTrue(saved);

        ArgumentCaptor<String> fingerprintCaptor = ArgumentCaptor.forClass(String.class);
        verify(historyStatement).setString(eq(1), fingerprintCaptor.capture());
        assertEquals(64, fingerprintCaptor.getValue().length());
        verify(historyStatement).setString(2, "ficc_wash_alert_1");
        verify(historyStatement).setInt(3, 1);
        verify(historyStatement).setInt(4, 1);
        verify(historyStatement).setString(5, "NAMR");
        verify(historyStatement).setString(6, "FICC_WASH_TRADE");
        verify(historyStatement).setString(7, "CUMULATIVE_TRANSACTION");
        verify(historyStatement).setDate(8, Date.valueOf(businessDate));
        verify(historyStatement).setDate(9, Date.valueOf(LocalDate.of(2026, 6, 7)));
        verify(historyStatement).setDate(10, Date.valueOf(LocalDate.of(2026, 6, 8)));
        verify(historyStatement).setString(11, "T-CUM-BUY-1,T-CUM-SELL-1");
        verify(historyStatement).setString(12, alertPayload);
        verify(historyStatement).setString(13, "DISPATCHED");
        verify(historyStatement).executeUpdate();

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
        verify(detailStatement, times(2)).addBatch();
        verify(detailStatement).executeBatch();
        verify(connection).commit();
        verify(connection).setAutoCommit(false);
        verify(connection).setAutoCommit(true);

        assertEquals(List.of(1, 2), sequenceCaptor.getAllValues());
        assertEquals(List.of("T-CUM-BUY-1", "T-CUM-SELL-1"), tradeIdCaptor.getAllValues());
        assertEquals(List.of(Date.valueOf("2026-06-07"), Date.valueOf("2026-06-08")),
                tradeDateCaptor.getAllValues());
        assertEquals(List.of(
                        Timestamp.valueOf("2026-06-07 09:42:00"),
                        Timestamp.valueOf("2026-06-08 09:42:20")
                ),
                timestampCaptor.getAllValues());
        assertEquals(List.of("BUY_LEG", "SELL_LEG"), roleCaptor.getAllValues());
    }

    @Test
    void saveIfNewReturnsFalseWhenDuplicateHistoryExists() throws Exception {
        Alert alert = cumulativeAlert();
        String alertPayload = "{\"alertId\":\"ficc_wash_alert_1\"}";

        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(
                contains("INSERT INTO ficc_wash_alert_history ("),
                eq(Statement.RETURN_GENERATED_KEYS)
        )).thenReturn(historyStatement);
        when(historyStatement.executeUpdate()).thenThrow(new SQLIntegrityConstraintViolationException("duplicate"));

        AlertHistoryRepository repository = new ConnectionBackedAlertHistoryRepository(connection);

        boolean saved = repository.saveIfNew(MODEL_CONFIG, LocalDate.of(2026, 6, 9), alert, alertPayload);

        assertFalse(saved);
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(connection, never()).prepareStatement(contains("INSERT INTO ficc_wash_alert_history_trade"));
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
