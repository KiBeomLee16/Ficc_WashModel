package com.portfolio.ficc.app;

import com.portfolio.ficc.io.RunRequestRepository;
import com.portfolio.ficc.model.RunRequest;
import com.portfolio.ficc.model.RunSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

@Service
public class FiccRunRequestWorker {

    private final FiccSurveillanceApplication surveillanceApplication;
    private final RunRequestRepository runRequestRepository;

    public FiccRunRequestWorker(
            FiccSurveillanceApplication surveillanceApplication,
            RunRequestRepository runRequestRepository
    ) {
        this.surveillanceApplication = surveillanceApplication;
        this.runRequestRepository = runRequestRepository;
    }

    public void run() {
        processRunnableRequests();
    }

    void processRunnableRequests() {
        Queue<RunRequest> runRequests = claimRunnableRequests();

        if (runRequests.isEmpty()) {
            System.out.println("No pending or failed surveillance run request found.");
            return;
        }

        while (!runRequests.isEmpty()) {
            processClaimedRequest(runRequests.remove());
        }
    }

    private void processClaimedRequest(RunRequest request) {
        System.out.printf("Run request %d started for appid=%d, region=%s, businessDate=%s.%n",
                request.requestId(), request.appId(), request.region(), request.businessDate());
        try {
            RunSummary summary = surveillanceApplication.run(
                    request.appId(),
                    request.region(),
                    request.businessDate()
            );
            runRequestRepository.markCompleted(request, summary);
            System.out.printf("Run request %d completed: trades=%d, generated=%d, dispatched=%d, duplicates=%d.%n",
                    request.requestId(),
                    summary.tradesProcessed(),
                    summary.alertsGenerated(),
                    summary.alertsDispatched(),
                    summary.duplicateAlerts());
        } catch (RuntimeException exception) {
            runRequestRepository.markFailed(request, exception);
            System.out.printf("Run request %d failed: %s%n", request.requestId(), exception.getMessage());
        }
    }

    private Queue<RunRequest> claimRunnableRequests() {
        Queue<RunRequest> runRequests = new ArrayDeque<>();
        while (true) {
            Optional<RunRequest> request = runRequestRepository.claimNextRunnableRequest();
            if (request.isEmpty()) {
                return runRequests;
            }
            runRequests.add(request.get());
        }
    }
}
