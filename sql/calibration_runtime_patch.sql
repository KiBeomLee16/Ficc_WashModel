USE ficc_surveillance;

INSERT IGNORE INTO surveillance_model_master (
    appid,
    region,
    name,
    model_code,
    model_name,
    model_class_name,
    description,
    enabled
) VALUES
(4, 'NAMRC', 'NAMRC FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'North America FICC_WASH Calibration Model.', TRUE),
(5, 'EMEAC', 'EMEAC FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'Europe, Middle East, and Africa FICC_WASH Calibration Model.', TRUE),
(6, 'APACC', 'APACC FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'Asia Pacific FICC_WASH Calibration Model.', TRUE);

INSERT IGNORE INTO surveillance_model_config (
    appid,
    modelid,
    region,
    enabled
) VALUES
(4, 1, 'NAMRC', TRUE),
(5, 1, 'EMEAC', TRUE),
(6, 1, 'APACC', TRUE);

INSERT INTO surveillance_model_threshold (
    appid,
    modelid,
    region,
    threshold_name,
    threshold_value,
    lookup_days,
    enabled
) VALUES
(4, 1, 'NAMRC', 'ONE_TIME_MIN_TOTAL_AMOUNT', 100000000.000000, 0, TRUE),
(4, 1, 'NAMRC', 'CUMULATIVE_MIN_TOTAL_AMOUNT', 5000000.000000, 4, TRUE),
(4, 1, 'NAMRC', 'QUANTITY_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(4, 1, 'NAMRC', 'TOTAL_AMOUNT_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(5, 1, 'EMEAC', 'ONE_TIME_MIN_TOTAL_AMOUNT', 100000000.000000, 0, TRUE),
(5, 1, 'EMEAC', 'CUMULATIVE_MIN_TOTAL_AMOUNT', 5000000.000000, 4, TRUE),
(5, 1, 'EMEAC', 'QUANTITY_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(5, 1, 'EMEAC', 'TOTAL_AMOUNT_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(6, 1, 'APACC', 'ONE_TIME_MIN_TOTAL_AMOUNT', 100000000.000000, 0, TRUE),
(6, 1, 'APACC', 'CUMULATIVE_MIN_TOTAL_AMOUNT', 5000000.000000, 4, TRUE),
(6, 1, 'APACC', 'QUANTITY_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(6, 1, 'APACC', 'TOTAL_AMOUNT_TOLERANCE_PERCENT', 5.000000, 0, TRUE)
ON DUPLICATE KEY UPDATE
    enabled = TRUE;

DROP PROCEDURE IF EXISTS sp_update_surveillance_model_threshold;
DROP PROCEDURE IF EXISTS sp_get_ficc_trades;

DELIMITER //

CREATE PROCEDURE sp_update_surveillance_model_threshold(
    IN p_appid INT,
    IN p_modelid INT,
    IN p_region VARCHAR(10),
    IN p_threshold_name VARCHAR(80),
    IN p_threshold_value DECIMAL(22, 6),
    IN p_lookup_days INT
)
BEGIN
    UPDATE surveillance_model_threshold
    SET threshold_value = p_threshold_value,
        lookup_days = p_lookup_days
    WHERE appid = p_appid
      AND modelid = p_modelid
      AND region = UPPER(p_region)
      AND threshold_name = UPPER(p_threshold_name)
      AND enabled = TRUE;

    SELECT COUNT(*) AS threshold_count
    FROM surveillance_model_threshold
    WHERE appid = p_appid
      AND modelid = p_modelid
      AND region = UPPER(p_region)
      AND threshold_name = UPPER(p_threshold_name)
      AND enabled = TRUE;
END//

CREATE PROCEDURE sp_get_ficc_trades(
    IN p_appid INT,
    IN p_modelid INT,
    IN p_region VARCHAR(20),
    IN p_business_date DATE
)
BEGIN
    DECLARE v_model_region VARCHAR(20);
    DECLARE v_trade_region VARCHAR(20);

    SET v_model_region = UPPER(p_region);
    SET v_trade_region = CASE v_model_region
        WHEN 'NAMRC' THEN 'NAMR'
        WHEN 'EMEAC' THEN 'EMEA'
        WHEN 'APACC' THEN 'APAC'
        ELSE v_model_region
    END;

    SELECT
        trade.trade_id,
        trade.trade_timestamp,
        trade.asset_class,
        trade.instrument_id,
        trade.maturity,
        trade.currency,
        trade.side,
        trade.quantity,
        trade.price,
        trade.counterparty_id,
        trade.account_id,
        trade.beneficial_owner,
        trade.trader_id,
        trade.desk,
        trade.book,
        trade.broker
    FROM ficc_trade trade
    JOIN surveillance_model_threshold threshold
      ON threshold.appid = p_appid
     AND threshold.modelid = p_modelid
     AND threshold.region = v_model_region
     AND threshold.threshold_name = 'CUMULATIVE_MIN_TOTAL_AMOUNT'
     AND threshold.enabled = TRUE
    WHERE trade.region = v_trade_region
      AND trade.trade_date BETWEEN DATE_SUB(p_business_date, INTERVAL threshold.lookup_days DAY)
                               AND p_business_date
    ORDER BY trade.trade_timestamp, trade.trade_id;
END//

DELIMITER ;
