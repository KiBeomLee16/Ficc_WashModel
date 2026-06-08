package com.portfolio.ficc.io;

import com.portfolio.ficc.model.RunRequest;
import com.portfolio.ficc.model.RunSummary;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

@Component
public class RunRequestRepository {

    private static final String CLAIM_NEXT_REQUEST_SQL = """
            SELECT
                request_id,
                appid,
                region,
                business_date,
                status,
                attempt_count
            FROM surveillance_run_request
            WHERE status = 'PENDING'
            ORDER BY requested_at, request_id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """;

    private static final String CLAIM_REQUEST_BY_ID_SQL = """
            SELECT
                request_id,
                appid,
                region,
                business_date,
                status,
                attempt_count
            FROM surveillance_run_request
            WHERE request_id = ?
              AND status = 'PENDING'
            FOR UPDATE
            """;

    private static final String MARK_RUNNING_SQL = """
            UPDATE surveillance_run_request
            SET status = 'RUNNING',
                started_at = CURRENT_TIMESTAMP,
                completed_at = NULL,
                attempt_count = attempt_count + 1,
                locked_by = ?,
                locked_at = CURRENT_TIMESTAMP,
                error_message = NULL
            WHERE request_id = ?
            """;

    private static final String MARK_COMPLETED_SQL = """
            UPDATE surveillance_run_request
            SET status = 'COMPLETED',
                completed_at = CURRENT_TIMESTAMP,
                trades_processed = ?,
                alerts_generated = ?,
                alerts_dispatched = ?,
                duplicate_alerts = ?,
                error_message = NULL
            WHERE request_id = ?
            """;

    private static final String MARK_FAILED_SQL = """
            UPDATE surveillance_run_request
            SET status = 'FAILED',
                completed_at = CURRENT_TIMESTAMP,
                error_message = ?
            WHERE request_id = ?
            """;

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final DatabaseConfig databaseConfig;

    public RunRequestRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = Objects.requireNonNull(databaseConfig, "databaseConfig is required");
    }

    public Optional<RunRequest> claimNextPendingRequest(String workerId) {
        return claimRequest(workerId, CLAIM_NEXT_REQUEST_SQL, statement -> {
        });
    }

    public Optional<RunRequest> claimRequestById(long requestId, String workerId) {
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        return claimRequest(workerId, CLAIM_REQUEST_BY_ID_SQL, statement -> statement.setLong(1, requestId));
    }

    public void markCompleted(RunRequest request, RunSummary summary) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(summary, "summary is required");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(MARK_COMPLETED_SQL)) {

            statement.setInt(1, summary.tradesProcessed());
            statement.setInt(2, summary.alertsGenerated());
            statement.setInt(3, summary.alertsDispatched());
            statement.setInt(4, summary.duplicateAlerts());
            statement.setLong(5, request.requestId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to mark run request completed requestId="
                    + request.requestId(), exception);
        }
    }

    public void markFailed(RunRequest request, Exception failure) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(failure, "failure is required");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(MARK_FAILED_SQL)) {

            statement.setString(1, errorMessage(failure));
            statement.setLong(2, request.requestId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to mark run request failed requestId="
                    + request.requestId(), exception);
        }
    }

    protected Connection getConnection() throws SQLException {
        return databaseConfig.getConnection();
    }

    private Optional<RunRequest> claimRequest(
            String workerId,
            String selectSql,
            StatementBinder binder
    ) {
        Objects.requireNonNull(workerId, "workerId is required");
        if (workerId.isBlank()) {
            throw new IllegalArgumentException("workerId is required");
        }

        try (Connection connection = getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                Optional<RunRequest> request = selectRequestForUpdate(connection, selectSql, binder);
                if (request.isEmpty()) {
                    connection.commit();
                    return Optional.empty();
                }
                markRunning(connection, request.get(), workerId);
                connection.commit();
                return Optional.of(request.get());
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            } finally {
                restoreAutoCommitQuietly(connection, originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to claim surveillance run request", exception);
        }
    }

    private Optional<RunRequest> selectRequestForUpdate(
            Connection connection,
            String selectSql,
            StatementBinder binder
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(toRunRequest(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    private RunRequest toRunRequest(ResultSet resultSet) throws SQLException {
        Date businessDate = resultSet.getDate("business_date");
        return new RunRequest(
                resultSet.getLong("request_id"),
                resultSet.getInt("appid"),
                resultSet.getString("region"),
                businessDate.toLocalDate(),
                resultSet.getString("status"),
                resultSet.getInt("attempt_count")
        );
    }

    private void markRunning(Connection connection, RunRequest request, String workerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MARK_RUNNING_SQL)) {
            statement.setString(1, workerId.trim());
            statement.setLong(2, request.requestId());
            statement.executeUpdate();
        }
    }

    private String errorMessage(Exception failure) {
        String message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original database exception.
        }
    }

    private void restoreAutoCommitQuietly(Connection connection, boolean originalAutoCommit) {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException ignored) {
            // Connection will be closed by try-with-resources.
        }
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
