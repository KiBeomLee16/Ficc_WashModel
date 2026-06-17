package com.portfolio.ficc.web;

import com.portfolio.ficc.io.RunRequestRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.Objects;

@Controller
@CrossOrigin(origins = "http://localhost:5173")
public class RunRequestController {

	private final RunRequestRepository runRequestRepository;

	public RunRequestController(RunRequestRepository runRequestRepository) {
		this.runRequestRepository = Objects.requireNonNull(runRequestRepository, "runRequestRepository is required");
	}

	@PostMapping(value = "/run-request", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<RunRequestSubmissionResponse> submitRunRequest(@RequestParam int appId,
			@RequestParam String region,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
			@RequestParam(defaultValue = "frontend-user") String requestedBy) {
		long requestId = runRequestRepository.insertRunRequest(appId, region, businessDate, requestedBy);

		return ResponseEntity.accepted().body(new RunRequestSubmissionResponse(requestId, appId,
				region.trim().toUpperCase(), businessDate, "PENDING"));
	}

	public record RunRequestSubmissionResponse(long requestId, int appId, String region, LocalDate businessDate,
			String status) {
	}
}
