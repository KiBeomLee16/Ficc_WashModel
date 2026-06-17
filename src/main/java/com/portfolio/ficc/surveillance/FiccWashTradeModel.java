package com.portfolio.ficc.surveillance;

import com.portfolio.ficc.io.AlertDispatcher;
import com.portfolio.ficc.io.DatabaseConfig;
import com.portfolio.ficc.model.Alert;
import com.portfolio.ficc.model.ModelConfig;
import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FiccWashTradeModel extends AbstractSurveillanceModel {

	public static final String MODEL_CODE = "FICC_WASH_TRADE";

	private static final String TRADE_STORED_PROCEDURE = "sp_get_ficc_trades";
	private static final String ONE_TIME_MIN_TOTAL_AMOUNT = "ONE_TIME_MIN_TOTAL_AMOUNT";
	private static final String CUMULATIVE_MIN_TOTAL_AMOUNT = "CUMULATIVE_MIN_TOTAL_AMOUNT";
	private static final String QUANTITY_TOLERANCE_PERCENT = "QUANTITY_TOLERANCE_PERCENT";
	private static final String TOTAL_AMOUNT_TOLERANCE_PERCENT = "TOTAL_AMOUNT_TOLERANCE_PERCENT";
	private static final String ONE_TIME_MATCH_TYPE = "ONE_TIME_TRANSACTION";
	private static final String CUMULATIVE_MATCH_TYPE = "CUMULATIVE_TRANSACTION";
	private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

	public FiccWashTradeModel(DatabaseConfig databaseConfig, AlertDispatcher alertDispatcher) {
		super(databaseConfig, alertDispatcher);
	}

	@Override
	public String modelCode() {
		return MODEL_CODE;
	}

	@Override
	public List<Trade> getTrades(ModelConfig modelConfig, String region, LocalDate businessDate) {
		Objects.requireNonNull(modelConfig, "modelConfig is required");
		Objects.requireNonNull(region, "region is required");
		String normalizedRegion = region.toUpperCase();
		Objects.requireNonNull(businessDate, "businessDate is required");

		String callSql = "{CALL " + TRADE_STORED_PROCEDURE + "(?, ?, ?, ?)}";
		List<Trade> trades = new ArrayList<>();

		try (Connection connection = getConnection(); CallableStatement statement = connection.prepareCall(callSql)) {

			statement.setInt(1, modelConfig.appId());
			statement.setInt(2, modelConfig.modelId());
			statement.setString(3, normalizedRegion);
			statement.setDate(4, Date.valueOf(businessDate));

			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					trades.add(toTrade(resultSet));
				}
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to load trades from stored procedure " + TRADE_STORED_PROCEDURE
					+ " for region=" + normalizedRegion + ", businessDate=" + businessDate, exception);
		}

		return List.copyOf(trades);
	}

	@Override
	public List<Alert> evaluate(ModelConfig modelConfig, List<Trade> trades, LocalDate businessDate) {
		if (trades.isEmpty()) {
			return List.of();
		}

		WashTradeThresholds thresholds = loadThresholds(modelConfig, businessDate);
		List<Alert> alerts = new ArrayList<>(evaluateOneTimeMatches(modelConfig, trades, businessDate, thresholds));

		evaluateCumulativeMatches(modelConfig, trades, businessDate, thresholds, alerts);

		return List.copyOf(alerts);
	}

	private WashTradeThresholds loadThresholds(ModelConfig modelConfig, LocalDate businessDate) {
		int cumulativeLookupDays = getThresholdLookupDays(modelConfig, CUMULATIVE_MIN_TOTAL_AMOUNT, businessDate);
		return new WashTradeThresholds(getAlertThreshold(modelConfig, ONE_TIME_MIN_TOTAL_AMOUNT, businessDate),
				getAlertThreshold(modelConfig, CUMULATIVE_MIN_TOTAL_AMOUNT, businessDate),
				getAlertThreshold(modelConfig, QUANTITY_TOLERANCE_PERCENT, businessDate),
				getAlertThreshold(modelConfig, TOTAL_AMOUNT_TOLERANCE_PERCENT, businessDate), cumulativeLookupDays,
				businessDate.minusDays(cumulativeLookupDays), businessDate);
	}

	private List<Alert> evaluateOneTimeMatches(ModelConfig modelConfig, List<Trade> trades, LocalDate businessDate,
			WashTradeThresholds thresholds) {
		BiPredicate<Trade, Trade> oneTimeMatch = (buyTrade, sellTrade) -> isOneTimeMatch(buyTrade, sellTrade,
				thresholds);

		return sortedTrades(trades).stream().filter(trade -> trade.timestamp().toLocalDate().equals(businessDate))
				.collect(Collectors.groupingBy(TradeMatchKey::from, LinkedHashMap::new, Collectors.toList())).values()
				.stream().flatMap(group -> {
					List<Trade> buyTrades = tradesBySide(group, Side.BUY);
					List<Trade> sellTrades = tradesBySide(group, Side.SELL);
					return buyTrades.stream().flatMap(buyTrade -> sellTrades.stream()
							.filter(sellTrade -> oneTimeMatch.test(buyTrade, sellTrade))
							.map(sellTrade -> createOneTimeAlert(modelConfig, buyTrade, sellTrade, thresholds)));
				}).toList();
	}

	private boolean isOneTimeMatch(Trade tradeA, Trade tradeB, WashTradeThresholds thresholds) {
		if (!hasSameInstrument(tradeA, tradeB) || !hasOppositeSides(tradeA, tradeB)
				|| !hasSameCounterparty(tradeA, tradeB)) {
			return false;
		}

		Trade buyTrade = buyTrade(tradeA, tradeB);
		Trade sellTrade = sellTrade(tradeA, tradeB);
		return isWithinTolerance(buyTrade.quantity(), sellTrade.quantity(), thresholds.quantityTolerancePercent())
				&& isWithinTolerance(buyTrade.totalAmount(), sellTrade.totalAmount(),
						thresholds.totalAmountTolerancePercent())
				&& minimum(buyTrade.totalAmount(), sellTrade.totalAmount())
						.compareTo(thresholds.oneTimeMinTotalAmount()) >= 0;
	}

	private Alert createOneTimeAlert(ModelConfig modelConfig, Trade tradeA, Trade tradeB,
			WashTradeThresholds thresholds) {
		Trade buyTrade = buyTrade(tradeA, tradeB);
		Trade sellTrade = sellTrade(tradeA, tradeB);
		BigDecimal quantityDifference = percentDifference(buyTrade.quantity(), sellTrade.quantity());
		BigDecimal amountDifference = percentDifference(buyTrade.totalAmount(), sellTrade.totalAmount());
		BigDecimal matchedAmount = minimum(buyTrade.totalAmount(), sellTrade.totalAmount());

		List<String> reasons = new ArrayList<>();
		reasons.add("One-time quantity tolerance: actual difference " + formatPercent(quantityDifference)
				+ ", threshold " + formatPercent(thresholds.quantityTolerancePercent()) + ", within threshold.");
		reasons.add("One-time total amount tolerance: actual difference " + formatPercent(amountDifference)
				+ ", threshold " + formatPercent(thresholds.totalAmountTolerancePercent()) + ", within threshold.");
		reasons.add("One-time minimum amount: matched amount " + matchedAmount.toPlainString() + ", threshold "
				+ thresholds.oneTimeMinTotalAmount().toPlainString() + ", above threshold.");

		return new Alert(generateAlertId(tradeA, tradeB), modelConfig.modelCode(), ONE_TIME_MATCH_TYPE, tradeA, tradeB,
				sortedTrades(List.of(tradeA, tradeB)), buyTrade.quantity(), sellTrade.quantity(),
				buyTrade.totalAmount(), sellTrade.totalAmount(), thresholds.oneTimeMinTotalAmount(), reasons,
				Instant.now());
	}

	private void evaluateCumulativeMatches(ModelConfig modelConfig, List<Trade> trades, LocalDate businessDate,
			WashTradeThresholds thresholds, List<Alert> alerts) {
		Map<TradeMatchKey, List<Trade>> groupedTrades = new LinkedHashMap<>();
		for (Trade trade : sortedTrades(trades)) {
			groupedTrades.computeIfAbsent(TradeMatchKey.from(trade), ignored -> new ArrayList<>()).add(trade);
		}

		for (List<Trade> group : groupedTrades.values()) {
			if (group.size() <= 2) {
				continue;
			}

			List<Trade> buyTrades = tradesBySide(group, Side.BUY);
			List<Trade> sellTrades = tradesBySide(group, Side.SELL);
			if (buyTrades.isEmpty() || sellTrades.isEmpty()) {
				continue;
			}
			if (group.stream().noneMatch(trade -> trade.timestamp().toLocalDate().equals(businessDate))) {
				continue;
			}

			BigDecimal totalBuyQuantity = sumQuantity(buyTrades);
			BigDecimal totalSellQuantity = sumQuantity(sellTrades);
			BigDecimal totalBuyAmount = sumAmount(buyTrades);
			BigDecimal totalSellAmount = sumAmount(sellTrades);
			BigDecimal matchedAmount = minimum(totalBuyAmount, totalSellAmount);

			if (isWithinTolerance(totalBuyQuantity, totalSellQuantity, thresholds.quantityTolerancePercent())
					&& isWithinTolerance(totalBuyAmount, totalSellAmount, thresholds.totalAmountTolerancePercent())
					&& matchedAmount.compareTo(thresholds.cumulativeMinTotalAmount()) >= 0) {
				alerts.add(
						createCumulativeAlert(modelConfig, buyTrades.get(0), sellTrades.get(0), group, totalBuyQuantity,
								totalSellQuantity, totalBuyAmount, totalSellAmount, matchedAmount, thresholds));
			}
		}
	}

	private Alert createCumulativeAlert(ModelConfig modelConfig, Trade buyTrade, Trade sellTrade,
			List<Trade> relatedTrades, BigDecimal totalBuyQuantity, BigDecimal totalSellQuantity,
			BigDecimal totalBuyAmount, BigDecimal totalSellAmount, BigDecimal matchedAmount,
			WashTradeThresholds thresholds) {
		BigDecimal quantityDifference = percentDifference(totalBuyQuantity, totalSellQuantity);
		BigDecimal amountDifference = percentDifference(totalBuyAmount, totalSellAmount);

		List<String> reasons = new ArrayList<>();
		reasons.add("Aggregate quantity tolerance: actual difference " + formatPercent(quantityDifference)
				+ ", threshold " + formatPercent(thresholds.quantityTolerancePercent()) + ", within threshold.");
		reasons.add("Aggregate total amount tolerance: actual difference " + formatPercent(amountDifference)
				+ ", threshold " + formatPercent(thresholds.totalAmountTolerancePercent()) + ", within threshold.");
		reasons.add("Aggregate minimum amount: matched amount " + matchedAmount.toPlainString() + ", threshold "
				+ thresholds.cumulativeMinTotalAmount().toPlainString() + ", above threshold.");

		return new Alert(generateAlertId(buyTrade, sellTrade), modelConfig.modelCode(), CUMULATIVE_MATCH_TYPE, buyTrade,
				sellTrade, sortedTrades(relatedTrades), totalBuyQuantity, totalSellQuantity, totalBuyAmount,
				totalSellAmount, thresholds.cumulativeMinTotalAmount(), reasons, Instant.now());
	}

	private boolean hasSameInstrument(Trade tradeA, Trade tradeB) {
		// Same Instrument Rule: only compare trades in the exact same FICC product
		// context.
		return sameText(tradeA.assetClass(), tradeB.assetClass())
				&& sameText(tradeA.instrumentId(), tradeB.instrumentId()) && tradeA.maturity().equals(tradeB.maturity())
				&& sameText(tradeA.currency(), tradeB.currency());
	}

	private boolean hasOppositeSides(Trade tradeA, Trade tradeB) {
		// Opposite Side Rule: wash trade candidates must offset BUY against SELL.
		return tradeA.side().isOpposite(tradeB.side());
	}

	private boolean hasSameCounterparty(Trade tradeA, Trade tradeB) {
		return sameText(tradeA.counterpartyId(), tradeB.counterpartyId());
	}

	private Trade buyTrade(Trade tradeA, Trade tradeB) {
		return tradeA.side() == Side.BUY ? tradeA : tradeB;
	}

	private Trade sellTrade(Trade tradeA, Trade tradeB) {
		return tradeA.side() == Side.SELL ? tradeA : tradeB;
	}

	private boolean isWithinTolerance(BigDecimal left, BigDecimal right, BigDecimal tolerancePercent) {
		return percentDifference(left, right).compareTo(tolerancePercent) <= 0;
	}

	private BigDecimal percentDifference(BigDecimal left, BigDecimal right) {
		BigDecimal max = left.max(right);
		if (max.signum() == 0) {
			return BigDecimal.ZERO;
		}
		return left.subtract(right).abs().multiply(ONE_HUNDRED).divide(max, 6, RoundingMode.HALF_UP);
	}

	private BigDecimal minimum(BigDecimal left, BigDecimal right) {
		return left.min(right);
	}

	private BigDecimal sumQuantity(List<Trade> trades) {
		return trades.stream().map(Trade::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal sumAmount(List<Trade> trades) {
		return trades.stream().map(Trade::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private List<Trade> tradesBySide(List<Trade> trades, Side side) {
		return trades.stream().filter(trade -> trade.side() == side).toList();
	}

	private List<Trade> sortedTrades(List<Trade> trades) {
		return trades.stream().sorted(Comparator.comparing(Trade::timestamp).thenComparing(Trade::tradeId)).toList();
	}

	private String formatPercent(BigDecimal percent) {
		return percent.stripTrailingZeros().toPlainString() + "%";
	}

	private boolean sameText(String left, String right) {
		return left.trim().equalsIgnoreCase(right.trim());
	}

	private record WashTradeThresholds(BigDecimal oneTimeMinTotalAmount, BigDecimal cumulativeMinTotalAmount,
			BigDecimal quantityTolerancePercent, BigDecimal totalAmountTolerancePercent, int cumulativeLookupDays,
			LocalDate lookupStartDate, LocalDate lookupEndDate) {
	}

	private record TradeMatchKey(String assetClass, String instrumentId, LocalDate maturity, String currency,
			String counterpartyId) {

		private static TradeMatchKey from(Trade trade) {
			return new TradeMatchKey(trade.assetClass(), trade.instrumentId(), trade.maturity(), trade.currency(),
					trade.counterpartyId());
		}
	}
}
