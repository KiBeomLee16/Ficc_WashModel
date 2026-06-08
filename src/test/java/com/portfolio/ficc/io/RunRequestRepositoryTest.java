package com.portfolio.ficc.io;

import com.portfolio.ficc.model.RunRequest;
import com.portfolio.ficc.model.RunSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.Date;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunRequestRepositoryTest {

    @Mock
    private Connection connection;

    @Mock
    private CallableStatement claimStatement;

    @Mock
    private CallableStatement updateStatement;

    @Mock
    private ResultSet resultSet;

    @Test
    void claimNextRunnableRequestCallsClaimProcedureAndMapsResult() throws Exception {
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareCall("{CALL sp_claim_next_surveillance_run_request()}")).thenReturn(claimStatement);
        when(claimStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        stubRunRequestRow();

        RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

        Optional<RunRequest> request = repository.claimNextRunnableRequest();

        assertTrue(request.isPresent());
        assertEquals(99L, request.get().requestId());
        assertEquals(1, request.get().appId());
        assertEquals("NAMR", request.get().region());
        assertEquals(LocalDate.of(2026, 6, 8), request.get().businessDate());

        verify(connection).setAutoCommit(false);
        verify(claimStatement).executeQuery();
        verify(connection).commit();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void markCompletedPersistsRunCounts() throws Exception {
        when(connection.prepareCall("{CALL sp_mark_surveillance_run_request_completed(?, ?)}"))
                .thenReturn(updateStatement);

        RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

        repository.markCompleted(runRequest(), runSummary());

        verify(updateStatement).setLong(1, 99L);
        verify(updateStatement).setInt(2, 2);
        verify(updateStatement).executeUpdate();
    }

    @Test
    void markFailedStoresTruncatedErrorMessage() throws Exception {
        when(connection.prepareCall("{CALL sp_mark_surveillance_run_request_failed(?, ?)}"))
                .thenReturn(updateStatement);

        RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

        repository.markFailed(runRequest(), new IllegalStateException("database down"));

        verify(updateStatement).setLong(1, 99L);
        verify(updateStatement).setString(2, "IllegalStateException: database down");
        verify(updateStatement).executeUpdate();
    }

    private void stubRunRequestRow() throws SQLException {
        when(resultSet.getLong("request_id")).thenReturn(99L);
        when(resultSet.getInt("appid")).thenReturn(1);
        when(resultSet.getString("region")).thenReturn("NAMR");
        when(resultSet.getDate("business_date")).thenReturn(Date.valueOf("2026-06-08"));
        when(resultSet.getString("status")).thenReturn("RUNNING");
    }

    private static RunRequest runRequest() {
        return new RunRequest(
                99,
                1,
                "NAMR",
                LocalDate.of(2026, 6, 8),
                "RUNNING"
        );
    }

    private static RunSummary runSummary() {
        return new RunSummary(
                1,
                1,
                "FICC_WASH_TRADE",
                "NAMR",
                LocalDate.of(2026, 6, 8),
                40,
                2,
                2,
                0
        );
    }

    private static class ConnectionBackedRunRequestRepository extends RunRequestRepository {

        private final Connection connection;

        ConnectionBackedRunRequestRepository(Connection connection) {
            super(new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""));
            this.connection = connection;
        }

        @Override
        protected Connection getConnection() {
            return connection;
        }
    }
}
