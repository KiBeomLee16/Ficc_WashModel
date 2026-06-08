package com.portfolio.ficc.surveillance;

import com.portfolio.ficc.io.DatabaseConfig;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.portfolio.ficc.TestConfigs.alertDispatcher;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiccWashTradeModelGetTradesTest {

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
    private CallableStatement tradeStatement;

    @Mock
    private ResultSet tradeResultSet;

    @Test
    void getTradesCallsTradeStoredProcedureAndMapsReturnedRows() throws Exception {
        LocalDate businessDate = LocalDate.of(2026, 6, 8);
        when(connection.prepareCall("{CALL sp_get_ficc_trades(?, ?, ?, ?)}")).thenReturn(tradeStatement);
        when(tradeStatement.executeQuery()).thenReturn(tradeResultSet);
        when(tradeResultSet.next()).thenReturn(true, false);
        stubTradeRow();

        FiccWashTradeModel model = new ConnectionBackedFiccWashTradeModel(connection);

        List<Trade> trades = model.getTrades(MODEL_CONFIG, "namr", businessDate);

        assertEquals(1, trades.size());

        Trade trade = trades.get(0);
        assertEquals("T-NAMR-UST-001", trade.tradeId());
        assertEquals(LocalDateTime.of(2026, 6, 8, 9, 30), trade.timestamp());
        assertEquals("Fixed Income", trade.assetClass());
        assertEquals("UST-10Y", trade.instrumentId());
        assertEquals(LocalDate.of(2036, 5, 15), trade.maturity());
        assertEquals("USD", trade.currency());
        assertEquals(Side.BUY, trade.side());
        assertEquals(new BigDecimal("10000000"), trade.quantity());
        assertEquals(new BigDecimal("99.8125"), trade.price());
        assertEquals("CP-ALPHA", trade.counterpartyId());
        assertEquals("ACCT-RATES-ALPHA", trade.accountId());

        verify(tradeStatement).setInt(1, 1);
        verify(tradeStatement).setInt(2, 1);
        verify(tradeStatement).setString(3, "NAMR");
        verify(tradeStatement).setDate(4, Date.valueOf(businessDate));
    }

    @Test
    void getTradesWrapsSqlExceptionWithStoredProcedureContext() throws Exception {
        when(connection.prepareCall("{CALL sp_get_ficc_trades(?, ?, ?, ?)}")).thenThrow(new SQLException("database down"));

        FiccWashTradeModel model = new ConnectionBackedFiccWashTradeModel(connection);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> model.getTrades(MODEL_CONFIG, "apac", LocalDate.of(2026, 6, 8))
        );

        assertEquals("Failed to load trades from stored procedure sp_get_ficc_trades for region=APAC, "
                        + "businessDate=2026-06-08",
                exception.getMessage());
    }

    private void stubTradeRow() throws SQLException {
        when(tradeResultSet.getString("trade_id")).thenReturn("T-NAMR-UST-001");
        when(tradeResultSet.getTimestamp("trade_timestamp"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 8, 9, 30)));
        when(tradeResultSet.getString("asset_class")).thenReturn("Fixed Income");
        when(tradeResultSet.getString("instrument_id")).thenReturn("UST-10Y");
        when(tradeResultSet.getDate("maturity")).thenReturn(Date.valueOf(LocalDate.of(2036, 5, 15)));
        when(tradeResultSet.getString("currency")).thenReturn("USD");
        when(tradeResultSet.getString("side")).thenReturn("BUY");
        when(tradeResultSet.getBigDecimal("quantity")).thenReturn(new BigDecimal("10000000"));
        when(tradeResultSet.getBigDecimal("price")).thenReturn(new BigDecimal("99.8125"));
        when(tradeResultSet.getString("counterparty_id")).thenReturn("CP-ALPHA");
        when(tradeResultSet.getString("account_id")).thenReturn("ACCT-RATES-ALPHA");
        when(tradeResultSet.getString("beneficial_owner")).thenReturn("Alpha Capital Master Fund");
        when(tradeResultSet.getString("trader_id")).thenReturn("TRDR-17");
        when(tradeResultSet.getString("desk")).thenReturn("Rates");
        when(tradeResultSet.getString("book")).thenReturn("GOVT-RATES-A");
        when(tradeResultSet.getString("broker")).thenReturn("BRKR-NY-1");
    }

    private static class ConnectionBackedFiccWashTradeModel extends FiccWashTradeModel {

        private final Connection connection;

        ConnectionBackedFiccWashTradeModel(Connection connection) {
            super(new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""),
                    alertDispatcher());
            this.connection = connection;
        }

        @Override
        protected Connection getConnection() {
            return connection;
        }
    }
}
