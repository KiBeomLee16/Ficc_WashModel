package com.portfolio.ficc.app;

import com.portfolio.ficc.io.RunRequestRepository;
import com.portfolio.ficc.model.RunRequest;
import com.portfolio.ficc.model.RunSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

@Service
public class FiccRunRequestWorker {

	private static final Logger LOGGER = LoggerFactory.getLogger(FiccRunRequestWorker.class);

	private final FiccSurveillanceApplication surveillanceApplication;
	private final RunRequestRepository runRequestRepository;

	public FiccRunRequestWorker(FiccSurveillanceApplication surveillanceApplication,
			RunRequestRepository runRequestRepository) {
		this.surveillanceApplication = surveillanceApplication;
		this.runRequestRepository = runRequestRepository;
	}

	public void run() {
		LOGGER.info("Starting surveillance run request worker.");
		processRunnableRequests();
	}

	void processRunnableRequests() {
		Queue<RunRequest> runRequests = claimRunnableRequests();

		if (runRequests.isEmpty()) {
			LOGGER.info("No pending or failed surveillance run request found.");
			System.out.println("No pending or failed surveillance run request found.");
			return;
		}

		LOGGER.info("Claimed {} runnable surveillance request(s).", runRequests.size());
		while (!runRequests.isEmpty()) {
			processClaimedRequest(runRequests.remove());
		}
		LOGGER.info("------------------------------------------------------------------------------------------");
		LOGGER.info("Finished processing claimed surveillance requests.");
	}

	private void processClaimedRequest(RunRequest request) {
		LOGGER.info("------------------------------------------------------------------------------------------");
		LOGGER.info("Run request {} started: appid={}, region={}, businessDate={}.", request.requestId(),
				request.appId(), request.region(), request.businessDate());
		System.out.printf("Run request %d started for appid=%d, region=%s, businessDate=%s.%n", request.requestId(),
				request.appId(), request.region(), request.businessDate());
		try {
			RunSummary summary = surveillanceApplication.run(
					request.requestId(),
					request.appId(),
					request.region(),
					request.businessDate());
			runRequestRepository.markCompleted(request, summary);
			LOGGER.info("------------------------------------------------------------------------------------------");
			LOGGER.info(
					"Run request {} completed: tradesProcessed={}, alertsGenerated={}, alertsDispatched={}, duplicateAlerts={}.",
					request.requestId(), summary.tradesProcessed(), summary.alertsGenerated(),
					summary.alertsDispatched(), summary.duplicateAlerts());
			System.out.printf("Run request %d completed: trades=%d, generated=%d, dispatched=%d, duplicates=%d.%n",
					request.requestId(), summary.tradesProcessed(), summary.alertsGenerated(),
					summary.alertsDispatched(), summary.duplicateAlerts());

		} catch (RuntimeException exception) {
			runRequestRepository.markFailed(request, exception);
			LOGGER.error("Run request {} failed: appid={}, region={}, businessDate={}.", request.requestId(),
					request.appId(), request.region(), request.businessDate(), exception);
			System.out.printf("Run request %d failed: %s%n", request.requestId(), exception.getMessage());
		}
	}

	private Queue<RunRequest> claimRunnableRequests() {
		Queue<RunRequest> runRequests = new ArrayDeque<>();
		while (true) {
			LOGGER.info("------------------------------------------------------------------------------------------");
			LOGGER.debug("Attempting to claim next runnable surveillance request.");
			Optional<RunRequest> request = runRequestRepository.claimNextRunnableRequest();
			if (request.isEmpty()) {
				return runRequests;
			}
			runRequests.add(request.get());
		}
	}
}
