package com.portfolio.ficc.io;

import com.portfolio.ficc.model.RunRequest;
import com.portfolio.ficc.model.RunSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunRequestRepositoryTest {

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement selectStatement;

    @Mock
    private PreparedStatement updateStatement;

    @Mock
    private ResultSet resultSet;

    @Test
    void claimNextPendingRequestLocksOldestPendingRequestAndMarksRunning() throws Exception {
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(argThat(sql ->
                containsAll(sql, "SELECT", "surveillance_run_request", "FOR UPDATE SKIP LOCKED")
        ))).thenReturn(selectStatement);
        when(connection.prepareStatement(argThat(sql ->
                containsAll(sql, "SET status = 'RUNNING'")
        ))).thenReturn(updateStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        stubRunRequestRow();

        RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

        Optional<RunRequest> request = repository.claimNextPendingRequest("worker-1");

        assertTrue(request.isPresent());
        assertEquals(99L, request.get().requestId());
        assertEquals(1, request.get().appId());
        assertEquals("NAMR", request.get().region());
        assertEquals(LocalDate.of(2026, 6, 8), request.get().businessDate());

        verify(connection).setAutoCommit(false);
        verify(updateStatement).setString(1, "worker-1");
        verify(updateStatement).setLong(2, 99L);
        verify(updateStatement).executeUpdate();
        verify(connection).commit();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void claimRequestByIdUsesRequestIdPredicate() throws Exception {
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(argThat(sql ->
                containsAll(sql, "WHERE request_id = ?", "FOR UPDATE")
        ))).thenReturn(selectStatement);
        when(connection.prepareStatement(argThat(sql ->
                containsAll(sql, "SET status = 'RUNNING'")
        ))).thenReturn(updateStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        stubRunRequestRow();

        RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

        Optional<RunRequest> request = repository.claimRequestById(99L, "worker-1");

        assertTrue(request.isPresent());
        verify(selectStatement).setLong(1, 99L);
        verify(updateStatement).setLong(2, 99L);
    }

    @Test
    void markCompletedPersistsRunCounts() throws Exception {
        when(connection.prepareStatement(argThat(sql ->
                containsAll(sql, "SET status = 'COMPLETED'")
        ))).thenReturn(updateStatement);

        RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

        repository.markCompleted(runRequest(), runSummary());

        verify(updateStatement).setInt(1, 40);
        verify(updateStatement).setInt(2, 2);
        verify(updateStatement).setInt(3, 2);
        verify(updateStatement).setInt(4, 0);
        verify(updateStatement).setLong(5, 99L);
        verify(updateStatement).executeUpdate();
    }

    @Test
    void markFailedStoresTruncatedErrorMessage() throws Exception {
        when(connection.prepareStatement(argThat(sql ->
                containsAll(sql, "SET status = 'FAILED'")
        ))).thenReturn(updateStatement);

        RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

        repository.markFailed(runRequest(), new IllegalStateException("database down"));

        verify(updateStatement).setString(1, "IllegalStateException: database down");
        verify(updateStatement).setLong(2, 99L);
        verify(updateStatement).executeUpdate();
    }

    private void stubRunRequestRow() throws SQLException {
        when(resultSet.getLong("request_id")).thenReturn(99L);
        when(resultSet.getInt("appid")).thenReturn(1);
        when(resultSet.getString("region")).thenReturn("NAMR");
        when(resultSet.getDate("business_date")).thenReturn(Date.valueOf("2026-06-08"));
        when(resultSet.getString("status")).thenReturn("PENDING");
        when(resultSet.getInt("attempt_count")).thenReturn(0);
    }

    private static boolean containsAll(String value, String... expectedParts) {
        if (value == null) {
            return false;
        }
        for (String expectedPart : expectedParts) {
            if (!value.contains(expectedPart)) {
                return false;
            }
        }
        return true;
    }

    private static RunRequest runRequest() {
        return new RunRequest(
                99,
                1,
                "NAMR",
                LocalDate.of(2026, 6, 8),
                "RUNNING",
                1
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
