package com.portfolio.ficc.web;

import com.portfolio.ficc.io.CalibrationThresholdRepository;
import com.portfolio.ficc.io.RunRequestRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Controller
@CrossOrigin(origins = "http://localhost:5173")
public class CalibrationRunRequestController {

	private final CalibrationThresholdRepository calibrationThresholdRepository;
	private final RunRequestRepository runRequestRepository;

	public CalibrationRunRequestController(CalibrationThresholdRepository calibrationThresholdRepository,
			RunRequestRepository runRequestRepository) {
		this.calibrationThresholdRepository = Objects.requireNonNull(calibrationThresholdRepository,
				"calibrationThresholdRepository is required");
		this.runRequestRepository = Objects.requireNonNull(runRequestRepository, "runRequestRepository is required");
	}

	@PostMapping(value = "/calibration-run-request", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<CalibrationRunRequestResponse> submitCalibrationRunRequest(@RequestParam int appId,
			@RequestParam String region,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
			@RequestParam BigDecimal oneTimeMinTotalAmount, @RequestParam BigDecimal cumulativeMinTotalAmount,
			@RequestParam BigDecimal quantityTolerancePercent, @RequestParam BigDecimal totalAmountTolerancePercent,
			@RequestParam(defaultValue = "4") int cumulativeLookupDays,
			@RequestParam(defaultValue = "frontend-calibration-user") String requestedBy) {
		String normalizedRegion = region.trim().toUpperCase();
		calibrationThresholdRepository.updateCalibrationThresholds(appId, normalizedRegion, oneTimeMinTotalAmount,
				cumulativeMinTotalAmount, quantityTolerancePercent, totalAmountTolerancePercent, cumulativeLookupDays);
		long requestId = runRequestRepository.insertRunRequest(appId, normalizedRegion, businessDate, requestedBy);

		return ResponseEntity.accepted()
				.body(new CalibrationRunRequestResponse(requestId, appId, normalizedRegion, businessDate, "PENDING",
						oneTimeMinTotalAmount, cumulativeMinTotalAmount, quantityTolerancePercent,
						totalAmountTolerancePercent, cumulativeLookupDays));
	}

	public record CalibrationRunRequestResponse(long requestId, int appId, String region, LocalDate businessDate,
			String status, BigDecimal oneTimeMinTotalAmount, BigDecimal cumulativeMinTotalAmount,
			BigDecimal quantityTolerancePercent, BigDecimal totalAmountTolerancePercent, int cumulativeLookupDays) {
	}
}
