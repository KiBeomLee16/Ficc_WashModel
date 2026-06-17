package com.portfolio.ficc.web;

import com.portfolio.ficc.io.AlertHistoryRepository;
import com.portfolio.ficc.io.RunRequestRepository;
import com.portfolio.ficc.model.AlertHistoryResult;
import com.portfolio.ficc.model.RunRequestStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Controller
@CrossOrigin(origins = "http://localhost:5173")
public class AlertHistoryController {

	private final AlertHistoryRepository alertHistoryRepository;
	private final RunRequestRepository runRequestRepository;

	public AlertHistoryController(AlertHistoryRepository alertHistoryRepository,
			RunRequestRepository runRequestRepository) {
		this.alertHistoryRepository = Objects.requireNonNull(alertHistoryRepository,
				"alertHistoryRepository is required");
		this.runRequestRepository = Objects.requireNonNull(runRequestRepository, "runRequestRepository is required");
	}

	@GetMapping(value = "/alert-history", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<AlertHistorySearchResponse> searchAlertHistory(@RequestParam int appId,
			@RequestParam String region,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
		String normalizedRegion = region.trim().toUpperCase();
		List<AlertHistoryResult> alerts = alertHistoryRepository.findByRunCriteria(appId, normalizedRegion,
				businessDate);
		List<RunRequestStatus> runRequests = runRequestRepository.findByRunCriteria(appId, normalizedRegion,
				businessDate);
		RunRequestStatus latestRunRequest = runRequests.isEmpty() ? null : runRequests.get(0);

		return ResponseEntity.ok(new AlertHistorySearchResponse(appId, normalizedRegion, businessDate, alerts.size(),
				alerts, latestRunRequest, runRequests));
	}

	public record AlertHistorySearchResponse(int appId, String region, LocalDate businessDate, int alertCount,
			List<AlertHistoryResult> alerts, RunRequestStatus runRequest, List<RunRequestStatus> runRequests) {
	}
}
