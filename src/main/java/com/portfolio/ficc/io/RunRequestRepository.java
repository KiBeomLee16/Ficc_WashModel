package com.portfolio.ficc.io;

import com.portfolio.ficc.model.RunRequest;
import com.portfolio.ficc.model.RunSummary;
import com.portfolio.ficc.model.RunRequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class RunRequestRepository {

	private static final Logger LOGGER = LoggerFactory.getLogger(RunRequestRepository.class);

	private static final String CLAIM_NEXT_REQUEST_CALL = "{CALL sp_claim_next_surveillance_run_request()}";
	private static final String INSERT_RUN_REQUEST_CALL = "{CALL sp_insert_surveillance_run_request(?, ?, ?, ?)}";
	private static final String FIND_LATEST_RUN_REQUEST_CALL = "{CALL sp_find_latest_surveillance_run_request(?, ?, ?)}";
	private static final String FIND_RUN_REQUESTS_CALL = "{CALL sp_find_surveillance_run_requests(?, ?, ?)}";
	private static final String FIND_RUN_REQUEST_BY_ID_CALL = "{CALL sp_find_surveillance_run_request_by_id(?)}";
	private static final String FIND_CALIBRATION_RUN_REQUESTS_CALL = "{CALL sp_find_calibration_run_requests()}";
	private static final String MARK_COMPLETED_CALL = "{CALL sp_mark_surveillance_run_request_completed(?, ?)}";
	private static final String MARK_FAILED_CALL = "{CALL sp_mark_surveillance_run_request_failed(?, ?)}";

	private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

	private final DatabaseConfig databaseConfig;

	public RunRequestRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = Objects.requireNonNull(databaseConfig, "databaseConfig is required");
	}

	public Optional<RunRequest> claimNextRunnableRequest() {
		Optional<RunRequest> request = claimRequest(CLAIM_NEXT_REQUEST_CALL, statement -> {
		});

		return request;
	}

	public long insertRunRequest(int appId, String region, LocalDate businessDate, String requestedBy) {
		Objects.requireNonNull(region, "region is required");
		Objects.requireNonNull(businessDate, "businessDate is required");
		Objects.requireNonNull(requestedBy, "requestedBy is required");

		try (Connection connection = getConnection();
				CallableStatement statement = connection.prepareCall(INSERT_RUN_REQUEST_CALL)) {
			statement.setInt(1, appId);
			statement.setString(2, region.trim().toUpperCase());
			statement.setDate(3, Date.valueOf(businessDate));
			statement.setString(4, requestedBy.trim().isEmpty() ? "FRONTEND_USER" : requestedBy.trim());

			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					return resultSet.getLong("request_id");
				}
			}
			throw new SQLException("Run request insert did not return generated request_id");
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to insert run request for appid=" + appId + ", region=" + region
					+ ", businessDate=" + businessDate, exception);
		}
	}

	public Optional<RunRequestStatus> findLatestByRunCriteria(int appId, String region, LocalDate businessDate) {
		Objects.requireNonNull(region, "region is required");
		Objects.requireNonNull(businessDate, "businessDate is required");

		try (Connection connection = getConnection();
				CallableStatement statement = connection.prepareCall(FIND_LATEST_RUN_REQUEST_CALL)) {
			statement.setInt(1, appId);
			statement.setString(2, region.trim().toUpperCase());
			statement.setDate(3, Date.valueOf(businessDate));

			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					return Optional.of(toRunRequestStatus(resultSet));
				}
			}
			return Optional.empty();
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to search run request for appid=" + appId + ", region=" + region
					+ ", businessDate=" + businessDate, exception);
		}
	}

	public List<RunRequestStatus> findByRunCriteria(int appId, String region, LocalDate businessDate) {
		Objects.requireNonNull(region, "region is required");
		Objects.requireNonNull(businessDate, "businessDate is required");

		try (Connection connection = getConnection();
				CallableStatement statement = connection.prepareCall(FIND_RUN_REQUESTS_CALL)) {
			statement.setInt(1, appId);
			statement.setString(2, region.trim().toUpperCase());
			statement.setDate(3, Date.valueOf(businessDate));

			try (ResultSet resultSet = statement.executeQuery()) {
				List<RunRequestStatus> results = new ArrayList<>();
				while (resultSet.next()) {
					results.add(toRunRequestStatus(resultSet));
				}
				return results;
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to search run requests for appid=" + appId + ", region=" + region
					+ ", businessDate=" + businessDate, exception);
		}
	}

	public Optional<RunRequestStatus> findByRequestId(long requestId) {
		if (requestId <= 0) {
			throw new IllegalArgumentException("requestId must be positive");
		}

		try (Connection connection = getConnection();
				CallableStatement statement = connection.prepareCall(FIND_RUN_REQUEST_BY_ID_CALL)) {
			statement.setLong(1, requestId);

			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					return Optional.of(toRunRequestStatus(resultSet));
				}
			}
			return Optional.empty();
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to search run request for requestId=" + requestId, exception);
		}
	}

	public List<RunRequestStatus> findCalibrationRunRequests() {
		try (Connection connection = getConnection();
				CallableStatement statement = connection.prepareCall(FIND_CALIBRATION_RUN_REQUESTS_CALL)) {
			try (ResultSet resultSet = statement.executeQuery()) {
				List<RunRequestStatus> results = new ArrayList<>();
				while (resultSet.next()) {
					results.add(toRunRequestStatus(resultSet));
				}
				return results;
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to search calibration run requests", exception);
		}
	}

	public void markCompleted(RunRequest request, RunSummary summary) {
		Objects.requireNonNull(request, "request is required");
		Objects.requireNonNull(summary, "summary is required");

		try (Connection connection = getConnection();
				CallableStatement statement = connection.prepareCall(MARK_COMPLETED_CALL)) {

			statement.setLong(1, request.requestId());
			statement.setInt(2, summary.alertsGenerated());
			statement.executeUpdate();
//            LOGGER.info("Marked surveillance run request completed: requestId={}, alertsGenerated={}.",
//                    request.requestId(), summary.alertsGenerated());
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to mark run request completed requestId=" + request.requestId(),
					exception);
		}
	}

	public void markFailed(RunRequest request, Exception failure) {
		Objects.requireNonNull(request, "request is required");
		Objects.requireNonNull(failure, "failure is required");

		try (Connection connection = getConnection();
				CallableStatement statement = connection.prepareCall(MARK_FAILED_CALL)) {

			statement.setLong(1, request.requestId());
			statement.setString(2, errorMessage(failure));
			statement.executeUpdate();
			LOGGER.info("------------------------------------------------------------------------------------------");
			LOGGER.warn("Marked surveillance run request failed: requestId={}, error={}.", request.requestId(),
					errorMessage(failure));
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to mark run request failed requestId=" + request.requestId(),
					exception);
		}
	}

	protected Connection getConnection() throws SQLException {
		return databaseConfig.getConnection();
	}

	private Optional<RunRequest> claimRequest(String callSql, StatementBinder binder) {
		try (Connection connection = getConnection()) {
			boolean originalAutoCommit = connection.getAutoCommit();
			try {
				connection.setAutoCommit(false);
				LOGGER.debug("Calling run request claim procedure: {}.", callSql);
				Optional<RunRequest> request = callClaimProcedure(connection, callSql, binder);
				connection.commit();
				return request;
			} catch (SQLException exception) {
				rollbackQuietly(connection);
				LOGGER.error("Failed while claiming surveillance run request. Rolling back transaction.", exception);
				throw exception;
			} finally {
				restoreAutoCommitQuietly(connection, originalAutoCommit);
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to claim surveillance run request", exception);
		}
	}

	private Optional<RunRequest> callClaimProcedure(Connection connection, String callSql, StatementBinder binder)
			throws SQLException {
		try (CallableStatement statement = connection.prepareCall(callSql)) {
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
		return new RunRequest(resultSet.getLong("request_id"), resultSet.getInt("appid"), resultSet.getString("region"),
				businessDate.toLocalDate(), resultSet.getString("status"));
	}

	private RunRequestStatus toRunRequestStatus(ResultSet resultSet) throws SQLException {
		Timestamp requestedAt = resultSet.getTimestamp("requested_at");
		Timestamp startedAt = resultSet.getTimestamp("started_at");
		Timestamp completedAt = resultSet.getTimestamp("completed_at");
		return new RunRequestStatus(resultSet.getLong("request_id"), resultSet.getInt("appid"),
				resultSet.getString("region"), resultSet.getDate("business_date").toLocalDate(),
				resultSet.getString("status"), resultSet.getInt("alerts_generated"),
				requestedAt == null ? null : requestedAt.toLocalDateTime(),
				startedAt == null ? null : startedAt.toLocalDateTime(),
				completedAt == null ? null : completedAt.toLocalDateTime(), resultSet.getString("error_message"));
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
		void bind(CallableStatement statement) throws SQLException;
	}
}
