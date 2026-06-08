package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

@Component
public class AlertDispatcher {

    private final AlertHistoryRepository alertHistoryRepository;

    public AlertDispatcher(AlertHistoryRepository alertHistoryRepository) {
        this.alertHistoryRepository = Objects.requireNonNull(
                alertHistoryRepository,
                "alertHistoryRepository is required"
        );
    }

    public boolean dispatch(ModelConfig modelConfig, LocalDate businessDate, Alert alert, String alertPayload) {
        Objects.requireNonNull(modelConfig, "modelConfig is required");
        Objects.requireNonNull(businessDate, "businessDate is required");
        Objects.requireNonNull(alert, "alert is required");
        Objects.requireNonNull(alertPayload, "alertPayload is required");

        return alertHistoryRepository.saveIfNew(modelConfig, businessDate, alert, alertPayload);
    }
}
