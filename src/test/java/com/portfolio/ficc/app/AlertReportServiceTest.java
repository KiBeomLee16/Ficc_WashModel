package com.portfolio.ficc.app;

import com.portfolio.ficc.io.AlertHistoryRepository;
import com.portfolio.ficc.model.AlertHistoryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertReportServiceTest {

	@Mock
	private AlertHistoryRepository alertHistoryRepository;

	@Mock
	private S3Client s3Client;

	@Test
	void uploadProductionReportDoesNothingWhenS3IsDisabled() {
		AlertReportService service = new AlertReportService(alertHistoryRepository, false, "ficc-alerts", s3Client);

		service.uploadProductionReport(18L, 1, "NAMR", LocalDate.of(2026, 6, 5));

		verifyNoInteractions(alertHistoryRepository, s3Client);
	}

	@Test
	void uploadProductionReportRejectsMissingBucketWhenEnabled() {
		AlertReportService service = new AlertReportService(alertHistoryRepository, true, "", s3Client);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> service.uploadProductionReport(18L, 1, "NAMR", LocalDate.of(2026, 6, 5)));

		assertEquals("FICC_S3_BUCKET_NAME is not configured.", exception.getMessage());
		verifyNoInteractions(alertHistoryRepository, s3Client);
	}

	@Test
	void uploadProductionReportUploadsCsvForCurrentRequestOnly() throws Exception {
		LocalDate businessDate = LocalDate.of(2026, 6, 5);
		AlertHistoryResult currentRequestAlert = alertHistory(18L, "ficc_wash_alert_18", "TRDR-\"ALPHA\"");
		AlertHistoryResult otherRequestAlert = alertHistory(19L, "ficc_wash_alert_19", "TRDR-BETA");
		when(alertHistoryRepository.findByRequestId(18L))
				.thenReturn(List.of(otherRequestAlert, currentRequestAlert));
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenReturn(PutObjectResponse.builder().build());
		AlertReportService service = new AlertReportService(alertHistoryRepository, true, "ficc-alerts", s3Client);

		service.uploadProductionReport(18L, 1, "NAMR", businessDate);

		ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
		ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
		verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

		PutObjectRequest request = requestCaptor.getValue();
		assertEquals("ficc-alerts", request.bucket());
		assertEquals("alerts/2026/06/05/request-18.csv", request.key());
		assertEquals("text/csv", request.contentType());

		String csv = new String(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes(),
				StandardCharsets.UTF_8);
		assertTrue(csv.startsWith("alert_history_id,alert_id,request_id,appid,modelid,region,"));
		assertTrue(csv.contains("\"ficc_wash_alert_18\""));
		assertTrue(csv.contains("\"18\""));
		assertTrue(csv.contains("\"TRDR-\"\"ALPHA\"\"\""));
		assertFalse(csv.contains("\"ficc_wash_alert_19\""));
	}

	@Test
	void uploadProductionReportSkipsS3WhenNoAlertsExistForRequest() {
		LocalDate businessDate = LocalDate.of(2026, 6, 5);
		when(alertHistoryRepository.findByRequestId(18L))
				.thenReturn(List.of(alertHistory(19L, "ficc_wash_alert_19", "TRDR-BETA")));
		AlertReportService service = new AlertReportService(alertHistoryRepository, true, "ficc-alerts", s3Client);

		service.uploadProductionReport(18L, 1, "NAMR", businessDate);

		verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
	}

	private static AlertHistoryResult alertHistory(long requestId, String alertId, String traderId) {
		LocalDate businessDate = LocalDate.of(2026, 6, 5);
		return new AlertHistoryResult(100L + requestId, alertId, requestId, 1, 1, "NAMR", "FICC_WASH_TRADE",
				"ONE_TIME_TRANSACTION", businessDate, businessDate, businessDate, "T-BUY,T-SELL",
				"alert-business-key-hash", businessDate, "Fixed Income", "UST-10Y", LocalDate.of(2036, 5, 15), "USD",
				traderId, "CP-ALPHA", "{\"alertId\":\"" + alertId + "\"}", "DISPATCHED",
				LocalDateTime.of(2026, 6, 5, 9, 30));
	}
}
