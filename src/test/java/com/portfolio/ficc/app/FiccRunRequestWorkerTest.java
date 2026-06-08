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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiccRunRequestWorkerTest {

    @Mock
    private FiccSurveillanceApplication surveillanceApplication;

    @Mock
    private RunRequestRepository runRequestRepository;

    @Test
    void noArgsClaimsNextPendingRequestAndMarksCompleted() {
        RunRequest request = runRequest();
        RunSummary summary = runSummary();
        when(runRequestRepository.claimNextPendingRequest(anyString())).thenReturn(Optional.of(request));
        when(surveillanceApplication.run(1, "NAMR", LocalDate.of(2026, 6, 8))).thenReturn(summary);

        FiccRunRequestWorker worker = new FiccRunRequestWorker(surveillanceApplication, runRequestRepository);

        worker.run(new String[0]);

        verify(runRequestRepository).claimNextPendingRequest(anyString());
        verify(surveillanceApplication).run(1, "NAMR", LocalDate.of(2026, 6, 8));
        verify(runRequestRepository).markCompleted(request, summary);
    }

    @Test
    void requestIdArgumentClaimsSpecificPendingRequest() {
        RunRequest request = runRequest();
        RunSummary summary = runSummary();
        when(runRequestRepository.claimRequestById(10L, "test-worker")).thenReturn(Optional.of(request));
        when(surveillanceApplication.run(1, "NAMR", LocalDate.of(2026, 6, 8))).thenReturn(summary);

        FiccRunRequestWorker worker = new TestWorker(surveillanceApplication, runRequestRepository);

        worker.run(new String[]{"--request-id=10"});

        verify(runRequestRepository).claimRequestById(10L, "test-worker");
        verify(runRequestRepository).markCompleted(request, summary);
    }

    @Test
    void positionalArgsUseDirectRunMode() {
        String[] args = {"1", "NAMR", "2026-06-08"};
        FiccRunRequestWorker worker = new FiccRunRequestWorker(surveillanceApplication, runRequestRepository);

        worker.run(args);

        verify(surveillanceApplication).run(args);
        verify(runRequestRepository, never()).claimNextPendingRequest(anyString());
    }

    @Test
    void failedRequestIsMarkedFailedAndRethrown() {
        RunRequest request = runRequest();
        IllegalStateException failure = new IllegalStateException("database down");
        when(runRequestRepository.claimNextPendingRequest(anyString())).thenReturn(Optional.of(request));
        when(surveillanceApplication.run(1, "NAMR", LocalDate.of(2026, 6, 8))).thenThrow(failure);

        FiccRunRequestWorker worker = new FiccRunRequestWorker(surveillanceApplication, runRequestRepository);

        assertThrows(IllegalStateException.class, () -> worker.run(new String[]{"--queue"}));

        verify(runRequestRepository).markFailed(request, failure);
        verify(runRequestRepository, never()).markCompleted(request, runSummary());
    }

    private static RunRequest runRequest() {
        return new RunRequest(
                10,
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

    private static class TestWorker extends FiccRunRequestWorker {

        TestWorker(FiccSurveillanceApplication surveillanceApplication, RunRequestRepository runRequestRepository) {
            super(surveillanceApplication, runRequestRepository);
        }

        @Override
        String workerId() {
            return "test-worker";
        }
    }
}
