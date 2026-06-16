package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.AlertBusinessKey;
import com.portfolio.ficc.model.CalibrationAlertHistoryResult;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.ThresholdSnapshot;
import com.portfolio.ficc.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class CalibrationResultRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalibrationResultRepository.class);

    private static final String THRESHOLD_SNAPSHOT_CALL = "{CALL sp_get_surveillance_model_threshold_snapshot(?, ?, ?)}";
    private static final String INSERT_CALIBRATION_ALERT_HISTORY_CALL = "{CALL sp_insert_ficc_wash_calibration_alert_history(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String INSERT_CALIBRATION_ALERT_DRILL_OUT_CALL = "{CALL sp_insert_ficc_wash_calibration_alert_drill_out(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String FIND_CALIBRATION_ALERT_HISTORY_CALL = "{CALL sp_find_ficc_wash_calibration_alert_history_by_request(?)}";
    private static final String DELETE_CALIBRATION_ALERT_HISTORY_CALL = "{CALL sp_delete_ficc_wash_calibration_alert_history_for_request(?)}";

    private final DatabaseConfig databaseConfig;

    public CalibrationResultRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = Objects.requireNonNull(databaseConfig, "databaseConfig is required");
    }

    public boolean saveIfNew(long requestId, ModelConfig modelConfig, LocalDate businessDate, Alert alert, String alertPayload) {
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId must be positive");
        }
        Objects.requireNonNull(modelConfig, "modelConfig is required");
        Objects.requireNonNull(businessDate, "businessDate is required");
        Objects.requireNonNull(alert, "alert is required");
        Objects.requireNonNull(alertPayload, "alertPayload is required");

        String relatedTradeIds = relatedTradeIds(alert);
        String alertFingerprint = alertFingerprint(modelConfig, alert, relatedTradeIds);
        AlertBusinessKey businessKey = AlertBusinessKey.from(alert);
        LocalDate firstTradeDate = firstTradeDate(alert);
        LocalDate lastTradeDate = lastTradeDate(alert);

        try (Connection connection = getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                ThresholdSnapshot thresholdSnapshot = loadThresholdSnapshot(connection, modelConfig);
                long calibrationAlertHistoryId = insertCalibrationAlertHistory(
                        connection,
                        requestId,
                        modelConfig,
                        businessDate,
                        alert,
                        alertPayload,
                        relatedTradeIds,
                        alertFingerprint,
                        businessKey,
                        firstTradeDate,
                        lastTradeDate,
                        thresholdSnapshot
                );
                insertCalibrationAlertDrillOutRows(connection, calibrationAlertHistoryId, alert);
                connection.commit();
                LOGGER.info("Saved calibration alert history: calibrationAlertHistoryId={}, requestId={}, alertId={}, matchType={}, appid={}, modelid={}, region={}, businessDate={}.",
                        calibrationAlertHistoryId,
                        requestId,
                        alert.alertId(),
                        alert.matchType(),
                        modelConfig.appId(),
                        modelConfig.modelId(),
                        modelConfig.region(),
                        businessDate);
            } catch (DuplicateCalibrationAlertHistoryException duplicateAlert) {
                rollbackQuietly(connection);
                return false;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                LOGGER.error("Failed to save calibration alert history. Rolling back transaction: alertId={}.",
                        alert.alertId(), exception);
                throw exception;
            } finally {
                restoreAutoCommitQuietly(connection, originalAutoCommit);
            }
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save calibration alert history for alertId="
                    + alert.alertId(), exception);
        }
    }

    public List<CalibrationAlertHistoryResult> findByRequestId(long requestId) {
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId must be positive");
        }

        try (Connection connection = getConnection();
             CallableStatement statement = connection.prepareCall(FIND_CALIBRATION_ALERT_HISTORY_CALL)) {
            statement.setLong(1, requestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CalibrationAlertHistoryResult> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(toCalibrationAlertHistoryResult(resultSet));
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to search calibration alert history for requestId="
                    + requestId, exception);
        }
    }

    public int deleteByRequestId(long requestId) {
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId must be positive");
        }

        try (Connection connection = getConnection();
             CallableStatement statement = connection.prepareCall(DELETE_CALIBRATION_ALERT_HISTORY_CALL)) {
            statement.setLong(1, requestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int deletedAlertCount = resultSet.getInt("deleted_alert_count");
                    int deletedTradeCount = resultSet.getInt("deleted_trade_count");
                    LOGGER.info("Deleted existing calibration alert history before refresh: requestId={}, alerts={}, drillOutTrades={}.",
                            requestId, deletedAlertCount, deletedTradeCount);
                    return deletedAlertCount;
                }
            }
            return 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete calibration alert history for requestId="
                    + requestId, exception);
        }
    }

    protected Connection getConnection() throws SQLException {
        return databaseConfig.getConnection();
    }

    private ThresholdSnapshot loadThresholdSnapshot(Connection connection, ModelConfig modelConfig) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(THRESHOLD_SNAPSHOT_CALL)) {
            statement.setInt(1, modelConfig.appId());
            statement.setInt(2, modelConfig.modelId());
            statement.setString(3, modelConfig.region());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new ThresholdSnapshot(
                            resultSet.getBigDecimal("one_time_min_total_amount"),
                            resultSet.getBigDecimal("cumulative_min_total_amount"),
                            resultSet.getBigDecimal("quantity_tolerance_percent"),
                            resultSet.getBigDecimal("total_amount_tolerance_percent"),
                            resultSet.getInt("cumulative_lookup_days")
                    );
                }
            }
        }
        throw new SQLException("Threshold snapshot procedure did not return a row");
    }

    private long insertCalibrationAlertHistory(
            Connection connection,
            long requestId,
            ModelConfig modelConfig,
            LocalDate businessDate,
            Alert alert,
            String alertPayload,
            String relatedTradeIds,
            String alertFingerprint,
            AlertBusinessKey businessKey,
            LocalDate firstTradeDate,
            LocalDate lastTradeDate,
            ThresholdSnapshot thresholdSnapshot
    ) throws SQLException, DuplicateCalibrationAlertHistoryException {
        try (CallableStatement statement = connection.prepareCall(INSERT_CALIBRATION_ALERT_HISTORY_CALL)) {
            statement.setString(1, alertFingerprint);
            statement.setString(2, alert.alertId());
            statement.setLong(3, requestId);
            statement.setInt(4, modelConfig.appId());
            statement.setInt(5, modelConfig.modelId());
            statement.setString(6, modelConfig.region());
            statement.setString(7, alert.alertType());
            statement.setString(8, alert.matchType());
            statement.setDate(9, Date.valueOf(businessDate));
            statement.setDate(10, Date.valueOf(firstTradeDate));
            statement.setDate(11, Date.valueOf(lastTradeDate));
            statement.setString(12, relatedTradeIds);
            statement.setString(13, businessKey.hash(alert.matchType()));
            statement.setDate(14, Date.valueOf(businessKey.tradeDate()));
            statement.setString(15, businessKey.assetClass());
            statement.setString(16, businessKey.instrumentId());
            statement.setDate(17, Date.valueOf(businessKey.maturityDate()));
            statement.setString(18, businessKey.currency());
            statement.setString(19, businessKey.traderId());
            statement.setString(20, businessKey.counterpartyId());
            statement.setString(21, alertPayload);
            statement.setBigDecimal(22, thresholdSnapshot.oneTimeMinTotalAmount());
            statement.setBigDecimal(23, thresholdSnapshot.cumulativeMinTotalAmount());
            statement.setBigDecimal(24, thresholdSnapshot.quantityTolerancePercent());
            statement.setBigDecimal(25, thresholdSnapshot.totalAmountTolerancePercent());
            statement.setInt(26, thresholdSnapshot.cumulativeLookupDays());
            statement.setString(27, "DISPATCHED");

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("calibration_alert_history_id");
                }
            }
            throw new SQLException("Calibration alert history insert did not return generated id");
        } catch (SQLIntegrityConstraintViolationException duplicateAlert) {
            throw new DuplicateCalibrationAlertHistoryException(duplicateAlert);
        } catch (SQLException exception) {
            if ("23000".equals(exception.getSQLState()) || exception.getErrorCode() == 1062) {
                throw new DuplicateCalibrationAlertHistoryException(exception);
            }
            throw exception;
        }
    }

    private void insertCalibrationAlertDrillOutRows(
            Connection connection,
            long calibrationAlertHistoryId,
            Alert alert
    ) throws SQLException {
        List<Trade> relatedTrades = sortedRelatedTrades(alert);
        try (CallableStatement statement = connection.prepareCall(INSERT_CALIBRATION_ALERT_DRILL_OUT_CALL)) {
            int sequence = 1;
            for (Trade trade : relatedTrades) {
                statement.setLong(1, calibrationAlertHistoryId);
                statement.setInt(2, sequence++);
                statement.setString(3, trade.tradeId());
                statement.setDate(4, Date.valueOf(trade.timestamp().toLocalDate()));
                statement.setTimestamp(5, Timestamp.valueOf(trade.timestamp()));
                statement.setString(6, trade.assetClass());
                statement.setString(7, trade.instrumentId());
                statement.setDate(8, Date.valueOf(trade.maturity()));
                statement.setString(9, trade.currency());
                statement.setString(10, trade.side().name());
                statement.setBigDecimal(11, trade.quantity());
                statement.setBigDecimal(12, trade.price());
                statement.setBigDecimal(13, trade.totalAmount());
                statement.setString(14, trade.counterpartyId());
                statement.setString(15, trade.accountId());
                statement.setString(16, trade.beneficialOwner());
                statement.setString(17, trade.traderId());
                statement.setString(18, trade.desk());
                statement.setString(19, trade.book());
                statement.setString(20, trade.broker());
                statement.setString(21, trade.side().name() + "_LEG");
                statement.executeUpdate();
            }
        }
    }

    private CalibrationAlertHistoryResult toCalibrationAlertHistoryResult(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new CalibrationAlertHistoryResult(
                resultSet.getLong("calibration_alert_history_id"),
                resultSet.getString("alert_id"),
                resultSet.getLong("request_id"),
                resultSet.getInt("appid"),
                resultSet.getInt("modelid"),
                resultSet.getString("region"),
                resultSet.getString("alert_type"),
                resultSet.getString("match_type"),
                resultSet.getDate("business_date").toLocalDate(),
                resultSet.getDate("first_trade_date").toLocalDate(),
                resultSet.getDate("last_trade_date").toLocalDate(),
                resultSet.getString("related_trade_ids"),
                resultSet.getString("alert_business_key_hash"),
                resultSet.getDate("trade_date").toLocalDate(),
                resultSet.getString("asset_class"),
                resultSet.getString("instrument_id"),
                resultSet.getDate("maturity_date").toLocalDate(),
                resultSet.getString("currency"),
                resultSet.getString("trader_id"),
                resultSet.getString("counterparty_id"),
                resultSet.getString("alert_payload"),
                resultSet.getBigDecimal("one_time_min_total_amount"),
                resultSet.getBigDecimal("cumulative_min_total_amount"),
                resultSet.getBigDecimal("quantity_tolerance_percent"),
                resultSet.getBigDecimal("total_amount_tolerance_percent"),
                resultSet.getInt("cumulative_lookup_days"),
                resultSet.getString("dispatch_status"),
                createdAt == null ? null : createdAt.toLocalDateTime()
        );
    }

    private String alertFingerprint(ModelConfig modelConfig, Alert alert, String relatedTradeIds) {
        String fingerprintInput = modelConfig.appId()
                + "|" + modelConfig.modelId()
                + "|" + modelConfig.region().trim().toUpperCase()
                + "|" + alert.alertType().trim().toUpperCase()
                + "|" + alert.matchType().trim().toUpperCase()
                + "|" + relatedTradeIds;
        return sha256Hex(fingerprintInput);
    }

    private String relatedTradeIds(Alert alert) {
        return sortedRelatedTrades(alert)
                .stream()
                .map(Trade::tradeId)
                .collect(Collectors.joining(","));
    }

    private LocalDate firstTradeDate(Alert alert) {
        return alert.relatedTrades()
                .stream()
                .min(Comparator.comparing(Trade::timestamp).thenComparing(Trade::tradeId))
                .orElseThrow()
                .timestamp()
                .toLocalDate();
    }

    private LocalDate lastTradeDate(Alert alert) {
        return alert.relatedTrades()
                .stream()
                .max(Comparator.comparing(Trade::timestamp).thenComparing(Trade::tradeId))
                .orElseThrow()
                .timestamp()
                .toLocalDate();
    }

    private List<Trade> sortedRelatedTrades(Alert alert) {
        return alert.relatedTrades()
                .stream()
                .sorted(Comparator.comparing(Trade::timestamp).thenComparing(Trade::tradeId))
                .toList();
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original database error.
        }
    }

    private void restoreAutoCommitQuietly(Connection connection, boolean originalAutoCommit) {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException ignored) {
            // Connection will be closed by try-with-resources.
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static class DuplicateCalibrationAlertHistoryException extends Exception {

        DuplicateCalibrationAlertHistoryException(SQLException cause) {
            super(cause);
        }
    }
}
