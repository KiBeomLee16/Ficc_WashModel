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
        RunRequest firstRequest = runRequest(10, "NAMR", LocalDate.of(2026, 6, 8));
        RunRequest secondRequest = runRequest(11, "NAMR", LocalDate.of(2026, 6, 8));
        RunSummary summary = runSummary("NAMR", LocalDate.of(2026, 6, 8));
        when(runRequestRepository.claimNextRunnableRequest())
                .thenReturn(Optional.of(firstRequest))
                .thenReturn(Optional.of(secondRequest))
                .thenReturn(Optional.empty());
        when(surveillanceApplication.run(1, "NAMR", LocalDate.of(2026, 6, 8))).thenReturn(summary);

        FiccRunRequestWorker worker = new FiccRunRequestWorker(surveillanceApplication, runRequestRepository);

        worker.run();

        verify(runRequestRepository, times(3)).claimNextRunnableRequest();
        verify(surveillanceApplication, times(2)).run(1, "NAMR", LocalDate.of(2026, 6, 8));
        verify(runRequestRepository).markCompleted(firstRequest, summary);
        verify(runRequestRepository).markCompleted(secondRequest, summary);
    }

    @Test
    void failedRequestIsMarkedFailedAndNextPendingRequestContinues() {
        RunRequest failedRequest = runRequest(10, "NAMR", LocalDate.of(2026, 6, 8));
        RunRequest nextRequest = runRequest(11, "EMEA", LocalDate.of(2026, 6, 9));
        IllegalStateException failure = new IllegalStateException("database down");
        RunSummary summary = runSummary("EMEA", LocalDate.of(2026, 6, 9));
        when(runRequestRepository.claimNextRunnableRequest())
                .thenReturn(Optional.of(failedRequest))
                .thenReturn(Optional.of(nextRequest))
                .thenReturn(Optional.empty());
        when(surveillanceApplication.run(1, "NAMR", LocalDate.of(2026, 6, 8))).thenThrow(failure);
        when(surveillanceApplication.run(1, "EMEA", LocalDate.of(2026, 6, 9))).thenReturn(summary);

        FiccRunRequestWorker worker = new FiccRunRequestWorker(surveillanceApplication, runRequestRepository);

        worker.run();

        verify(runRequestRepository).markFailed(failedRequest, failure);
        verify(runRequestRepository, never()).markCompleted(failedRequest, summary);
        verify(runRequestRepository).markCompleted(nextRequest, summary);
    }

    private static RunRequest runRequest(long requestId, String region, LocalDate businessDate) {
        return new RunRequest(
                requestId,
                1,
                region,
                businessDate,
                "RUNNING"
        );
    }

    private static RunSummary runSummary(String region, LocalDate businessDate) {
        return new RunSummary(
                1,
                1,
                "FICC_WASH_TRADE",
                region,
                businessDate,
                40,
                2,
                2,
                0
        );
    }

}
