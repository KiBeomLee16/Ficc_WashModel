package com.portfolio.ficc.app;

import com.portfolio.ficc.io.RunRequestRepository;
import com.portfolio.ficc.model.RunRequest;
import com.portfolio.ficc.model.RunSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiccRunRequestWorkerTest {

	@Mock
	private FiccSurveillanceApplication surveillanceApplication;

	@Mock
	private RunRequestRepository runRequestRepository;

	@Test
	void runClaimsRunnableRequestsIntoQueueAndMarksCompleted() {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		RunRequest firstRequest = new RunRequest(10, 1, "NAMR", businessDate, "RUNNING");
		RunRequest secondRequest = new RunRequest(11, firstRequest.appId(), firstRequest.region(), businessDate,
				"RUNNING");
		RunSummary summary = mock(RunSummary.class);
		when(summary.tradesProcessed()).thenReturn(40);
		when(summary.alertsGenerated()).thenReturn(2);
		when(summary.alertsDispatched()).thenReturn(2);
		when(summary.duplicateAlerts()).thenReturn(0);
		when(runRequestRepository.claimNextRunnableRequest()).thenReturn(Optional.of(firstRequest))
				.thenReturn(Optional.of(secondRequest)).thenReturn(Optional.empty());
		when(surveillanceApplication.run(firstRequest.requestId(), firstRequest.appId(), firstRequest.region(),
				firstRequest.businessDate())).thenReturn(summary);
		when(surveillanceApplication.run(secondRequest.requestId(), secondRequest.appId(), secondRequest.region(),
				secondRequest.businessDate())).thenReturn(summary);

		FiccRunRequestWorker worker = new FiccRunRequestWorker(surveillanceApplication, runRequestRepository);

		worker.run();

		verify(runRequestRepository, times(3)).claimNextRunnableRequest();
		verify(surveillanceApplication).run(firstRequest.requestId(), firstRequest.appId(), firstRequest.region(),
				firstRequest.businessDate());
		verify(surveillanceApplication).run(secondRequest.requestId(), secondRequest.appId(), secondRequest.region(),
				secondRequest.businessDate());
		verify(runRequestRepository).markCompleted(firstRequest, summary);
		verify(runRequestRepository).markCompleted(secondRequest, summary);
	}

	@Test
	void failedRequestIsMarkedFailedAndNextPendingRequestContinues() {
		RunRequest failedRequest = new RunRequest(10, 1, "NAMR", LocalDate.of(2026, 6, 8), "RUNNING");
		RunRequest nextRequest = new RunRequest(11, failedRequest.appId(), "EMEA", LocalDate.of(2026, 6, 9), "RUNNING");
		IllegalStateException failure = new IllegalStateException("database down");
		RunSummary summary = mock(RunSummary.class);
		when(summary.tradesProcessed()).thenReturn(40);
		when(summary.alertsGenerated()).thenReturn(2);
		when(summary.alertsDispatched()).thenReturn(2);
		when(summary.duplicateAlerts()).thenReturn(0);
		when(runRequestRepository.claimNextRunnableRequest()).thenReturn(Optional.of(failedRequest))
				.thenReturn(Optional.of(nextRequest)).thenReturn(Optional.empty());
		when(surveillanceApplication.run(failedRequest.requestId(), failedRequest.appId(), failedRequest.region(),
				failedRequest.businessDate())).thenThrow(failure);
		when(surveillanceApplication.run(nextRequest.requestId(), nextRequest.appId(), nextRequest.region(),
				nextRequest.businessDate())).thenReturn(summary);

		FiccRunRequestWorker worker = new FiccRunRequestWorker(surveillanceApplication, runRequestRepository);

		worker.run();

		verify(runRequestRepository).markFailed(failedRequest, failure);
		verify(runRequestRepository, never()).markCompleted(eq(failedRequest), any(RunSummary.class));
		verify(runRequestRepository).markCompleted(nextRequest, summary);
	}
}
