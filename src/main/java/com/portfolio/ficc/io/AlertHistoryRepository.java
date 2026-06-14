package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.AlertHistoryResult;
import com.portfolio.ficc.model.ModelConfig;
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
public class AlertHistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertHistoryRepository.class);

    private static final String INSERT_ALERT_HISTORY_CALL = "{CALL sp_insert_ficc_wash_alert_history(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String INSERT_ALERT_HISTORY_TRADE_CALL = "{CALL sp_insert_ficc_wash_alert_history_trade(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String FIND_ALERT_HISTORY_CALL = "{CALL sp_find_ficc_wash_alert_history(?, ?, ?)}";
    private static final String DELETE_ALERT_HISTORY_CALL = "{CALL sp_delete_ficc_wash_alert_history_for_run(?, ?, ?, ?)}";

    private final DatabaseConfig databaseConfig;

    public AlertHistoryRepository(DatabaseConfig databaseConfig) {
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
        LocalDate firstTradeDate = firstTradeDate(alert);
        LocalDate lastTradeDate = lastTradeDate(alert);

        try (Connection connection = getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                long alertHistoryId = insertAlertHistory(
                        connection,
                        requestId,
                        modelConfig,
                        businessDate,
                        alert,
                        alertPayload,
                        relatedTradeIds,
                        alertFingerprint,
                        firstTradeDate,
                        lastTradeDate
                );
                insertAlertHistoryTrades(connection, alertHistoryId, alert);
                connection.commit();
                LOGGER.info("Saved alert history: alertHistoryId={}, requestId={}, alertId={}, matchType={}, relatedTrades={}, appid={}, modelid={}, region={}, businessDate={}.",
                        alertHistoryId,
                        requestId,
                        alert.alertId(),
                        alert.matchType(),
                        alert.relatedTrades().size(),
                        modelConfig.appId(),
                        modelConfig.modelId(),
                        modelConfig.region(),
                        businessDate);
            } catch (DuplicateAlertHistoryException duplicateAlert) {
                rollbackQuietly(connection);
//                LOGGER.warn("Duplicate alert history skipped: alertId={}, matchType={}, fingerprint={}, appid={}, modelid={}, region={}, businessDate={}.",
//                        alert.alertId(),
//                        alert.matchType(),
//                        alertFingerprint,
//                        modelConfig.appId(),
//                        modelConfig.modelId(),
//                        modelConfig.region(),
//                        businessDate);
                return false;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                LOGGER.error("Failed to save alert history. Rolling back alert history transaction: alertId={}.",
                        alert.alertId(), exception);
                throw exception;
            } finally {
                restoreAutoCommitQuietly(connection, originalAutoCommit);
            }
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save alert history for alertId=" + alert.alertId(), exception);
        }
    }

    public List<AlertHistoryResult> findByRunCriteria(int appId, String region, LocalDate businessDate) {
        Objects.requireNonNull(region, "region is required");
        Objects.requireNonNull(businessDate, "businessDate is required");

        try (Connection connection = getConnection();
             CallableStatement statement = connection.prepareCall(FIND_ALERT_HISTORY_CALL)) {
            statement.setInt(1, appId);
            statement.setString(2, region.trim().toUpperCase());
            statement.setDate(3, Date.valueOf(businessDate));

            try (ResultSet resultSet = statement.executeQuery()) {
                List<AlertHistoryResult> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(toAlertHistoryResult(resultSet));
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to search alert history for appid="
                    + appId + ", region=" + region + ", businessDate=" + businessDate, exception);
        }
    }

    public int deleteByRunCriteria(ModelConfig modelConfig, LocalDate businessDate) {
        Objects.requireNonNull(modelConfig, "modelConfig is required");
        Objects.requireNonNull(businessDate, "businessDate is required");

        try (Connection connection = getConnection();
             CallableStatement statement = connection.prepareCall(DELETE_ALERT_HISTORY_CALL)) {
            statement.setInt(1, modelConfig.appId());
            statement.setInt(2, modelConfig.modelId());
            statement.setString(3, modelConfig.region().trim().toUpperCase());
            statement.setDate(4, Date.valueOf(businessDate));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int deletedAlertCount = resultSet.getInt("deleted_alert_count");
                    int deletedTradeCount = resultSet.getInt("deleted_trade_count");
                    LOGGER.info("Deleted existing alert history before refresh: alerts={}, drillOutTrades={}, appid={}, modelid={}, region={}, businessDate={}.",
                            deletedAlertCount,
                            deletedTradeCount,
                            modelConfig.appId(),
                            modelConfig.modelId(),
                            modelConfig.region(),
                            businessDate);
                    return deletedAlertCount;
                }
            }
            return 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete alert history for appid="
                    + modelConfig.appId() + ", modelid=" + modelConfig.modelId()
                    + ", region=" + modelConfig.region() + ", businessDate=" + businessDate, exception);
        }
    }

    protected Connection getConnection() throws SQLException {
        return databaseConfig.getConnection();
    }

    String alertFingerprint(ModelConfig modelConfig, Alert alert, String relatedTradeIds) {
        String fingerprintInput = modelConfig.appId()
                + "|" + modelConfig.modelId()
                + "|" + modelConfig.region().trim().toUpperCase()
                + "|" + alert.alertType().trim().toUpperCase()
                + "|" + alert.matchType().trim().toUpperCase()
                + "|" + relatedTradeIds;
        return sha256Hex(fingerprintInput);
    }

    private long insertAlertHistory(
            Connection connection,
            long requestId,
            ModelConfig modelConfig,
            LocalDate businessDate,
            Alert alert,
            String alertPayload,
            String relatedTradeIds,
            String alertFingerprint,
            LocalDate firstTradeDate,
            LocalDate lastTradeDate
    ) throws SQLException, DuplicateAlertHistoryException {
        try (CallableStatement statement = connection.prepareCall(INSERT_ALERT_HISTORY_CALL)) {
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
            statement.setString(13, alertPayload);
            statement.setString(14, "DISPATCHED");

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("alert_history_id");
                }
            }
            throw new SQLException("Alert history insert did not return generated alert_history_id");
        } catch (SQLIntegrityConstraintViolationException duplicateAlert) {
            throw new DuplicateAlertHistoryException(duplicateAlert);
        } catch (SQLException exception) {
            if ("23000".equals(exception.getSQLState()) || exception.getErrorCode() == 1062) {
                throw new DuplicateAlertHistoryException(exception);
            }
            throw exception;
        }
    }

    private void insertAlertHistoryTrades(Connection connection, long alertHistoryId, Alert alert) throws SQLException {
        List<Trade> relatedTrades = sortedRelatedTrades(alert);
        try (CallableStatement statement = connection.prepareCall(INSERT_ALERT_HISTORY_TRADE_CALL)) {
            int sequence = 1;
            for (Trade trade : relatedTrades) {
                statement.setLong(1, alertHistoryId);
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

    private AlertHistoryResult toAlertHistoryResult(ResultSet resultSet) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new AlertHistoryResult(
                resultSet.getLong("alert_history_id"),
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
                resultSet.getString("alert_payload"),
                resultSet.getString("dispatch_status"),
                createdAt == null ? null : createdAt.toLocalDateTime()
        );
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

    private LocalDate lastTradeDate(Alert alert) {
        return alert.relatedTrades()
                .stream()
                .max(Comparator.comparing(Trade::timestamp).thenComparing(Trade::tradeId))
                .orElseThrow()
                .timestamp()
                .toLocalDate();
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

    private static class DuplicateAlertHistoryException extends Exception {

        DuplicateAlertHistoryException(SQLException cause) {
            super(cause);
        }
    }
}
