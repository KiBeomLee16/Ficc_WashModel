package com.portfolio.ficc.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalibrationThresholdRepositoryTest {

    @Mock
    private Connection connection;

    @Mock
    private CallableStatement statement;

    @Mock
    private ResultSet resultSet;

    @Test
    void updateCalibrationThresholdsUpdatesAllCalibrationThresholdRowsInTransaction() throws Exception {
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareCall("{CALL sp_update_surveillance_model_threshold(?, ?, ?, ?, ?, ?)}"))
                .thenReturn(statement);
        when(statement.execute()).thenReturn(true);
        when(statement.getResultSet()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, true);
        when(resultSet.getInt("threshold_count")).thenReturn(1);

        CalibrationThresholdRepository repository = new ConnectionBackedCalibrationThresholdRepository(connection);

        repository.updateCalibrationThresholds(
                4,
                "namrc",
                new BigDecimal("90000000"),
                new BigDecimal("4500000"),
                new BigDecimal("4.5"),
                new BigDecimal("4.25"),
                3
        );

        ArgumentCaptor<String> thresholdNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BigDecimal> thresholdValueCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<Integer> lookupDaysCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(statement, times(4)).setInt(1, 4);
        verify(statement, times(4)).setInt(2, 1);
        verify(statement, times(4)).setString(3, "NAMRC");
        verify(statement, times(4)).setString(eq(4), thresholdNameCaptor.capture());
        verify(statement, times(4)).setBigDecimal(eq(5), thresholdValueCaptor.capture());
        verify(statement, times(4)).setInt(eq(6), lookupDaysCaptor.capture());
        verify(statement, times(4)).execute();
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
        verify(connection).setAutoCommit(true);

        assertEquals(List.of(
                "ONE_TIME_MIN_TOTAL_AMOUNT",
                "CUMULATIVE_MIN_TOTAL_AMOUNT",
                "QUANTITY_TOLERANCE_PERCENT",
                "TOTAL_AMOUNT_TOLERANCE_PERCENT"
        ), thresholdNameCaptor.getAllValues());
        assertEquals(List.of(
                new BigDecimal("90000000"),
                new BigDecimal("4500000"),
                new BigDecimal("4.5"),
                new BigDecimal("4.25")
        ), thresholdValueCaptor.getAllValues());
        assertEquals(List.of(0, 3, 0, 0), lookupDaysCaptor.getAllValues());
    }

    @Test
    void updateCalibrationThresholdsRejectsNonCalibrationRegionAppIdPair() throws Exception {
        CalibrationThresholdRepository repository = new ConnectionBackedCalibrationThresholdRepository(connection);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> repository.updateCalibrationThresholds(
                        3,
                        "APACC",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        4
                )
        );

        assertEquals("Calibration region must match appid: NAMRC=4, EMEAC=5, APACC=6", exception.getMessage());
        verify(connection, never()).prepareCall("{CALL sp_update_surveillance_model_threshold(?, ?, ?, ?, ?, ?)}");
    }

    private static class ConnectionBackedCalibrationThresholdRepository extends CalibrationThresholdRepository {

        private final Connection connection;

        ConnectionBackedCalibrationThresholdRepository(Connection connection) {
            super(new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""));
            this.connection = connection;
        }

        @Override
        protected Connection getConnection() throws SQLException {
            return connection;
        }
    }
}
