package com.portfolio.ficc.web;

import com.portfolio.ficc.io.CalibrationResultRepository;
import com.portfolio.ficc.io.AlertHistoryRepository;
import com.portfolio.ficc.io.RunRequestRepository;
import com.portfolio.ficc.model.AlertHistoryResult;
import com.portfolio.ficc.model.CalibrationAlertHistoryResult;
import com.portfolio.ficc.model.RunRequestStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Controller
@CrossOrigin(origins = "http://localhost:5173")
public class CalibrationResultController {

	private final RunRequestRepository runRequestRepository;
	private final CalibrationResultRepository calibrationResultRepository;
	private final AlertHistoryRepository alertHistoryRepository;

	public CalibrationResultController(RunRequestRepository runRequestRepository,
			CalibrationResultRepository calibrationResultRepository, AlertHistoryRepository alertHistoryRepository) {
		this.runRequestRepository = Objects.requireNonNull(runRequestRepository, "runRequestRepository is required");
		this.calibrationResultRepository = Objects.requireNonNull(calibrationResultRepository,
				"calibrationResultRepository is required");
		this.alertHistoryRepository = Objects.requireNonNull(alertHistoryRepository,
				"alertHistoryRepository is required");
	}

	@GetMapping(value = "/calibration-run-requests", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<CalibrationRunRequestsResponse> calibrationRunRequests() {
		return ResponseEntity.ok(new CalibrationRunRequestsResponse(runRequestRepository.findCalibrationRunRequests()));
	}

	@GetMapping(value = "/calibration-results", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<CalibrationResultResponse> calibrationResults(@RequestParam long requestId) {
		RunRequestStatus runRequest = runRequestRepository.findByRequestId(requestId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"No run request found for requestId=" + requestId));
		List<CalibrationAlertHistoryResult> calibrationAlerts = calibrationResultRepository.findByRequestId(requestId);
		List<AlertHistoryResult> productionAlerts = findProductionAlerts(runRequest);
		List<CalibrationComparisonAlert> comparisonAlerts = compareCalibrationToProduction(runRequest,
				calibrationAlerts, productionAlerts);

		return ResponseEntity.ok(new CalibrationResultResponse(runRequest, calibrationAlerts.size(), comparisonAlerts));
	}

	private List<AlertHistoryResult> findProductionAlerts(RunRequestStatus runRequest) {
		ProductionRunCriteria criteria = productionRunCriteria(runRequest);
		return alertHistoryRepository.findByRunCriteria(criteria.appId(), criteria.region(), runRequest.businessDate());
	}

	private List<CalibrationComparisonAlert> compareCalibrationToProduction(RunRequestStatus runRequest,
			List<CalibrationAlertHistoryResult> calibrationAlerts, List<AlertHistoryResult> productionAlerts) {
		Map<String, Deque<AlertHistoryResult>> productionByKey = new LinkedHashMap<>();
		for (AlertHistoryResult productionAlert : productionAlerts) {
			productionByKey.computeIfAbsent(productionAlert.alertBusinessKeyHash(), ignored -> new ArrayDeque<>())
					.add(productionAlert);
		}

		List<CalibrationComparisonAlert> comparisonAlerts = new java.util.ArrayList<>();
		for (CalibrationAlertHistoryResult calibrationAlert : calibrationAlerts) {
			Deque<AlertHistoryResult> matchingProductionAlerts = productionByKey
					.get(calibrationAlert.alertBusinessKeyHash());
			AlertHistoryResult matchingProductionAlert = matchingProductionAlerts == null ? null
					: matchingProductionAlerts.pollFirst();
			if (matchingProductionAlerts != null && matchingProductionAlerts.isEmpty()) {
				productionByKey.remove(calibrationAlert.alertBusinessKeyHash());
			}
			if (matchingProductionAlert == null) {
				comparisonAlerts.add(fromCalibrationOnlyAlert(runRequest, calibrationAlert));
			} else {
				comparisonAlerts.add(fromMatchedProductionAlert(runRequest, calibrationAlert, matchingProductionAlert));
			}
		}

		for (Deque<AlertHistoryResult> removedProductionAlerts : productionByKey.values()) {
			for (AlertHistoryResult removedProductionAlert : removedProductionAlerts) {
				comparisonAlerts.add(fromProductionOnlyAlert(runRequest, removedProductionAlert));
			}
		}

		return comparisonAlerts;
	}

	private CalibrationComparisonAlert fromCalibrationOnlyAlert(RunRequestStatus runRequest,
			CalibrationAlertHistoryResult calibrationAlert) {
		return new CalibrationComparisonAlert(calibrationAlert.calibrationAlertHistoryId(), null, "CALIBRATION_NEW",
				calibrationAlert.alertId(), runRequest.requestId(), null, calibrationAlert.appId(),
				calibrationAlert.modelId(), calibrationAlert.region(), calibrationAlert.alertType(),
				calibrationAlert.matchType(), calibrationAlert.businessDate(), calibrationAlert.firstTradeDate(),
				calibrationAlert.lastTradeDate(), calibrationAlert.relatedTradeIds(),
				calibrationAlert.alertBusinessKeyHash(), calibrationAlert.tradeDate(), calibrationAlert.assetClass(),
				calibrationAlert.instrumentId(), calibrationAlert.maturityDate(), calibrationAlert.currency(),
				calibrationAlert.traderId(), calibrationAlert.counterpartyId(), calibrationAlert.alertPayload(),
				calibrationAlert.oneTimeMinTotalAmount(), calibrationAlert.cumulativeMinTotalAmount(),
				calibrationAlert.quantityTolerancePercent(), calibrationAlert.totalAmountTolerancePercent(),
				calibrationAlert.cumulativeLookupDays(), calibrationAlert.dispatchStatus(),
				calibrationAlert.createdAt());
	}

	private CalibrationComparisonAlert fromMatchedProductionAlert(RunRequestStatus runRequest,
			CalibrationAlertHistoryResult calibrationAlert, AlertHistoryResult productionAlert) {
		return new CalibrationComparisonAlert(calibrationAlert.calibrationAlertHistoryId(),
				productionAlert.alertHistoryId(), "SAME_AS_PRODUCTION", productionAlert.alertId(),
				runRequest.requestId(), productionAlert.requestId(), productionAlert.appId(), productionAlert.modelId(),
				productionAlert.region(), productionAlert.alertType(), productionAlert.matchType(),
				productionAlert.businessDate(), productionAlert.firstTradeDate(), productionAlert.lastTradeDate(),
				productionAlert.relatedTradeIds(), productionAlert.alertBusinessKeyHash(), productionAlert.tradeDate(),
				productionAlert.assetClass(), productionAlert.instrumentId(), productionAlert.maturityDate(),
				productionAlert.currency(), productionAlert.traderId(), productionAlert.counterpartyId(),
				productionAlert.alertPayload(), calibrationAlert.oneTimeMinTotalAmount(),
				calibrationAlert.cumulativeMinTotalAmount(), calibrationAlert.quantityTolerancePercent(),
				calibrationAlert.totalAmountTolerancePercent(), calibrationAlert.cumulativeLookupDays(),
				productionAlert.dispatchStatus(), productionAlert.createdAt());
	}

	private CalibrationComparisonAlert fromProductionOnlyAlert(RunRequestStatus runRequest,
			AlertHistoryResult productionAlert) {
		return new CalibrationComparisonAlert(null, productionAlert.alertHistoryId(), "PRODUCTION_REMOVED",
				productionAlert.alertId(), runRequest.requestId(), productionAlert.requestId(), productionAlert.appId(),
				productionAlert.modelId(), productionAlert.region(), productionAlert.alertType(),
				productionAlert.matchType(), productionAlert.businessDate(), productionAlert.firstTradeDate(),
				productionAlert.lastTradeDate(), productionAlert.relatedTradeIds(),
				productionAlert.alertBusinessKeyHash(), productionAlert.tradeDate(), productionAlert.assetClass(),
				productionAlert.instrumentId(), productionAlert.maturityDate(), productionAlert.currency(),
				productionAlert.traderId(), productionAlert.counterpartyId(), productionAlert.alertPayload(), null,
				null, null, null, null, productionAlert.dispatchStatus(), productionAlert.createdAt());
	}

	private ProductionRunCriteria productionRunCriteria(RunRequestStatus runRequest) {
		return switch (runRequest.region().trim().toUpperCase()) {
		case "NAMRC" -> new ProductionRunCriteria(1, "NAMR");
		case "EMEAC" -> new ProductionRunCriteria(2, "EMEA");
		case "APACC" -> new ProductionRunCriteria(3, "APAC");
		default -> new ProductionRunCriteria(runRequest.appId(), runRequest.region().trim().toUpperCase());
		};
	}

	public record CalibrationRunRequestsResponse(List<RunRequestStatus> requests) {
	}

	public record CalibrationResultResponse(RunRequestStatus runRequest, int alertCount,
			List<CalibrationComparisonAlert> alerts) {
	}

	public record CalibrationComparisonAlert(Long calibrationAlertHistoryId, Long productionAlertHistoryId,
			String comparisonStatus, String alertId, long requestId, Long productionRequestId, int appId, int modelId,
			String region, String alertType, String matchType, java.time.LocalDate businessDate,
			java.time.LocalDate firstTradeDate, java.time.LocalDate lastTradeDate, String relatedTradeIds,
			String alertBusinessKeyHash, java.time.LocalDate tradeDate, String assetClass, String instrumentId,
			java.time.LocalDate maturityDate, String currency, String traderId, String counterpartyId,
			String alertPayload, java.math.BigDecimal oneTimeMinTotalAmount,
			java.math.BigDecimal cumulativeMinTotalAmount, java.math.BigDecimal quantityTolerancePercent,
			java.math.BigDecimal totalAmountTolerancePercent, Integer cumulativeLookupDays, String dispatchStatus,
			java.time.LocalDateTime createdAt) {
	}

	private record ProductionRunCriteria(int appId, String region) {
	}
}
