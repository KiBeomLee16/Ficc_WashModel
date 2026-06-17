package com.portfolio.ficc.io;

import com.portfolio.ficc.model.RunRequest;
import com.portfolio.ficc.model.RunRequestStatus;
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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
	private CallableStatement insertStatement;

	@Mock
	private CallableStatement searchStatement;

	@Mock
	private ResultSet resultSet;

	@Mock
	private ResultSet generatedKeys;

	@Mock
	private ResultSet searchResultSet;

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
	void insertRunRequestStoresQueueRowAndReturnsGeneratedRequestId() throws Exception {
		when(connection.prepareCall("{CALL sp_insert_surveillance_run_request(?, ?, ?, ?)}"))
				.thenReturn(insertStatement);
		when(insertStatement.executeQuery()).thenReturn(generatedKeys);
		when(generatedKeys.next()).thenReturn(true);
		when(generatedKeys.getLong("request_id")).thenReturn(123L);

		RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

		long requestId = repository.insertRunRequest(1, "namr", LocalDate.of(2026, 6, 8), "local-demo");

		assertEquals(123L, requestId);
		verify(insertStatement).setInt(1, 1);
		verify(insertStatement).setString(2, "NAMR");
		verify(insertStatement).setDate(3, Date.valueOf("2026-06-08"));
		verify(insertStatement).setString(4, "local-demo");
		verify(insertStatement).executeQuery();
	}

	@Test
	void findLatestByRunCriteriaReturnsLatestRequestStatus() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 7);
		when(connection.prepareCall("{CALL sp_find_latest_surveillance_run_request(?, ?, ?)}"))
				.thenReturn(searchStatement);
		when(searchStatement.executeQuery()).thenReturn(searchResultSet);
		when(searchResultSet.next()).thenReturn(true);
		when(searchResultSet.getLong("request_id")).thenReturn(18L);
		when(searchResultSet.getInt("appid")).thenReturn(3);
		when(searchResultSet.getString("region")).thenReturn("APAC");
		when(searchResultSet.getDate("business_date")).thenReturn(Date.valueOf(businessDate));
		when(searchResultSet.getString("status")).thenReturn("COMPLETED");
		when(searchResultSet.getInt("alerts_generated")).thenReturn(0);
		when(searchResultSet.getTimestamp("requested_at")).thenReturn(Timestamp.valueOf("2026-06-07 09:00:00"));
		when(searchResultSet.getTimestamp("started_at")).thenReturn(Timestamp.valueOf("2026-06-07 09:00:05"));
		when(searchResultSet.getTimestamp("completed_at")).thenReturn(Timestamp.valueOf("2026-06-07 09:00:07"));

		RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

		Optional<RunRequestStatus> status = repository.findLatestByRunCriteria(3, "apac", businessDate);

		assertTrue(status.isPresent());
		assertEquals(18L, status.get().requestId());
		assertEquals(3, status.get().appId());
		assertEquals("APAC", status.get().region());
		assertEquals("COMPLETED", status.get().status());
		assertEquals(0, status.get().alertsGenerated());

		verify(searchStatement).setInt(1, 3);
		verify(searchStatement).setString(2, "APAC");
		verify(searchStatement).setDate(3, Date.valueOf(businessDate));
	}

	@Test
	void findByRunCriteriaReturnsAllMatchingRequestStatuses() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(connection.prepareCall("{CALL sp_find_surveillance_run_requests(?, ?, ?)}")).thenReturn(searchStatement);
		when(searchStatement.executeQuery()).thenReturn(searchResultSet);
		when(searchResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
		when(searchResultSet.getLong("request_id")).thenReturn(22L).thenReturn(21L);
		when(searchResultSet.getInt("appid")).thenReturn(3);
		when(searchResultSet.getString("region")).thenReturn("APAC");
		when(searchResultSet.getDate("business_date")).thenReturn(Date.valueOf(businessDate));
		when(searchResultSet.getString("status")).thenReturn("COMPLETED").thenReturn("PENDING");
		when(searchResultSet.getInt("alerts_generated")).thenReturn(2).thenReturn(0);
		when(searchResultSet.getTimestamp("requested_at")).thenReturn(Timestamp.valueOf("2026-06-08 09:10:00"))
				.thenReturn(Timestamp.valueOf("2026-06-08 09:00:00"));

		RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

		List<RunRequestStatus> statuses = repository.findByRunCriteria(3, "apac", businessDate);

		assertEquals(2, statuses.size());
		assertEquals(22L, statuses.get(0).requestId());
		assertEquals("COMPLETED", statuses.get(0).status());
		assertEquals(21L, statuses.get(1).requestId());
		assertEquals("PENDING", statuses.get(1).status());

		verify(searchStatement).setInt(1, 3);
		verify(searchStatement).setString(2, "APAC");
		verify(searchStatement).setDate(3, Date.valueOf(businessDate));
	}

	@Test
	void findByRequestIdReturnsRequestStatus() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(connection.prepareCall("{CALL sp_find_surveillance_run_request_by_id(?)}")).thenReturn(searchStatement);
		when(searchStatement.executeQuery()).thenReturn(searchResultSet);
		when(searchResultSet.next()).thenReturn(true);
		when(searchResultSet.getLong("request_id")).thenReturn(24L);
		when(searchResultSet.getInt("appid")).thenReturn(4);
		when(searchResultSet.getString("region")).thenReturn("NAMRC");
		when(searchResultSet.getDate("business_date")).thenReturn(Date.valueOf(businessDate));
		when(searchResultSet.getString("status")).thenReturn("COMPLETED");
		when(searchResultSet.getInt("alerts_generated")).thenReturn(2);
		when(searchResultSet.getTimestamp("requested_at")).thenReturn(Timestamp.valueOf("2026-06-08 09:00:00"));

		RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

		Optional<RunRequestStatus> status = repository.findByRequestId(24L);

		assertTrue(status.isPresent());
		assertEquals(24L, status.get().requestId());
		assertEquals(4, status.get().appId());
		assertEquals("NAMRC", status.get().region());
		verify(searchStatement).setLong(1, 24L);
	}

	@Test
	void findCalibrationRunRequestsReturnsCalibrationStatuses() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(connection.prepareCall("{CALL sp_find_calibration_run_requests()}")).thenReturn(searchStatement);
		when(searchStatement.executeQuery()).thenReturn(searchResultSet);
		when(searchResultSet.next()).thenReturn(true).thenReturn(false);
		when(searchResultSet.getLong("request_id")).thenReturn(24L);
		when(searchResultSet.getInt("appid")).thenReturn(4);
		when(searchResultSet.getString("region")).thenReturn("NAMRC");
		when(searchResultSet.getDate("business_date")).thenReturn(Date.valueOf(businessDate));
		when(searchResultSet.getString("status")).thenReturn("COMPLETED");
		when(searchResultSet.getInt("alerts_generated")).thenReturn(2);

		RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

		List<RunRequestStatus> statuses = repository.findCalibrationRunRequests();

		assertEquals(1, statuses.size());
		assertEquals(24L, statuses.get(0).requestId());
		assertEquals("NAMRC", statuses.get(0).region());
		verify(searchStatement).executeQuery();
	}

	@Test
	void markCompletedPersistsRunCounts() throws Exception {
		when(connection.prepareCall("{CALL sp_mark_surveillance_run_request_completed(?, ?)}"))
				.thenReturn(updateStatement);
		RunRequest request = new RunRequest(99, 1, "NAMR", LocalDate.of(2026, 6, 8), "RUNNING");
		RunSummary summary = mock(RunSummary.class);
		when(summary.alertsGenerated()).thenReturn(2);

		RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

		repository.markCompleted(request, summary);

		verify(updateStatement).setLong(1, request.requestId());
		verify(updateStatement).setInt(2, 2);
		verify(updateStatement).executeUpdate();
	}

	@Test
	void markFailedStoresTruncatedErrorMessage() throws Exception {
		when(connection.prepareCall("{CALL sp_mark_surveillance_run_request_failed(?, ?)}"))
				.thenReturn(updateStatement);
		RunRequest request = new RunRequest(99, 1, "NAMR", LocalDate.of(2026, 6, 8), "RUNNING");

		RunRequestRepository repository = new ConnectionBackedRunRequestRepository(connection);

		repository.markFailed(request, new IllegalStateException("database down"));

		verify(updateStatement).setLong(1, request.requestId());
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
