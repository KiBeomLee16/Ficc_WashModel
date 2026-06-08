package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.Trade;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class AlertHistoryRepository {

    private static final String INSERT_ALERT_HISTORY_SQL = """
            INSERT INTO ficc_wash_alert_history (
                alert_fingerprint,
                alert_id,
                appid,
                modelid,
                region,
                alert_type,
                match_type,
                business_date,
                first_trade_date,
                last_trade_date,
                related_trade_ids,
                alert_payload,
                dispatch_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_ALERT_HISTORY_TRADE_SQL = """
            INSERT INTO ficc_wash_alert_history_trade (
                alert_history_id,
                trade_sequence,
                trade_id,
                trade_date,
                trade_timestamp,
                asset_class,
                instrument_id,
                maturity,
                currency,
                side,
                quantity,
                price,
                total_amount,
                counterparty_id,
                account_id,
                beneficial_owner,
                trader_id,
                desk,
                book,
                broker,
                trade_role
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final DatabaseConfig databaseConfig;

    public AlertHistoryRepository(DatabaseConfig databaseConfig) {
        this.databaseConfig = Objects.requireNonNull(databaseConfig, "databaseConfig is required");
    }

    public boolean saveIfNew(ModelConfig modelConfig, LocalDate businessDate, Alert alert, String alertPayload) {
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
            } catch (DuplicateAlertHistoryException duplicateAlert) {
                rollbackQuietly(connection);
                return false;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw exception;
            } finally {
                restoreAutoCommitQuietly(connection, originalAutoCommit);
            }
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save alert history for alertId=" + alert.alertId(), exception);
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
            ModelConfig modelConfig,
            LocalDate businessDate,
            Alert alert,
            String alertPayload,
            String relatedTradeIds,
            String alertFingerprint,
            LocalDate firstTradeDate,
            LocalDate lastTradeDate
    ) throws SQLException, DuplicateAlertHistoryException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_ALERT_HISTORY_SQL,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, alertFingerprint);
            statement.setString(2, alert.alertId());
            statement.setInt(3, modelConfig.appId());
            statement.setInt(4, modelConfig.modelId());
            statement.setString(5, modelConfig.region());
            statement.setString(6, alert.alertType());
            statement.setString(7, alert.matchType());
            statement.setDate(8, Date.valueOf(businessDate));
            statement.setDate(9, Date.valueOf(firstTradeDate));
            statement.setDate(10, Date.valueOf(lastTradeDate));
            statement.setString(11, relatedTradeIds);
            statement.setString(12, alertPayload);
            statement.setString(13, "DISPATCHED");

            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
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
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ALERT_HISTORY_TRADE_SQL)) {
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
                statement.addBatch();
            }
            statement.executeBatch();
        }
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
