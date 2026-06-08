package com.portfolio.ficc.app;

import com.portfolio.ficc.io.RunRequestRepository;
import com.portfolio.ficc.model.RunRequest;
import com.portfolio.ficc.model.RunSummary;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class FiccRunRequestWorker {

    private static final String QUEUE_ARGUMENT = "--queue";
    private static final String REQUEST_ID_ARGUMENT_PREFIX = "--request-id=";

    private final FiccSurveillanceApplication surveillanceApplication;
    private final RunRequestRepository runRequestRepository;
    private final String workerId;

    public FiccRunRequestWorker(
            FiccSurveillanceApplication surveillanceApplication,
            RunRequestRepository runRequestRepository
    ) {
        this.surveillanceApplication = surveillanceApplication;
        this.runRequestRepository = runRequestRepository;
        this.workerId = "ficc-worker-" + UUID.randomUUID();
    }

    public void run(String[] args) {
        String[] safeArgs = args == null ? new String[0] : args;
        Optional<Long> requestId = requestId(safeArgs);

        if (safeArgs.length == 0 || hasArgument(safeArgs, QUEUE_ARGUMENT)) {
            processNextPendingRequest();
            return;
        }
        if (requestId.isPresent()) {
            processRequestById(requestId.get());
            return;
        }

        surveillanceApplication.run(safeArgs);
    }

    void processNextPendingRequest() {
        Optional<RunRequest> request = runRequestRepository.claimNextPendingRequest(workerId());
        if (request.isEmpty()) {
            System.out.println("No pending surveillance run request found.");
            return;
        }
        processClaimedRequest(request.get());
    }

    void processRequestById(long requestId) {
        Optional<RunRequest> request = runRequestRepository.claimRequestById(requestId, workerId());
        if (request.isEmpty()) {
            System.out.printf("No pending surveillance run request found for requestId=%d.%n", requestId);
            return;
        }
        processClaimedRequest(request.get());
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
            throw exception;
        }
    }

    private Optional<Long> requestId(String[] args) {
        return Arrays.stream(args)
                .filter(argument -> argument.startsWith(REQUEST_ID_ARGUMENT_PREFIX))
                .map(argument -> argument.substring(REQUEST_ID_ARGUMENT_PREFIX.length()))
                .map(Long::parseLong)
                .findFirst();
    }

    private boolean hasArgument(String[] args, String expectedArgument) {
        return Arrays.stream(args).anyMatch(expectedArgument::equalsIgnoreCase);
    }

    String workerId() {
        return workerId;
    }
}
