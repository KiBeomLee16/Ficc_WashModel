package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

@Component
public class AlertDispatcher {

	private final AlertHistoryRepository alertHistoryRepository;
	private final CalibrationResultRepository calibrationResultRepository;

	public AlertDispatcher(AlertHistoryRepository alertHistoryRepository,
			CalibrationResultRepository calibrationResultRepository) {
		this.alertHistoryRepository = Objects.requireNonNull(alertHistoryRepository,
				"alertHistoryRepository is required");
		this.calibrationResultRepository = Objects.requireNonNull(calibrationResultRepository,
				"calibrationResultRepository is required");
	}

	public boolean dispatch(long requestId, ModelConfig modelConfig, LocalDate businessDate, Alert alert,
			String alertPayload) {
		if (requestId <= 0) {
			throw new IllegalArgumentException("requestId must be positive");
		}
		Objects.requireNonNull(modelConfig, "modelConfig is required");
		Objects.requireNonNull(businessDate, "businessDate is required");
		Objects.requireNonNull(alert, "alert is required");
		Objects.requireNonNull(alertPayload, "alertPayload is required");

		return alertHistoryRepository.saveIfNew(requestId, modelConfig, businessDate, alert, alertPayload);
	}

	public boolean dispatchCalibrationResult(long requestId, ModelConfig modelConfig, LocalDate businessDate,
			Alert alert, String alertPayload) {
		if (requestId <= 0) {
			throw new IllegalArgumentException("requestId must be positive");
		}
		Objects.requireNonNull(modelConfig, "modelConfig is required");
		Objects.requireNonNull(businessDate, "businessDate is required");
		Objects.requireNonNull(alert, "alert is required");
		Objects.requireNonNull(alertPayload, "alertPayload is required");

		return calibrationResultRepository.saveIfNew(requestId, modelConfig, businessDate, alert, alertPayload);
	}

	public int clearHistory(ModelConfig modelConfig, LocalDate businessDate) {
		Objects.requireNonNull(modelConfig, "modelConfig is required");
		Objects.requireNonNull(businessDate, "businessDate is required");

		return alertHistoryRepository.deleteByRunCriteria(modelConfig, businessDate);
	}

	public int clearCalibrationResults(long requestId) {
		if (requestId <= 0) {
			throw new IllegalArgumentException("requestId must be positive");
		}

		return calibrationResultRepository.deleteByRequestId(requestId);
	}
}
