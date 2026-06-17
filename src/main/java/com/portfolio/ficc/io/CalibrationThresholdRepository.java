package com.portfolio.ficc.io;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

@Component
public class CalibrationThresholdRepository {

	private static final int MODEL_ID = 1;
	private static final String UPDATE_THRESHOLD_CALL = "{CALL sp_update_surveillance_model_threshold(?, ?, ?, ?, ?, ?)}";
	private static final String ONE_TIME_MIN_TOTAL_AMOUNT = "ONE_TIME_MIN_TOTAL_AMOUNT";
	private static final String CUMULATIVE_MIN_TOTAL_AMOUNT = "CUMULATIVE_MIN_TOTAL_AMOUNT";
	private static final String QUANTITY_TOLERANCE_PERCENT = "QUANTITY_TOLERANCE_PERCENT";
	private static final String TOTAL_AMOUNT_TOLERANCE_PERCENT = "TOTAL_AMOUNT_TOLERANCE_PERCENT";
	private static final Map<String, Integer> CALIBRATION_APP_IDS = Map.of("NAMRC", 4, "EMEAC", 5, "APACC", 6);

	private final DatabaseConfig databaseConfig;

	public CalibrationThresholdRepository(DatabaseConfig databaseConfig) {
		this.databaseConfig = Objects.requireNonNull(databaseConfig, "databaseConfig is required");
	}

	public void updateCalibrationThresholds(int appId, String region, BigDecimal oneTimeMinTotalAmount,
			BigDecimal cumulativeMinTotalAmount, BigDecimal quantityTolerancePercent,
			BigDecimal totalAmountTolerancePercent, int cumulativeLookupDays) {
		String normalizedRegion = requireCalibrationRegion(appId, region);
		requireNonNegative(oneTimeMinTotalAmount, "oneTimeMinTotalAmount");
		requireNonNegative(cumulativeMinTotalAmount, "cumulativeMinTotalAmount");
		requireNonNegative(quantityTolerancePercent, "quantityTolerancePercent");
		requireNonNegative(totalAmountTolerancePercent, "totalAmountTolerancePercent");
		if (cumulativeLookupDays < 0) {
			throw new IllegalArgumentException("cumulativeLookupDays cannot be negative");
		}

		try (Connection connection = getConnection()) {
			boolean originalAutoCommit = connection.getAutoCommit();
			try {
				connection.setAutoCommit(false);
				updateThreshold(connection, appId, normalizedRegion, ONE_TIME_MIN_TOTAL_AMOUNT, oneTimeMinTotalAmount,
						0);
				updateThreshold(connection, appId, normalizedRegion, CUMULATIVE_MIN_TOTAL_AMOUNT,
						cumulativeMinTotalAmount, cumulativeLookupDays);
				updateThreshold(connection, appId, normalizedRegion, QUANTITY_TOLERANCE_PERCENT,
						quantityTolerancePercent, 0);
				updateThreshold(connection, appId, normalizedRegion, TOTAL_AMOUNT_TOLERANCE_PERCENT,
						totalAmountTolerancePercent, 0);
				connection.commit();
			} catch (SQLException exception) {
				rollbackQuietly(connection);
				throw exception;
			} catch (RuntimeException exception) {
				rollbackQuietly(connection);
				throw exception;
			} finally {
				restoreAutoCommitQuietly(connection, originalAutoCommit);
			}
		} catch (SQLException exception) {
			throw new IllegalStateException(
					"Failed to update calibration thresholds for appid=" + appId + ", region=" + normalizedRegion,
					exception);
		}
	}

	protected Connection getConnection() throws SQLException {
		return databaseConfig.getConnection();
	}

	private void updateThreshold(Connection connection, int appId, String region, String thresholdName,
			BigDecimal thresholdValue, int lookupDays) throws SQLException {
		try (CallableStatement statement = connection.prepareCall(UPDATE_THRESHOLD_CALL)) {
			statement.setInt(1, appId);
			statement.setInt(2, MODEL_ID);
			statement.setString(3, region);
			statement.setString(4, thresholdName);
			statement.setBigDecimal(5, thresholdValue);
			statement.setInt(6, lookupDays);

			boolean hasResultSet = statement.execute();
			while (true) {
				if (hasResultSet) {
					try (ResultSet resultSet = statement.getResultSet()) {
						if (resultSet.next() && resultSet.getInt("threshold_count") > 0) {
							return;
						}
					}
				} else if (statement.getUpdateCount() == -1) {
					break;
				}
				hasResultSet = statement.getMoreResults();
			}
		}
		throw new IllegalStateException("No calibration threshold row found for appid=" + appId + ", region=" + region
				+ ", thresholdName=" + thresholdName);
	}

	private String requireCalibrationRegion(int appId, String region) {
		Objects.requireNonNull(region, "region is required");
		String normalizedRegion = region.trim().toUpperCase();
		Integer expectedAppId = CALIBRATION_APP_IDS.get(normalizedRegion);
		if (expectedAppId == null || expectedAppId != appId) {
			throw new IllegalArgumentException("Calibration region must match appid: NAMRC=4, EMEAC=5, APACC=6");
		}
		return normalizedRegion;
	}

	private void requireNonNegative(BigDecimal value, String fieldName) {
		Objects.requireNonNull(value, fieldName + " is required");
		if (value.signum() < 0) {
			throw new IllegalArgumentException(fieldName + " cannot be negative");
		}
	}

	private void rollbackQuietly(Connection connection) {
		try {
			connection.rollback();
		} catch (SQLException ignored) {
			// Preserve the original database exception.
		}
	}

	private void restoreAutoCommitQuietly(Connection connection, boolean originalAutoCommit) {
		try {
			connection.setAutoCommit(originalAutoCommit);
		} catch (SQLException ignored) {
			// Connection will be closed by try-with-resources.
		}
	}
}
