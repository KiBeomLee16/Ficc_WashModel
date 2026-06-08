package com.portfolio.ficc.app;

import com.portfolio.ficc.io.DatabaseConfig;
import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.RunSummary;
import com.portfolio.ficc.model.Trade;
import com.portfolio.ficc.surveillance.AbstractSurveillanceModel;
import com.portfolio.ficc.surveillance.SurveillanceModelRegistry;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FiccSurveillanceApplication {

    private static final String MODEL_CONFIG_PROCEDURE = "sp_get_surveillance_model_config";

    private final DatabaseConfig databaseConfig;
    private final SurveillanceModelRegistry modelRegistry;

    public FiccSurveillanceApplication(
            DatabaseConfig databaseConfig,
            SurveillanceModelRegistry modelRegistry
    ) {
        this.databaseConfig = Objects.requireNonNull(databaseConfig, "databaseConfig is required");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry is required");
    }

    public RunSummary run(int appId, String region, LocalDate businessDate) {
        ModelConfig modelConfig = getSpecificModel(appId, region);
        AbstractSurveillanceModel model = getModel(modelConfig);

        List<Trade> trades = model.getTrades(modelConfig, region, businessDate);
        List<Alert> alerts = model.evaluate(modelConfig, trades, businessDate);
        int dispatchedAlerts = 0;
        int duplicateAlerts = 0;

        for (Alert alert : alerts) {
            String alertPayload = model.generateJson(alert);
            if (model.dispatchAlert(modelConfig, businessDate, alert, alertPayload)) {
                dispatchedAlerts++;
            } else {
                duplicateAlerts++;
                System.out.printf("Skipped duplicate alert %s for appid=%d, modelid=%d, region=%s, businessDate=%s.%n",
                        alert.alertId(), appId, modelConfig.modelId(), modelConfig.region(), businessDate);
            }
        }

        System.out.printf("Processed %d trades for appid=%d, modelid=%d, model=%s, class=%s, region=%s, businessDate=%s and dispatched %d alerts, skipped %d duplicates.%n",
                trades.size(), appId, modelConfig.modelId(), modelConfig.modelCode(),
                modelConfig.modelClassName(), modelConfig.region(), businessDate, dispatchedAlerts, duplicateAlerts);

        return new RunSummary(
                appId,
                modelConfig.modelId(),
                modelConfig.modelCode(),
                modelConfig.region(),
                businessDate,
                trades.size(),
                alerts.size(),
                dispatchedAlerts,
                duplicateAlerts
        );
    }

    /**
     * Resolve the user-provided app ID and region to the specific model class.
     */
    public ModelConfig getSpecificModel(int appId, String region) {
        if (appId <= 0) {
            throw new IllegalArgumentException("appId must be positive");
        }
        String normalizedRegion = region.toUpperCase();
        String callSql = "{CALL " + MODEL_CONFIG_PROCEDURE + "(?, ?)}";

        try (Connection connection = getConnection();
             CallableStatement statement = connection.prepareCall(callSql)) {

            statement.setInt(1, appId);
            statement.setString(2, normalizedRegion);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new ModelConfig(
                            resultSet.getInt("appid"),
                            resultSet.getInt("modelid"),
                            resultSet.getString("region"),
                            resultSet.getString("app_name"),
                            resultSet.getString("model_code"),
                            resultSet.getString("model_name"),
                            resultSet.getString("model_class_name")
                    );
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load model config for appid="
                    + appId + ", region=" + normalizedRegion, exception);
        }

        throw new IllegalArgumentException("No active model config found for appid="
                + appId + ", region=" + normalizedRegion);
    }

    private AbstractSurveillanceModel getModel(ModelConfig modelConfig) {
        AbstractSurveillanceModel model = modelRegistry.getModel(modelConfig.modelClassName());
        if (!model.modelCode().equalsIgnoreCase(modelConfig.modelCode())) {
            throw new IllegalArgumentException("Configured modelCode=" + modelConfig.modelCode()
                    + " does not match registered modelCode=" + model.modelCode()
                    + " for class=" + modelConfig.modelClassName());
        }
        return model;
    }

    protected Connection getConnection() throws SQLException {
        return databaseConfig.getConnection();
    }
}
