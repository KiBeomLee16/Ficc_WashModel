CREATE DATABASE IF NOT EXISTS ficc_surveillance;
USE ficc_surveillance;

DROP PROCEDURE IF EXISTS sp_get_surveillance_model_config;
DROP PROCEDURE IF EXISTS sp_get_surveillance_model_threshold;
DROP PROCEDURE IF EXISTS sp_update_surveillance_model_threshold;
DROP PROCEDURE IF EXISTS sp_get_ficc_trades;
DROP PROCEDURE IF EXISTS sp_claim_next_surveillance_run_request;
DROP PROCEDURE IF EXISTS sp_insert_surveillance_run_request;
DROP PROCEDURE IF EXISTS sp_find_latest_surveillance_run_request;
DROP PROCEDURE IF EXISTS sp_find_surveillance_run_requests;
DROP PROCEDURE IF EXISTS sp_find_surveillance_run_request_by_id;
DROP PROCEDURE IF EXISTS sp_find_calibration_run_requests;
DROP PROCEDURE IF EXISTS sp_mark_surveillance_run_request_completed;
DROP PROCEDURE IF EXISTS sp_mark_surveillance_run_request_failed;
DROP PROCEDURE IF EXISTS sp_insert_ficc_wash_alert_history;
DROP PROCEDURE IF EXISTS sp_insert_ficc_wash_alert_history_trade;
DROP PROCEDURE IF EXISTS sp_find_ficc_wash_alert_history;
DROP PROCEDURE IF EXISTS sp_delete_ficc_wash_alert_history_for_run;
DROP PROCEDURE IF EXISTS sp_insert_ficc_wash_calibration_alert_history;
DROP PROCEDURE IF EXISTS sp_insert_ficc_wash_calibration_alert_history_trade;
DROP PROCEDURE IF EXISTS sp_find_ficc_wash_calibration_alert_history_by_request;
DROP PROCEDURE IF EXISTS sp_delete_ficc_wash_calibration_alert_history_for_request;
DROP PROCEDURE IF EXISTS sp_get_surveillance_model_threshold_snapshot;
DROP TABLE IF EXISTS ficc_wash_calibration_alert_history_trade;
DROP TABLE IF EXISTS ficc_wash_calibration_alert_history;
DROP TABLE IF EXISTS ficc_wash_alert_history_trade;
DROP TABLE IF EXISTS ficc_wash_alert_history;
DROP TABLE IF EXISTS surveillance_run_request;
DROP TABLE IF EXISTS surveillance_model_threshold;
DROP TABLE IF EXISTS surveillance_model_config;
DROP TABLE IF EXISTS surveillance_model_master;
DROP TABLE IF EXISTS ficc_trade;

CREATE TABLE surveillance_model_master (
    appid INT PRIMARY KEY,
    region VARCHAR(10) NOT NULL,
    name VARCHAR(120) NOT NULL,
    model_code VARCHAR(80) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    model_class_name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_surveillance_model_master_app_region (appid, region),
    UNIQUE KEY uk_surveillance_model_master_region (region)
);

CREATE TABLE surveillance_model_config (
    appid INT NOT NULL,
    modelid INT NOT NULL,
    region VARCHAR(10) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (appid, modelid, region),
    CONSTRAINT fk_surveillance_model_config_master
        FOREIGN KEY (appid, region) REFERENCES surveillance_model_master (appid, region)
);

CREATE TABLE surveillance_run_request (
    request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    appid INT NOT NULL,
    region VARCHAR(10) NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_by VARCHAR(80) NOT NULL DEFAULT 'LOCAL_USER',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    alerts_generated INT NOT NULL DEFAULT 0,
    error_message VARCHAR(2000),
    INDEX idx_surveillance_run_request_queue (status, requested_at, request_id),
    INDEX idx_surveillance_run_request_business_date (region, business_date),
    CONSTRAINT fk_surveillance_run_request_master
        FOREIGN KEY (appid, region)
        REFERENCES surveillance_model_master (appid, region)
);

CREATE TABLE surveillance_model_threshold (
    appid INT NOT NULL,
    modelid INT NOT NULL,
    region VARCHAR(10) NOT NULL,
    threshold_name VARCHAR(80) NOT NULL,
    threshold_value DECIMAL(22, 6) NOT NULL,
    lookup_days INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (appid, modelid, region, threshold_name),
    CONSTRAINT fk_surveillance_model_threshold_config
        FOREIGN KEY (appid, modelid, region)
        REFERENCES surveillance_model_config (appid, modelid, region)
);

CREATE TABLE ficc_wash_alert_history (
    alert_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_fingerprint CHAR(64) NOT NULL,
    alert_id VARCHAR(80) NOT NULL,
    request_id BIGINT NOT NULL,
    appid INT NOT NULL,
    modelid INT NOT NULL,
    region VARCHAR(10) NOT NULL,
    alert_type VARCHAR(80) NOT NULL,
    match_type VARCHAR(80) NOT NULL,
    business_date DATE NOT NULL,
    first_trade_date DATE NOT NULL,
    last_trade_date DATE NOT NULL,
    related_trade_ids VARCHAR(1000) NOT NULL,
    alert_business_key_hash CHAR(64) NOT NULL,
    trade_date DATE NOT NULL,
    asset_class VARCHAR(50) NOT NULL,
    instrument_id VARCHAR(80) NOT NULL,
    maturity_date DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    trader_id VARCHAR(500) NOT NULL,
    counterparty_id VARCHAR(500) NOT NULL,
    alert_payload LONGTEXT NOT NULL,
    dispatch_status VARCHAR(30) NOT NULL DEFAULT 'DISPATCHED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ficc_wash_alert_history_fingerprint (alert_fingerprint),
    INDEX idx_ficc_wash_alert_history_run (appid, modelid, region, business_date),
    INDEX idx_ficc_wash_alert_history_business_key (appid, modelid, region, business_date, alert_business_key_hash),
    INDEX idx_ficc_wash_alert_history_request (request_id),
    CONSTRAINT fk_ficc_wash_alert_history_request
        FOREIGN KEY (request_id)
        REFERENCES surveillance_run_request (request_id),
    CONSTRAINT fk_ficc_wash_alert_history_config
        FOREIGN KEY (appid, modelid, region)
        REFERENCES surveillance_model_config (appid, modelid, region)
);

CREATE TABLE ficc_wash_alert_history_trade (
    alert_history_id BIGINT NOT NULL,
    trade_sequence INT NOT NULL,
    trade_id VARCHAR(50) NOT NULL,
    trade_date DATE NOT NULL,
    trade_timestamp DATETIME NOT NULL,
    asset_class VARCHAR(50) NOT NULL,
    instrument_id VARCHAR(80) NOT NULL,
    maturity DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity DECIMAL(22, 6) NOT NULL,
    price DECIMAL(22, 8) NOT NULL,
    total_amount DECIMAL(30, 8) NOT NULL,
    counterparty_id VARCHAR(80) NOT NULL,
    account_id VARCHAR(80) NOT NULL,
    beneficial_owner VARCHAR(120) NOT NULL,
    trader_id VARCHAR(80) NOT NULL,
    desk VARCHAR(80) NOT NULL,
    book VARCHAR(80) NOT NULL,
    broker VARCHAR(80) NOT NULL,
    trade_role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (alert_history_id, trade_id),
    UNIQUE KEY uk_ficc_wash_alert_history_trade_sequence (alert_history_id, trade_sequence),
    INDEX idx_ficc_wash_alert_history_trade_trade (trade_id),
    INDEX idx_ficc_wash_alert_history_trade_date (trade_date),
    CONSTRAINT fk_ficc_wash_alert_history_trade_history
        FOREIGN KEY (alert_history_id)
        REFERENCES ficc_wash_alert_history (alert_history_id)
);

CREATE TABLE ficc_wash_calibration_alert_history (
    calibration_alert_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_fingerprint CHAR(64) NOT NULL,
    alert_id VARCHAR(80) NOT NULL,
    request_id BIGINT NOT NULL,
    appid INT NOT NULL,
    modelid INT NOT NULL,
    region VARCHAR(10) NOT NULL,
    alert_type VARCHAR(80) NOT NULL,
    match_type VARCHAR(80) NOT NULL,
    business_date DATE NOT NULL,
    first_trade_date DATE NOT NULL,
    last_trade_date DATE NOT NULL,
    related_trade_ids VARCHAR(1000) NOT NULL,
    alert_business_key_hash CHAR(64) NOT NULL,
    trade_date DATE NOT NULL,
    asset_class VARCHAR(50) NOT NULL,
    instrument_id VARCHAR(80) NOT NULL,
    maturity_date DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    trader_id VARCHAR(500) NOT NULL,
    counterparty_id VARCHAR(500) NOT NULL,
    alert_payload LONGTEXT NOT NULL,
    one_time_min_total_amount DECIMAL(22, 6) NOT NULL,
    cumulative_min_total_amount DECIMAL(22, 6) NOT NULL,
    quantity_tolerance_percent DECIMAL(22, 6) NOT NULL,
    total_amount_tolerance_percent DECIMAL(22, 6) NOT NULL,
    cumulative_lookup_days INT NOT NULL,
    dispatch_status VARCHAR(30) NOT NULL DEFAULT 'DISPATCHED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ficc_wash_calibration_alert_history_request_fingerprint (request_id, alert_fingerprint),
    INDEX idx_ficc_wash_calibration_alert_history_run (appid, modelid, region, business_date),
    INDEX idx_ficc_wash_calibration_alert_history_business_key (appid, modelid, region, business_date, alert_business_key_hash),
    INDEX idx_ficc_wash_calibration_alert_history_request (request_id),
    CONSTRAINT fk_ficc_wash_calibration_alert_history_request
        FOREIGN KEY (request_id)
        REFERENCES surveillance_run_request (request_id),
    CONSTRAINT fk_ficc_wash_calibration_alert_history_config
        FOREIGN KEY (appid, modelid, region)
        REFERENCES surveillance_model_config (appid, modelid, region)
);

CREATE TABLE ficc_wash_calibration_alert_history_trade (
    calibration_alert_history_id BIGINT NOT NULL,
    trade_sequence INT NOT NULL,
    trade_id VARCHAR(50) NOT NULL,
    trade_date DATE NOT NULL,
    trade_timestamp DATETIME NOT NULL,
    asset_class VARCHAR(50) NOT NULL,
    instrument_id VARCHAR(80) NOT NULL,
    maturity DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity DECIMAL(22, 6) NOT NULL,
    price DECIMAL(22, 8) NOT NULL,
    total_amount DECIMAL(30, 8) NOT NULL,
    counterparty_id VARCHAR(80) NOT NULL,
    account_id VARCHAR(80) NOT NULL,
    beneficial_owner VARCHAR(120) NOT NULL,
    trader_id VARCHAR(80) NOT NULL,
    desk VARCHAR(80) NOT NULL,
    book VARCHAR(80) NOT NULL,
    broker VARCHAR(80) NOT NULL,
    trade_role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (calibration_alert_history_id, trade_id),
    UNIQUE KEY uk_ficc_wash_calibration_alert_history_trade_sequence (calibration_alert_history_id, trade_sequence),
    INDEX idx_ficc_wash_calibration_alert_history_trade_trade (trade_id),
    INDEX idx_ficc_wash_calibration_alert_history_trade_date (trade_date),
    CONSTRAINT fk_ficc_wash_calibration_alert_history_trade_history
        FOREIGN KEY (calibration_alert_history_id)
        REFERENCES ficc_wash_calibration_alert_history (calibration_alert_history_id)
);

INSERT INTO surveillance_model_master (
    appid,
    region,
    name,
    model_code,
    model_name,
    model_class_name,
    description,
    enabled
) VALUES
(1, 'NAMR', 'NAMR FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'North America FICC_WASH Model.', TRUE),
(2, 'EMEA', 'EMEA FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'Europe, Middle East, and Africa FICC_WASH Model.', TRUE),
(3, 'APAC', 'APAC FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'Asia Pacific FICC_WASH Model.', TRUE),
(4, 'NAMRC', 'NAMRC FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'North America FICC_WASH Calibration Model.', TRUE),
(5, 'EMEAC', 'EMEAC FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'Europe, Middle East, and Africa FICC_WASH Calibration Model.', TRUE),
(6, 'APACC', 'APACC FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'Asia Pacific FICC_WASH Calibration Model.', TRUE);

INSERT INTO surveillance_model_config (
    appid,
    modelid,
    region,
    enabled
) VALUES
(1, 1, 'NAMR', TRUE),
(2, 1, 'EMEA', TRUE),
(3, 1, 'APAC', TRUE),
(4, 1, 'NAMRC', TRUE),
(5, 1, 'EMEAC', TRUE),
(6, 1, 'APACC', TRUE);

INSERT INTO surveillance_run_request (
    appid,
    region,
    business_date,
    requested_by
) VALUES
(1, 'NAMR', '2026-06-01', 'portfolio-seed'),
(1, 'NAMR', '2026-06-02', 'portfolio-seed'),
(1, 'NAMR', '2026-06-03', 'portfolio-seed'),
(1, 'NAMR', '2026-06-04', 'portfolio-seed'),
(1, 'NAMR', '2026-06-05', 'portfolio-seed'),
(2, 'EMEA', '2026-06-01', 'portfolio-seed'),
(2, 'EMEA', '2026-06-02', 'portfolio-seed'),
(2, 'EMEA', '2026-06-03', 'portfolio-seed'),
(2, 'EMEA', '2026-06-04', 'portfolio-seed'),
(2, 'EMEA', '2026-06-05', 'portfolio-seed'),
(3, 'APAC', '2026-06-01', 'portfolio-seed'),
(3, 'APAC', '2026-06-02', 'portfolio-seed'),
(3, 'APAC', '2026-06-03', 'portfolio-seed'),
(3, 'APAC', '2026-06-04', 'portfolio-seed'),
(3, 'APAC', '2026-06-05', 'portfolio-seed');

INSERT INTO surveillance_model_threshold (
    appid,
    modelid,
    region,
    threshold_name,
    threshold_value,
    lookup_days,
    enabled
) VALUES
(1, 1, 'NAMR', 'ONE_TIME_MIN_TOTAL_AMOUNT', 100000000.000000, 0, TRUE),
(1, 1, 'NAMR', 'CUMULATIVE_MIN_TOTAL_AMOUNT', 5000000.000000, 4, TRUE),
(1, 1, 'NAMR', 'QUANTITY_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(1, 1, 'NAMR', 'TOTAL_AMOUNT_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(2, 1, 'EMEA', 'ONE_TIME_MIN_TOTAL_AMOUNT', 100000000.000000, 0, TRUE),
(2, 1, 'EMEA', 'CUMULATIVE_MIN_TOTAL_AMOUNT', 5000000.000000, 4, TRUE),
(2, 1, 'EMEA', 'QUANTITY_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(2, 1, 'EMEA', 'TOTAL_AMOUNT_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(3, 1, 'APAC', 'ONE_TIME_MIN_TOTAL_AMOUNT', 100000000.000000, 0, TRUE),
(3, 1, 'APAC', 'CUMULATIVE_MIN_TOTAL_AMOUNT', 5000000.000000, 4, TRUE),
(3, 1, 'APAC', 'QUANTITY_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
(3, 1, 'APAC', 'TOTAL_AMOUNT_TOLERANCE_PERCENT', 5.000000, 0, TRUE),
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
(6, 1, 'APACC', 'TOTAL_AMOUNT_TOLERANCE_PERCENT', 5.000000, 0, TRUE);

CREATE TABLE ficc_trade (
    trade_id VARCHAR(50) PRIMARY KEY,
    region VARCHAR(20) NOT NULL,
    trade_date DATE NOT NULL,
    trade_timestamp DATETIME NOT NULL,
    asset_class VARCHAR(50) NOT NULL,
    instrument_id VARCHAR(80) NOT NULL,
    maturity DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    side VARCHAR(4) NOT NULL,
    quantity DECIMAL(22, 6) NOT NULL,
    price DECIMAL(22, 8) NOT NULL,
    counterparty_id VARCHAR(80) NOT NULL,
    account_id VARCHAR(80) NOT NULL,
    beneficial_owner VARCHAR(120) NOT NULL,
    trader_id VARCHAR(80) NOT NULL,
    desk VARCHAR(80) NOT NULL,
    book VARCHAR(80) NOT NULL,
    broker VARCHAR(80) NOT NULL,
    INDEX idx_ficc_trade_region_date (region, trade_date),
    INDEX idx_ficc_trade_candidate (asset_class, instrument_id, maturity, currency, side)
);

INSERT INTO ficc_trade (
    trade_id,
    region,
    trade_date,
    trade_timestamp,
    asset_class,
    instrument_id,
    maturity,
    currency,
    side,
    quantity,
    price,
    counterparty_id,
    account_id,
    beneficial_owner,
    trader_id,
    desk,
    book,
    broker
) VALUES
('T-NAMR-0601-OT-001', 'NAMR', '2026-06-01', '2026-06-01 09:15:00', 'Fixed Income', 'UST-2Y-0601', '2028-06-30', 'USD', 'BUY', 2000000, 100.2500, 'CP-OT-0601', 'ACCT-OT-0601-A', 'Delta Rates Master Fund', 'TRDR-104', 'Rates', 'GOVT-OT-0601', 'BRKR-NY-1'),
('T-NAMR-0601-OT-002', 'NAMR', '2026-06-01', '2026-06-01 09:15:04', 'Fixed Income', 'UST-2Y-0601', '2028-06-30', 'USD', 'SELL', 1990000, 100.2510, 'CP-OT-0601', 'ACCT-OT-0601-B', 'Delta Rates Master Fund', 'TRDR-104', 'Rates', 'GOVT-OT-0601', 'BRKR-NY-1'),
('T-NAMR-0601-CUM-BUY-1', 'NAMR', '2026-06-01', '2026-06-01 10:00:00', 'Currencies', 'EUR/USD-CUM-0601', '2026-06-10', 'USD', 'BUY', 3000000, 1.10000, 'CP-CUM-0601', 'ACCT-CUM-0601-A', 'Aurora Macro Fund', 'TRDR-204', 'FX', 'FX-CUM-0601-A', 'BRKR-LON-1'),
('T-NAMR-0601-CUM-BUY-2', 'NAMR', '2026-06-01', '2026-06-01 10:00:11', 'Currencies', 'EUR/USD-CUM-0601', '2026-06-10', 'USD', 'BUY', 2000000, 1.10000, 'CP-CUM-0601', 'ACCT-CUM-0601-A', 'Aurora Macro Fund', 'TRDR-204', 'FX', 'FX-CUM-0601-A', 'BRKR-LON-1'),
('T-NAMR-0601-CUM-SELL-1', 'NAMR', '2026-06-01', '2026-06-01 10:00:25', 'Currencies', 'EUR/USD-CUM-0601', '2026-06-10', 'USD', 'SELL', 2500000, 1.10020, 'CP-CUM-0601', 'ACCT-CUM-0601-B', 'Aurora Macro Fund', 'TRDR-205', 'FX', 'FX-CUM-0601-B', 'BRKR-LON-1'),
('T-NAMR-0601-CUM-SELL-2', 'NAMR', '2026-06-01', '2026-06-01 10:00:39', 'Currencies', 'EUR/USD-CUM-0601', '2026-06-10', 'USD', 'SELL', 2450000, 1.10020, 'CP-CUM-0601', 'ACCT-CUM-0601-B', 'Aurora Macro Fund', 'TRDR-205', 'FX', 'FX-CUM-0601-B', 'BRKR-LON-1'),
('T-NAMR-0601-NA-001', 'NAMR', '2026-06-01', '2026-06-01 13:20:00', 'Commodities', 'WTI-DEC26-NA-0601', '2026-12-20', 'USD', 'BUY', 1000, 75.0000, 'CP-NA-0601', 'ACCT-NA-0601-A', 'Physical Energy Fund', 'TRDR-304', 'Commodities', 'ENERGY-NA-0601', 'BRKR-HOU-1'),
('T-NAMR-0601-NA-002', 'NAMR', '2026-06-01', '2026-06-01 13:27:00', 'Commodities', 'WTI-DEC26-NA-0601', '2026-12-20', 'USD', 'SELL', 1800, 75.5000, 'CP-NA-0601', 'ACCT-NA-0601-B', 'Physical Energy Fund', 'TRDR-305', 'Commodities', 'ENERGY-NA-0601', 'BRKR-HOU-1'),
('T-NAMR-0602-CUM-BUY-1', 'NAMR', '2026-06-01', '2026-06-01 15:10:00', 'Currencies', 'GBP/USD-CUM-0602', '2026-06-12', 'USD', 'BUY', 2000000, 1.26000, 'CP-CUM-0602', 'ACCT-CUM-0602-A', 'Boreal Macro Fund', 'TRDR-215', 'FX', 'FX-CUM-0602-A', 'BRKR-LON-2'),
('T-NAMR-0602-OT-001', 'NAMR', '2026-06-02', '2026-06-02 09:18:00', 'Fixed Income', 'CORP-ALPHA-2029', '2029-09-15', 'USD', 'BUY', 2500000, 101.2500, 'CP-OT-0602', 'ACCT-OT-0602-A', 'Orion Credit Fund', 'TRDR-105', 'Credit', 'CREDIT-OT-0602', 'BRKR-NY-2'),
('T-NAMR-0602-OT-002', 'NAMR', '2026-06-02', '2026-06-02 09:18:05', 'Fixed Income', 'CORP-ALPHA-2029', '2029-09-15', 'USD', 'SELL', 2490000, 101.2510, 'CP-OT-0602', 'ACCT-OT-0602-B', 'Orion Credit Fund', 'TRDR-105', 'Credit', 'CREDIT-OT-0602', 'BRKR-NY-2'),
('T-NAMR-0602-CUM-BUY-2', 'NAMR', '2026-06-02', '2026-06-02 10:05:00', 'Currencies', 'GBP/USD-CUM-0602', '2026-06-12', 'USD', 'BUY', 3000000, 1.26000, 'CP-CUM-0602', 'ACCT-CUM-0602-A', 'Boreal Macro Fund', 'TRDR-215', 'FX', 'FX-CUM-0602-A', 'BRKR-LON-2'),
('T-NAMR-0602-CUM-SELL-1', 'NAMR', '2026-06-02', '2026-06-02 10:05:18', 'Currencies', 'GBP/USD-CUM-0602', '2026-06-12', 'USD', 'SELL', 2500000, 1.26010, 'CP-CUM-0602', 'ACCT-CUM-0602-B', 'Boreal Macro Fund', 'TRDR-216', 'FX', 'FX-CUM-0602-B', 'BRKR-LON-2'),
('T-NAMR-0602-CUM-SELL-2', 'NAMR', '2026-06-02', '2026-06-02 10:05:36', 'Currencies', 'GBP/USD-CUM-0602', '2026-06-12', 'USD', 'SELL', 2450000, 1.26010, 'CP-CUM-0602', 'ACCT-CUM-0602-B', 'Boreal Macro Fund', 'TRDR-216', 'FX', 'FX-CUM-0602-B', 'BRKR-LON-2'),
('T-NAMR-0602-NA-001', 'NAMR', '2026-06-02', '2026-06-02 14:00:00', 'Fixed Income', 'UST-5Y-NA-0602', '2031-06-30', 'USD', 'BUY', 5000000, 99.0000, 'CP-NA-0602', 'ACCT-NA-0602-A', 'Mismatch Rates Fund', 'TRDR-315', 'Rates', 'RATES-NA-0602', 'BRKR-CHI-1'),
('T-NAMR-0602-NA-002', 'NAMR', '2026-06-02', '2026-06-02 14:00:09', 'Fixed Income', 'UST-5Y-NA-0602', '2031-06-30', 'USD', 'SELL', 4000000, 99.0000, 'CP-NA-0602', 'ACCT-NA-0602-B', 'Mismatch Rates Fund', 'TRDR-316', 'Rates', 'RATES-NA-0602', 'BRKR-CHI-1'),
('T-NAMR-0603-CUM-BUY-1', 'NAMR', '2026-06-02', '2026-06-02 15:05:00', 'Currencies', 'AUD/USD-CUM-0603', '2026-06-13', 'USD', 'BUY', 5000000, 0.66000, 'CP-CUM-0603', 'ACCT-CUM-0603-A', 'Cascade Macro Fund', 'TRDR-226', 'FX', 'FX-CUM-0603-A', 'BRKR-SYD-1'),
('T-NAMR-0603-CUM-BUY-2', 'NAMR', '2026-06-02', '2026-06-02 15:05:12', 'Currencies', 'AUD/USD-CUM-0603', '2026-06-13', 'USD', 'BUY', 4000000, 0.66000, 'CP-CUM-0603', 'ACCT-CUM-0603-A', 'Cascade Macro Fund', 'TRDR-226', 'FX', 'FX-CUM-0603-A', 'BRKR-SYD-1'),
('T-NAMR-0603-OT-001', 'NAMR', '2026-06-03', '2026-06-03 09:21:00', 'Fixed Income', 'UST-5Y-0603', '2031-06-30', 'USD', 'BUY', 5000000, 98.5000, 'CP-OT-0603', 'ACCT-OT-0603-A', 'Northstar Rates Fund', 'TRDR-106', 'Rates', 'GOVT-OT-0603', 'BRKR-NY-3'),
('T-NAMR-0603-OT-002', 'NAMR', '2026-06-03', '2026-06-03 09:21:04', 'Fixed Income', 'UST-5Y-0603', '2031-06-30', 'USD', 'SELL', 4980000, 98.5010, 'CP-OT-0603', 'ACCT-OT-0603-B', 'Northstar Rates Fund', 'TRDR-106', 'Rates', 'GOVT-OT-0603', 'BRKR-NY-3'),
('T-NAMR-0603-CUM-SELL-1', 'NAMR', '2026-06-03', '2026-06-03 10:12:00', 'Currencies', 'AUD/USD-CUM-0603', '2026-06-13', 'USD', 'SELL', 4500000, 0.66010, 'CP-CUM-0603', 'ACCT-CUM-0603-B', 'Cascade Macro Fund', 'TRDR-227', 'FX', 'FX-CUM-0603-B', 'BRKR-SYD-1'),
('T-NAMR-0603-CUM-SELL-2', 'NAMR', '2026-06-03', '2026-06-03 10:12:17', 'Currencies', 'AUD/USD-CUM-0603', '2026-06-13', 'USD', 'SELL', 4400000, 0.66010, 'CP-CUM-0603', 'ACCT-CUM-0603-B', 'Cascade Macro Fund', 'TRDR-227', 'FX', 'FX-CUM-0603-B', 'BRKR-SYD-1'),
('T-NAMR-0603-NA-001', 'NAMR', '2026-06-03', '2026-06-03 13:15:00', 'Commodities', 'XAU-AUG26-NA-0603', '2026-08-31', 'USD', 'BUY', 1000, 2350.0000, 'CP-NA-0603-A', 'ACCT-NA-0603-A', 'Metals Opportunity Fund', 'TRDR-326', 'Commodities', 'METALS-NA-0603', 'BRKR-CHI-2'),
('T-NAMR-0603-NA-002', 'NAMR', '2026-06-03', '2026-06-03 13:16:00', 'Commodities', 'XAU-AUG26-NA-0603', '2026-08-31', 'USD', 'SELL', 1000, 2350.0000, 'CP-NA-0603-B', 'ACCT-NA-0603-B', 'Metals Opportunity Fund', 'TRDR-327', 'Commodities', 'METALS-NA-0603', 'BRKR-CHI-2'),
('T-NAMR-0604-CUM-BUY-1', 'NAMR', '2026-06-03', '2026-06-03 15:45:00', 'Currencies', 'NZD/USD-CUM-0604', '2026-06-14', 'USD', 'BUY', 5000000, 0.61000, 'CP-CUM-0604', 'ACCT-CUM-0604-A', 'Dawn Macro Fund', 'TRDR-237', 'FX', 'FX-CUM-0604-A', 'BRKR-SYD-2'),
('T-NAMR-0604-CUM-BUY-2', 'NAMR', '2026-06-03', '2026-06-03 15:45:15', 'Currencies', 'NZD/USD-CUM-0604', '2026-06-14', 'USD', 'BUY', 4000000, 0.61000, 'CP-CUM-0604', 'ACCT-CUM-0604-A', 'Dawn Macro Fund', 'TRDR-237', 'FX', 'FX-CUM-0604-A', 'BRKR-SYD-2'),
('T-NAMR-0604-OT-001', 'NAMR', '2026-06-04', '2026-06-04 09:24:00', 'Fixed Income', 'AGENCY-MBS-2031', '2031-01-25', 'USD', 'BUY', 1500000, 101.1000, 'CP-OT-0604', 'ACCT-OT-0604-A', 'Helios Income Fund', 'TRDR-107', 'Securitized Products', 'MBS-OT-0604', 'BRKR-NY-4'),
('T-NAMR-0604-OT-002', 'NAMR', '2026-06-04', '2026-06-04 09:24:05', 'Fixed Income', 'AGENCY-MBS-2031', '2031-01-25', 'USD', 'SELL', 1490000, 101.1010, 'CP-OT-0604', 'ACCT-OT-0604-B', 'Helios Income Fund', 'TRDR-107', 'Securitized Products', 'MBS-OT-0604', 'BRKR-NY-4'),
('T-NAMR-0604-CUM-SELL-1', 'NAMR', '2026-06-04', '2026-06-04 10:18:00', 'Currencies', 'NZD/USD-CUM-0604', '2026-06-14', 'USD', 'SELL', 4500000, 0.61010, 'CP-CUM-0604', 'ACCT-CUM-0604-B', 'Dawn Macro Fund', 'TRDR-238', 'FX', 'FX-CUM-0604-B', 'BRKR-SYD-2'),
('T-NAMR-0604-CUM-SELL-2', 'NAMR', '2026-06-04', '2026-06-04 10:18:19', 'Currencies', 'NZD/USD-CUM-0604', '2026-06-14', 'USD', 'SELL', 4400000, 0.61010, 'CP-CUM-0604', 'ACCT-CUM-0604-B', 'Dawn Macro Fund', 'TRDR-238', 'FX', 'FX-CUM-0604-B', 'BRKR-SYD-2'),
('T-NAMR-0604-NA-001', 'NAMR', '2026-06-04', '2026-06-04 13:05:00', 'Fixed Income', 'CORP-BETA-NA-0604', '2030-03-15', 'USD', 'BUY', 2000000, 100.5000, 'CP-NA-0604-A', 'ACCT-NA-0604-A', 'Pair Break Credit Fund', 'TRDR-337', 'Credit', 'CREDIT-NA-0604', 'BRKR-NY-5'),
('T-NAMR-0604-NA-002', 'NAMR', '2026-06-04', '2026-06-04 13:05:06', 'Fixed Income', 'CORP-BETA-NA-0604', '2030-03-15', 'USD', 'SELL', 1990000, 100.5000, 'CP-NA-0604-B', 'ACCT-NA-0604-B', 'Pair Break Credit Fund', 'TRDR-338', 'Credit', 'CREDIT-NA-0604', 'BRKR-NY-5'),
('T-NAMR-UST-001', 'NAMR', '2026-06-05', '2026-06-05 09:30:00', 'Fixed Income', 'UST-10Y', '2036-05-15', 'USD', 'BUY', 10000000, 99.8125, 'CP-ALPHA', 'ACCT-RATES-ALPHA', 'Alpha Capital Master Fund', 'TRDR-17', 'Rates', 'GOVT-RATES-A', 'BRKR-NY-1'),
('T-NAMR-UST-002', 'NAMR', '2026-06-05', '2026-06-05 09:30:03', 'Fixed Income', 'UST-10Y', '2036-05-15', 'USD', 'SELL', 9980000, 99.8130, 'CP-ALPHA', 'ACCT-RATES-ALPHA', 'Alpha Capital Master Fund', 'TRDR-17', 'Rates', 'GOVT-RATES-A', 'BRKR-NY-1'),
('T-NAMR-FX-001', 'NAMR', '2026-06-03', '2026-06-03 09:42:00', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'BUY', 3000000, 1.08450, 'CP-BETA', 'ACCT-FX-BETA', 'Beta Macro Fund', 'TRDR-42', 'FX', 'FX-SPOT-A', 'BRKR-LON-2'),
('T-NAMR-FX-002', 'NAMR', '2026-06-04', '2026-06-04 09:42:10', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'BUY', 2000000, 1.08450, 'CP-BETA', 'ACCT-FX-BETA', 'Beta Macro Fund', 'TRDR-42', 'FX', 'FX-SPOT-A', 'BRKR-LON-2'),
('T-NAMR-FX-003', 'NAMR', '2026-06-05', '2026-06-05 09:42:20', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'SELL', 2500000, 1.08495, 'CP-BETA', 'ACCT-FX-OMEGA', 'Omega Global Fund', 'TRDR-42', 'Macro FX', 'FX-SPOT-B', 'BRKR-NY-9'),
('T-NAMR-FX-004', 'NAMR', '2026-06-05', '2026-06-05 09:42:30', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'SELL', 2450000, 1.08495, 'CP-BETA', 'ACCT-FX-OMEGA', 'Omega Global Fund', 'TRDR-42', 'Macro FX', 'FX-SPOT-B', 'BRKR-NY-9'),
('T-NAMR-CMD-001', 'NAMR', '2026-06-05', '2026-06-05 10:30:00', 'Commodities', 'WTI-DEC26', '2026-12-20', 'USD', 'BUY', 1000, 75.00, 'CP-GAMMA', 'ACCT-CMD-GAMMA', 'Gamma Energy Fund', 'TRDR-88', 'Commodities', 'ENERGY-A', 'BRKR-HOU-4'),
('T-NAMR-CMD-002', 'NAMR', '2026-06-05', '2026-06-05 10:40:00', 'Commodities', 'WTI-DEC26', '2026-12-20', 'USD', 'SELL', 1200, 76.00, 'CP-DELTA', 'ACCT-CMD-DELTA', 'Delta Physical Trading', 'TRDR-91', 'Commodities', 'ENERGY-B', 'BRKR-HOU-8'),
('T-UST-001', 'APAC', '2026-06-05', '2026-06-05 09:30:00', 'Fixed Income', 'UST-10Y', '2036-05-15', 'USD', 'BUY', 10000000, 99.8125, 'CP-ALPHA', 'ACCT-RATES-ALPHA', 'Alpha Capital Master Fund', 'TRDR-17', 'Rates', 'GOVT-RATES-A', 'BRKR-NY-1'),
('T-UST-002', 'APAC', '2026-06-05', '2026-06-05 09:30:03', 'Fixed Income', 'UST-10Y', '2036-05-15', 'USD', 'SELL', 9980000, 99.8130, 'CP-ALPHA', 'ACCT-RATES-ALPHA', 'Alpha Capital Master Fund', 'TRDR-17', 'Rates', 'GOVT-RATES-A', 'BRKR-NY-1'),
('T-FX-001', 'APAC', '2026-06-03', '2026-06-03 09:42:00', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'BUY', 3000000, 1.08450, 'CP-BETA', 'ACCT-FX-BETA', 'Beta Macro Fund', 'TRDR-42', 'FX', 'FX-SPOT-A', 'BRKR-LON-2'),
('T-FX-002', 'APAC', '2026-06-04', '2026-06-04 09:42:10', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'BUY', 2000000, 1.08450, 'CP-BETA', 'ACCT-FX-BETA', 'Beta Macro Fund', 'TRDR-42', 'FX', 'FX-SPOT-A', 'BRKR-LON-2'),
('T-FX-003', 'APAC', '2026-06-05', '2026-06-05 09:42:20', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'SELL', 2500000, 1.08495, 'CP-BETA', 'ACCT-FX-OMEGA', 'Omega Global Fund', 'TRDR-42', 'Macro FX', 'FX-SPOT-B', 'BRKR-NY-9'),
('T-FX-004', 'APAC', '2026-06-05', '2026-06-05 09:42:30', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'SELL', 2450000, 1.08495, 'CP-BETA', 'ACCT-FX-OMEGA', 'Omega Global Fund', 'TRDR-42', 'Macro FX', 'FX-SPOT-B', 'BRKR-NY-9'),
('T-CMD-001', 'APAC', '2026-06-05', '2026-06-05 10:30:00', 'Commodities', 'WTI-DEC26', '2026-12-20', 'USD', 'BUY', 1000, 75.00, 'CP-GAMMA', 'ACCT-CMD-GAMMA', 'Gamma Energy Fund', 'TRDR-88', 'Commodities', 'ENERGY-A', 'BRKR-HOU-4'),
('T-CMD-002', 'APAC', '2026-06-05', '2026-06-05 10:40:00', 'Commodities', 'WTI-DEC26', '2026-12-20', 'USD', 'SELL', 1200, 76.00, 'CP-DELTA', 'ACCT-CMD-DELTA', 'Delta Physical Trading', 'TRDR-91', 'Commodities', 'ENERGY-B', 'BRKR-HOU-8');

INSERT INTO ficc_trade (
    trade_id,
    region,
    trade_date,
    trade_timestamp,
    asset_class,
    instrument_id,
    maturity,
    currency,
    side,
    quantity,
    price,
    counterparty_id,
    account_id,
    beneficial_owner,
    trader_id,
    desk,
    book,
    broker
) VALUES
('T-NAMR-CAL-0605-OT-001', 'NAMR', '2026-06-05', '2026-06-05 11:05:00', 'Fixed Income', 'UST-CAL-MINI-10Y', '2036-05-15', 'USD', 'BUY', 750000, 99.5000, 'CP-CAL-NAMR-1', 'ACCT-CAL-NAMR-A', 'Calibration Rates Fund', 'TRDR-CAL-1', 'Rates', 'CAL-RATES-A', 'BRKR-NY-CAL'),
('T-NAMR-CAL-0605-OT-002', 'NAMR', '2026-06-05', '2026-06-05 11:05:04', 'Fixed Income', 'UST-CAL-MINI-10Y', '2036-05-15', 'USD', 'SELL', 748000, 99.5200, 'CP-CAL-NAMR-1', 'ACCT-CAL-NAMR-B', 'Calibration Rates Fund', 'TRDR-CAL-2', 'Rates', 'CAL-RATES-B', 'BRKR-NY-CAL'),
('T-NAMR-CAL-0605-TOL-001', 'NAMR', '2026-06-05', '2026-06-05 11:12:00', 'Fixed Income', 'CORP-CAL-TOL-2031', '2031-10-15', 'USD', 'BUY', 900000, 88.0000, 'CP-CAL-NAMR-2', 'ACCT-CAL-NAMR-C', 'Calibration Credit Fund', 'TRDR-CAL-3', 'Credit', 'CAL-CREDIT-A', 'BRKR-NY-CAL'),
('T-NAMR-CAL-0605-TOL-002', 'NAMR', '2026-06-05', '2026-06-05 11:12:05', 'Fixed Income', 'CORP-CAL-TOL-2031', '2031-10-15', 'USD', 'SELL', 830000, 88.1000, 'CP-CAL-NAMR-2', 'ACCT-CAL-NAMR-D', 'Calibration Credit Fund', 'TRDR-CAL-4', 'Credit', 'CAL-CREDIT-B', 'BRKR-NY-CAL'),
('T-NAMR-CAL-CUM-0605-BUY-1', 'NAMR', '2026-06-04', '2026-06-04 15:00:00', 'Currencies', 'USD/CAD-CAL-CUM', '2026-06-12', 'USD', 'BUY', 2000000, 1.00000, 'CP-CAL-NAMR-3', 'ACCT-CAL-NAMR-E', 'Calibration Macro Fund', 'TRDR-CAL-5', 'FX', 'CAL-FX-A', 'BRKR-TOR-CAL'),
('T-NAMR-CAL-CUM-0605-BUY-2', 'NAMR', '2026-06-05', '2026-06-05 08:45:00', 'Currencies', 'USD/CAD-CAL-CUM', '2026-06-12', 'USD', 'BUY', 2000000, 1.00000, 'CP-CAL-NAMR-3', 'ACCT-CAL-NAMR-E', 'Calibration Macro Fund', 'TRDR-CAL-5', 'FX', 'CAL-FX-A', 'BRKR-TOR-CAL'),
('T-NAMR-CAL-CUM-0605-SELL-1', 'NAMR', '2026-06-05', '2026-06-05 08:46:00', 'Currencies', 'USD/CAD-CAL-CUM', '2026-06-12', 'USD', 'SELL', 1950000, 1.00010, 'CP-CAL-NAMR-3', 'ACCT-CAL-NAMR-F', 'Calibration Macro Fund', 'TRDR-CAL-6', 'FX', 'CAL-FX-B', 'BRKR-TOR-CAL'),
('T-NAMR-CAL-CUM-0605-SELL-2', 'NAMR', '2026-06-05', '2026-06-05 08:46:10', 'Currencies', 'USD/CAD-CAL-CUM', '2026-06-12', 'USD', 'SELL', 1950000, 1.00010, 'CP-CAL-NAMR-3', 'ACCT-CAL-NAMR-F', 'Calibration Macro Fund', 'TRDR-CAL-6', 'FX', 'CAL-FX-B', 'BRKR-TOR-CAL'),
('T-EMEA-0601-OT-001', 'EMEA', '2026-06-01', '2026-06-01 08:20:00', 'Fixed Income', 'BUND-2Y-0601', '2028-06-30', 'EUR', 'BUY', 1300000, 101.2000, 'CP-EMEA-OT-0601', 'ACCT-EMEA-OT-A', 'Rhine Rates Fund', 'TRDR-E101', 'Rates', 'EMEA-RATES-A', 'BRKR-FRA-1'),
('T-EMEA-0601-OT-002', 'EMEA', '2026-06-01', '2026-06-01 08:20:04', 'Fixed Income', 'BUND-2Y-0601', '2028-06-30', 'EUR', 'SELL', 1295000, 101.2050, 'CP-EMEA-OT-0601', 'ACCT-EMEA-OT-B', 'Rhine Rates Fund', 'TRDR-E102', 'Rates', 'EMEA-RATES-B', 'BRKR-FRA-1'),
('T-EMEA-0602-OT-001', 'EMEA', '2026-06-02', '2026-06-02 08:22:00', 'Fixed Income', 'GILT-5Y-0602', '2031-06-30', 'GBP', 'BUY', 1400000, 100.7500, 'CP-EMEA-OT-0602', 'ACCT-EMEA-OT-C', 'Thames Income Fund', 'TRDR-E103', 'Rates', 'EMEA-RATES-C', 'BRKR-LON-1'),
('T-EMEA-0602-OT-002', 'EMEA', '2026-06-02', '2026-06-02 08:22:05', 'Fixed Income', 'GILT-5Y-0602', '2031-06-30', 'GBP', 'SELL', 1390000, 100.7600, 'CP-EMEA-OT-0602', 'ACCT-EMEA-OT-D', 'Thames Income Fund', 'TRDR-E104', 'Rates', 'EMEA-RATES-D', 'BRKR-LON-1'),
('T-EMEA-0603-OT-001', 'EMEA', '2026-06-03', '2026-06-03 08:24:00', 'Fixed Income', 'OAT-7Y-0603', '2033-06-30', 'EUR', 'BUY', 1500000, 99.9000, 'CP-EMEA-OT-0603', 'ACCT-EMEA-OT-E', 'Seine Credit Fund', 'TRDR-E105', 'Credit', 'EMEA-CREDIT-A', 'BRKR-PAR-1'),
('T-EMEA-0603-OT-002', 'EMEA', '2026-06-03', '2026-06-03 08:24:04', 'Fixed Income', 'OAT-7Y-0603', '2033-06-30', 'EUR', 'SELL', 1495000, 99.9050, 'CP-EMEA-OT-0603', 'ACCT-EMEA-OT-F', 'Seine Credit Fund', 'TRDR-E106', 'Credit', 'EMEA-CREDIT-B', 'BRKR-PAR-1'),
('T-EMEA-0604-OT-001', 'EMEA', '2026-06-04', '2026-06-04 08:26:00', 'Fixed Income', 'BTP-10Y-0604', '2036-06-30', 'EUR', 'BUY', 1600000, 98.7500, 'CP-EMEA-OT-0604', 'ACCT-EMEA-OT-G', 'Alpine Macro Fund', 'TRDR-E107', 'Rates', 'EMEA-RATES-E', 'BRKR-MIL-1'),
('T-EMEA-0604-OT-002', 'EMEA', '2026-06-04', '2026-06-04 08:26:05', 'Fixed Income', 'BTP-10Y-0604', '2036-06-30', 'EUR', 'SELL', 1590000, 98.7550, 'CP-EMEA-OT-0604', 'ACCT-EMEA-OT-H', 'Alpine Macro Fund', 'TRDR-E108', 'Rates', 'EMEA-RATES-F', 'BRKR-MIL-1'),
('T-EMEA-BUND-0605-OT-001', 'EMEA', '2026-06-05', '2026-06-05 08:30:00', 'Fixed Income', 'BUND-10Y-0605', '2036-06-30', 'EUR', 'BUY', 1500000, 101.0000, 'CP-EMEA-ALPHA', 'ACCT-EMEA-RATES-A', 'Rhine Rates Fund', 'TRDR-E201', 'Rates', 'EMEA-GOVT-A', 'BRKR-FRA-2'),
('T-EMEA-BUND-0605-OT-002', 'EMEA', '2026-06-05', '2026-06-05 08:30:04', 'Fixed Income', 'BUND-10Y-0605', '2036-06-30', 'EUR', 'SELL', 1490000, 101.0050, 'CP-EMEA-ALPHA', 'ACCT-EMEA-RATES-B', 'Rhine Rates Fund', 'TRDR-E202', 'Rates', 'EMEA-GOVT-B', 'BRKR-FRA-2'),
('T-EMEA-FX-0605-BUY-1', 'EMEA', '2026-06-03', '2026-06-03 09:35:00', 'Currencies', 'EUR/CHF-CUM-0605', '2026-06-12', 'EUR', 'BUY', 3500000, 1.02000, 'CP-EMEA-BETA', 'ACCT-EMEA-FX-A', 'Helvetic Macro Fund', 'TRDR-E301', 'FX', 'EMEA-FX-A', 'BRKR-ZRH-1'),
('T-EMEA-FX-0605-BUY-2', 'EMEA', '2026-06-04', '2026-06-04 09:35:10', 'Currencies', 'EUR/CHF-CUM-0605', '2026-06-12', 'EUR', 'BUY', 3000000, 1.02000, 'CP-EMEA-BETA', 'ACCT-EMEA-FX-A', 'Helvetic Macro Fund', 'TRDR-E301', 'FX', 'EMEA-FX-A', 'BRKR-ZRH-1'),
('T-EMEA-FX-0605-SELL-1', 'EMEA', '2026-06-05', '2026-06-05 09:35:20', 'Currencies', 'EUR/CHF-CUM-0605', '2026-06-12', 'EUR', 'SELL', 3250000, 1.02020, 'CP-EMEA-BETA', 'ACCT-EMEA-FX-B', 'Helvetic Macro Fund', 'TRDR-E302', 'FX', 'EMEA-FX-B', 'BRKR-ZRH-1'),
('T-EMEA-FX-0605-SELL-2', 'EMEA', '2026-06-05', '2026-06-05 09:35:35', 'Currencies', 'EUR/CHF-CUM-0605', '2026-06-12', 'EUR', 'SELL', 3200000, 1.02020, 'CP-EMEA-BETA', 'ACCT-EMEA-FX-B', 'Helvetic Macro Fund', 'TRDR-E302', 'FX', 'EMEA-FX-B', 'BRKR-ZRH-1'),
('T-EMEA-GILT-CAL-0605-001', 'EMEA', '2026-06-05', '2026-06-05 10:10:00', 'Fixed Income', 'GILT-CAL-MINI-2031', '2031-06-30', 'GBP', 'BUY', 700000, 98.0000, 'CP-EMEA-CAL-1', 'ACCT-EMEA-CAL-A', 'Calibration Sterling Fund', 'TRDR-E401', 'Rates', 'EMEA-CAL-A', 'BRKR-LON-CAL'),
('T-EMEA-GILT-CAL-0605-002', 'EMEA', '2026-06-05', '2026-06-05 10:10:04', 'Fixed Income', 'GILT-CAL-MINI-2031', '2031-06-30', 'GBP', 'SELL', 698000, 98.0100, 'CP-EMEA-CAL-1', 'ACCT-EMEA-CAL-B', 'Calibration Sterling Fund', 'TRDR-E402', 'Rates', 'EMEA-CAL-B', 'BRKR-LON-CAL'),
('T-EMEA-CREDIT-CAL-0605-001', 'EMEA', '2026-06-05', '2026-06-05 10:18:00', 'Fixed Income', 'EU-CREDIT-CAL-TOL', '2030-09-15', 'EUR', 'BUY', 900000, 90.0000, 'CP-EMEA-CAL-2', 'ACCT-EMEA-CAL-C', 'Calibration Credit Fund', 'TRDR-E403', 'Credit', 'EMEA-CAL-C', 'BRKR-FRA-CAL'),
('T-EMEA-CREDIT-CAL-0605-002', 'EMEA', '2026-06-05', '2026-06-05 10:18:05', 'Fixed Income', 'EU-CREDIT-CAL-TOL', '2030-09-15', 'EUR', 'SELL', 820000, 90.2000, 'CP-EMEA-CAL-2', 'ACCT-EMEA-CAL-D', 'Calibration Credit Fund', 'TRDR-E404', 'Credit', 'EMEA-CAL-D', 'BRKR-FRA-CAL'),
('T-EMEA-NA-0605-001', 'EMEA', '2026-06-05', '2026-06-05 13:05:00', 'Commodities', 'TTF-GAS-CAL-NA', '2026-12-31', 'EUR', 'BUY', 1000, 39.5000, 'CP-EMEA-NA-A', 'ACCT-EMEA-NA-A', 'Energy Basis Fund', 'TRDR-E501', 'Commodities', 'EMEA-CMD-A', 'BRKR-AMS-1'),
('T-EMEA-NA-0605-002', 'EMEA', '2026-06-05', '2026-06-05 13:05:07', 'Commodities', 'TTF-GAS-CAL-NA', '2026-12-31', 'EUR', 'SELL', 1000, 39.5000, 'CP-EMEA-NA-B', 'ACCT-EMEA-NA-B', 'Energy Basis Fund', 'TRDR-E502', 'Commodities', 'EMEA-CMD-B', 'BRKR-AMS-1'),
('T-APAC-0601-OT-001', 'APAC', '2026-06-01', '2026-06-01 09:05:00', 'Fixed Income', 'JGB-2Y-0601', '2028-06-30', 'JPY', 'BUY', 1400000, 100.2000, 'CP-APAC-OT-0601', 'ACCT-APAC-OT-A', 'Sakura Rates Fund', 'TRDR-A101', 'Rates', 'APAC-RATES-A', 'BRKR-TKO-1'),
('T-APAC-0601-OT-002', 'APAC', '2026-06-01', '2026-06-01 09:05:04', 'Fixed Income', 'JGB-2Y-0601', '2028-06-30', 'JPY', 'SELL', 1390000, 100.2050, 'CP-APAC-OT-0601', 'ACCT-APAC-OT-B', 'Sakura Rates Fund', 'TRDR-A102', 'Rates', 'APAC-RATES-B', 'BRKR-TKO-1'),
('T-APAC-0602-OT-001', 'APAC', '2026-06-02', '2026-06-02 09:08:00', 'Fixed Income', 'ACGB-5Y-0602', '2031-06-30', 'AUD', 'BUY', 1250000, 101.3000, 'CP-APAC-OT-0602', 'ACCT-APAC-OT-C', 'Harbour Income Fund', 'TRDR-A103', 'Rates', 'APAC-RATES-C', 'BRKR-SYD-3'),
('T-APAC-0602-OT-002', 'APAC', '2026-06-02', '2026-06-02 09:08:05', 'Fixed Income', 'ACGB-5Y-0602', '2031-06-30', 'AUD', 'SELL', 1240000, 101.3050, 'CP-APAC-OT-0602', 'ACCT-APAC-OT-D', 'Harbour Income Fund', 'TRDR-A104', 'Rates', 'APAC-RATES-D', 'BRKR-SYD-3'),
('T-APAC-0603-OT-001', 'APAC', '2026-06-03', '2026-06-03 09:10:00', 'Fixed Income', 'NZGB-7Y-0603', '2033-06-30', 'NZD', 'BUY', 1350000, 99.8000, 'CP-APAC-OT-0603', 'ACCT-APAC-OT-E', 'Tasman Macro Fund', 'TRDR-A105', 'Rates', 'APAC-RATES-E', 'BRKR-AKL-1'),
('T-APAC-0603-OT-002', 'APAC', '2026-06-03', '2026-06-03 09:10:05', 'Fixed Income', 'NZGB-7Y-0603', '2033-06-30', 'NZD', 'SELL', 1340000, 99.8050, 'CP-APAC-OT-0603', 'ACCT-APAC-OT-F', 'Tasman Macro Fund', 'TRDR-A106', 'Rates', 'APAC-RATES-F', 'BRKR-AKL-1'),
('T-APAC-0604-OT-001', 'APAC', '2026-06-04', '2026-06-04 09:12:00', 'Fixed Income', 'KTB-10Y-0604', '2036-06-30', 'KRW', 'BUY', 1450000, 100.5000, 'CP-APAC-OT-0604', 'ACCT-APAC-OT-G', 'Han River Fund', 'TRDR-A107', 'Rates', 'APAC-RATES-G', 'BRKR-SEL-1'),
('T-APAC-0604-OT-002', 'APAC', '2026-06-04', '2026-06-04 09:12:04', 'Fixed Income', 'KTB-10Y-0604', '2036-06-30', 'KRW', 'SELL', 1440000, 100.5050, 'CP-APAC-OT-0604', 'ACCT-APAC-OT-H', 'Han River Fund', 'TRDR-A108', 'Rates', 'APAC-RATES-H', 'BRKR-SEL-1'),
('T-APAC-JGB-0605-OT-001', 'APAC', '2026-06-05', '2026-06-05 09:55:00', 'Fixed Income', 'JGB-10Y-0605', '2036-06-30', 'JPY', 'BUY', 1300000, 100.4000, 'CP-APAC-GAMMA', 'ACCT-APAC-RATES-A', 'Sakura Rates Fund', 'TRDR-A201', 'Rates', 'APAC-GOVT-A', 'BRKR-TKO-2'),
('T-APAC-JGB-0605-OT-002', 'APAC', '2026-06-05', '2026-06-05 09:55:05', 'Fixed Income', 'JGB-10Y-0605', '2036-06-30', 'JPY', 'SELL', 1290000, 100.4050, 'CP-APAC-GAMMA', 'ACCT-APAC-RATES-B', 'Sakura Rates Fund', 'TRDR-A202', 'Rates', 'APAC-GOVT-B', 'BRKR-TKO-2'),
('T-APAC-CAL-JGB-0605-001', 'APAC', '2026-06-05', '2026-06-05 11:20:00', 'Fixed Income', 'JGB-CAL-MINI-10Y', '2036-06-30', 'JPY', 'BUY', 650000, 99.7500, 'CP-APAC-CAL-1', 'ACCT-APAC-CAL-A', 'Calibration Asia Fund', 'TRDR-A401', 'Rates', 'APAC-CAL-A', 'BRKR-TKO-CAL'),
('T-APAC-CAL-JGB-0605-002', 'APAC', '2026-06-05', '2026-06-05 11:20:04', 'Fixed Income', 'JGB-CAL-MINI-10Y', '2036-06-30', 'JPY', 'SELL', 648000, 99.7600, 'CP-APAC-CAL-1', 'ACCT-APAC-CAL-B', 'Calibration Asia Fund', 'TRDR-A402', 'Rates', 'APAC-CAL-B', 'BRKR-TKO-CAL'),
('T-APAC-CAL-CUM-0605-BUY-1', 'APAC', '2026-06-04', '2026-06-04 14:25:00', 'Currencies', 'AUD/NZD-CAL-CUM', '2026-06-12', 'AUD', 'BUY', 1900000, 1.05000, 'CP-APAC-CAL-2', 'ACCT-APAC-CAL-C', 'Calibration Asia Macro Fund', 'TRDR-A403', 'FX', 'APAC-CAL-FX-A', 'BRKR-SYD-CAL'),
('T-APAC-CAL-CUM-0605-BUY-2', 'APAC', '2026-06-05', '2026-06-05 08:50:00', 'Currencies', 'AUD/NZD-CAL-CUM', '2026-06-12', 'AUD', 'BUY', 1900000, 1.05000, 'CP-APAC-CAL-2', 'ACCT-APAC-CAL-C', 'Calibration Asia Macro Fund', 'TRDR-A403', 'FX', 'APAC-CAL-FX-A', 'BRKR-SYD-CAL'),
('T-APAC-CAL-CUM-0605-SELL-1', 'APAC', '2026-06-05', '2026-06-05 08:50:12', 'Currencies', 'AUD/NZD-CAL-CUM', '2026-06-12', 'AUD', 'SELL', 1850000, 1.05010, 'CP-APAC-CAL-2', 'ACCT-APAC-CAL-D', 'Calibration Asia Macro Fund', 'TRDR-A404', 'FX', 'APAC-CAL-FX-B', 'BRKR-SYD-CAL'),
('T-APAC-CAL-CUM-0605-SELL-2', 'APAC', '2026-06-05', '2026-06-05 08:50:20', 'Currencies', 'AUD/NZD-CAL-CUM', '2026-06-12', 'AUD', 'SELL', 1850000, 1.05010, 'CP-APAC-CAL-2', 'ACCT-APAC-CAL-D', 'Calibration Asia Macro Fund', 'TRDR-A404', 'FX', 'APAC-CAL-FX-B', 'BRKR-SYD-CAL'),
('T-APAC-NA-0605-001', 'APAC', '2026-06-05', '2026-06-05 13:40:00', 'Commodities', 'IRON-ORE-CAL-NA', '2026-09-30', 'USD', 'BUY', 2000, 112.5000, 'CP-APAC-NA-A', 'ACCT-APAC-NA-A', 'Pacific Commodities Fund', 'TRDR-A501', 'Commodities', 'APAC-CMD-A', 'BRKR-SIN-1'),
('T-APAC-NA-0605-002', 'APAC', '2026-06-05', '2026-06-05 13:41:00', 'Commodities', 'IRON-ORE-CAL-NA', '2026-09-30', 'USD', 'SELL', 2000, 112.5000, 'CP-APAC-NA-B', 'ACCT-APAC-NA-B', 'Pacific Commodities Fund', 'TRDR-A502', 'Commodities', 'APAC-CMD-B', 'BRKR-SIN-1');

DELIMITER //

CREATE PROCEDURE sp_get_surveillance_model_config(
    IN p_appid INT,
    IN p_region VARCHAR(10)
)
BEGIN
    SELECT
        master.appid,
        config.modelid,
        config.region,
        master.name AS app_name,
        master.model_code,
        master.model_name,
        master.model_class_name
    FROM surveillance_model_config config
    JOIN surveillance_model_master master
      ON config.appid = master.appid
     AND config.region = master.region
    WHERE config.appid = p_appid
      AND config.region = UPPER(p_region)
      AND config.enabled = TRUE
      AND master.enabled = TRUE;
END//

CREATE PROCEDURE sp_claim_next_surveillance_run_request()
BEGIN
    DECLARE v_request_id BIGINT DEFAULT NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_request_id = NULL;

    SELECT request_id
    INTO v_request_id
    FROM surveillance_run_request
    WHERE status IN ('PENDING', 'FAILED')
    ORDER BY
        CASE status
            WHEN 'PENDING' THEN 0
            ELSE 1
        END,
        requested_at,
        request_id
    LIMIT 1
    FOR UPDATE SKIP LOCKED;

    IF v_request_id IS NOT NULL THEN
        UPDATE surveillance_run_request
        SET status = 'RUNNING',
            started_at = CURRENT_TIMESTAMP,
            completed_at = NULL,
            error_message = NULL
        WHERE request_id = v_request_id;
    END IF;

    SELECT
        request_id,
        appid,
        region,
        business_date,
        status
    FROM surveillance_run_request
    WHERE request_id = v_request_id;
END//

CREATE PROCEDURE sp_insert_surveillance_run_request(
    IN p_appid INT,
    IN p_region VARCHAR(10),
    IN p_business_date DATE,
    IN p_requested_by VARCHAR(80)
)
BEGIN
    INSERT INTO surveillance_run_request (
        appid,
        region,
        business_date,
        requested_by
    ) VALUES (
        p_appid,
        UPPER(p_region),
        p_business_date,
        COALESCE(NULLIF(TRIM(p_requested_by), ''), 'FRONTEND_USER')
    );

    SELECT LAST_INSERT_ID() AS request_id;
END//

CREATE PROCEDURE sp_find_latest_surveillance_run_request(
    IN p_appid INT,
    IN p_region VARCHAR(10),
    IN p_business_date DATE
)
BEGIN
    SELECT
        request_id,
        appid,
        region,
        business_date,
        status,
        alerts_generated,
        requested_at,
        started_at,
        completed_at,
        error_message
    FROM surveillance_run_request
    WHERE appid = p_appid
      AND region = UPPER(p_region)
      AND business_date = p_business_date
    ORDER BY requested_at DESC, request_id DESC
    LIMIT 1;
END//

CREATE PROCEDURE sp_find_surveillance_run_requests(
    IN p_appid INT,
    IN p_region VARCHAR(10),
    IN p_business_date DATE
)
BEGIN
    SELECT
        request_id,
        appid,
        region,
        business_date,
        status,
        alerts_generated,
        requested_at,
        started_at,
        completed_at,
        error_message
    FROM surveillance_run_request
    WHERE appid = p_appid
      AND region = UPPER(p_region)
      AND business_date = p_business_date
    ORDER BY requested_at DESC, request_id DESC;
END//

CREATE PROCEDURE sp_find_surveillance_run_request_by_id(
    IN p_request_id BIGINT
)
BEGIN
    SELECT
        request_id,
        appid,
        region,
        business_date,
        status,
        alerts_generated,
        requested_at,
        started_at,
        completed_at,
        error_message
    FROM surveillance_run_request
    WHERE request_id = p_request_id;
END//

CREATE PROCEDURE sp_find_calibration_run_requests()
BEGIN
    SELECT
        request_id,
        appid,
        region,
        business_date,
        status,
        alerts_generated,
        requested_at,
        started_at,
        completed_at,
        error_message
    FROM surveillance_run_request
    WHERE appid IN (4, 5, 6)
      AND region IN ('NAMRC', 'EMEAC', 'APACC')
    ORDER BY requested_at DESC, request_id DESC
    LIMIT 100;
END//

CREATE PROCEDURE sp_mark_surveillance_run_request_completed(
    IN p_request_id BIGINT,
    IN p_alerts_generated INT
)
BEGIN
    UPDATE surveillance_run_request
    SET status = 'COMPLETED',
        completed_at = CURRENT_TIMESTAMP,
        alerts_generated = p_alerts_generated,
        error_message = NULL
    WHERE request_id = p_request_id;
END//

CREATE PROCEDURE sp_mark_surveillance_run_request_failed(
    IN p_request_id BIGINT,
    IN p_error_message VARCHAR(2000)
)
BEGIN
    UPDATE surveillance_run_request
    SET status = 'FAILED',
        completed_at = CURRENT_TIMESTAMP,
        error_message = p_error_message
    WHERE request_id = p_request_id;
END//

CREATE PROCEDURE sp_insert_ficc_wash_alert_history(
    IN p_alert_fingerprint CHAR(64),
    IN p_alert_id VARCHAR(80),
    IN p_request_id BIGINT,
    IN p_appid INT,
    IN p_modelid INT,
    IN p_region VARCHAR(10),
    IN p_alert_type VARCHAR(80),
    IN p_match_type VARCHAR(80),
    IN p_business_date DATE,
    IN p_first_trade_date DATE,
    IN p_last_trade_date DATE,
    IN p_related_trade_ids VARCHAR(1000),
    IN p_alert_business_key_hash CHAR(64),
    IN p_trade_date DATE,
    IN p_asset_class VARCHAR(50),
    IN p_instrument_id VARCHAR(80),
    IN p_maturity_date DATE,
    IN p_currency CHAR(3),
    IN p_trader_id VARCHAR(500),
    IN p_counterparty_id VARCHAR(500),
    IN p_alert_payload LONGTEXT,
    IN p_dispatch_status VARCHAR(30)
)
BEGIN
    INSERT INTO ficc_wash_alert_history (
        alert_fingerprint,
        alert_id,
        request_id,
        appid,
        modelid,
        region,
        alert_type,
        match_type,
        business_date,
        first_trade_date,
        last_trade_date,
        related_trade_ids,
        alert_business_key_hash,
        trade_date,
        asset_class,
        instrument_id,
        maturity_date,
        currency,
        trader_id,
        counterparty_id,
        alert_payload,
        dispatch_status
    ) VALUES (
        p_alert_fingerprint,
        p_alert_id,
        p_request_id,
        p_appid,
        p_modelid,
        UPPER(p_region),
        p_alert_type,
        p_match_type,
        p_business_date,
        p_first_trade_date,
        p_last_trade_date,
        p_related_trade_ids,
        p_alert_business_key_hash,
        p_trade_date,
        p_asset_class,
        p_instrument_id,
        p_maturity_date,
        UPPER(p_currency),
        p_trader_id,
        p_counterparty_id,
        p_alert_payload,
        p_dispatch_status
    );

    SELECT LAST_INSERT_ID() AS alert_history_id;
END//

CREATE PROCEDURE sp_insert_ficc_wash_alert_history_trade(
    IN p_alert_history_id BIGINT,
    IN p_trade_sequence INT,
    IN p_trade_id VARCHAR(50),
    IN p_trade_date DATE,
    IN p_trade_timestamp DATETIME,
    IN p_asset_class VARCHAR(50),
    IN p_instrument_id VARCHAR(80),
    IN p_maturity DATE,
    IN p_currency CHAR(3),
    IN p_side VARCHAR(4),
    IN p_quantity DECIMAL(22, 6),
    IN p_price DECIMAL(22, 8),
    IN p_total_amount DECIMAL(30, 8),
    IN p_counterparty_id VARCHAR(80),
    IN p_account_id VARCHAR(80),
    IN p_beneficial_owner VARCHAR(120),
    IN p_trader_id VARCHAR(80),
    IN p_desk VARCHAR(80),
    IN p_book VARCHAR(80),
    IN p_broker VARCHAR(80),
    IN p_trade_role VARCHAR(30)
)
BEGIN
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
    ) VALUES (
        p_alert_history_id,
        p_trade_sequence,
        p_trade_id,
        p_trade_date,
        p_trade_timestamp,
        p_asset_class,
        p_instrument_id,
        p_maturity,
        p_currency,
        p_side,
        p_quantity,
        p_price,
        p_total_amount,
        p_counterparty_id,
        p_account_id,
        p_beneficial_owner,
        p_trader_id,
        p_desk,
        p_book,
        p_broker,
        p_trade_role
    );
END//

CREATE PROCEDURE sp_find_ficc_wash_alert_history(
    IN p_appid INT,
    IN p_region VARCHAR(10),
    IN p_business_date DATE
)
BEGIN
    SELECT
        alert_history_id,
        alert_id,
        request_id,
        appid,
        modelid,
        region,
        alert_type,
        match_type,
        business_date,
        first_trade_date,
        last_trade_date,
        related_trade_ids,
        alert_business_key_hash,
        trade_date,
        asset_class,
        instrument_id,
        maturity_date,
        currency,
        trader_id,
        counterparty_id,
        alert_payload,
        dispatch_status,
        created_at
    FROM ficc_wash_alert_history
    WHERE appid = p_appid
      AND region = UPPER(p_region)
      AND business_date = p_business_date
    ORDER BY alert_history_id;
END//

CREATE PROCEDURE sp_delete_ficc_wash_alert_history_for_run(
    IN p_appid INT,
    IN p_modelid INT,
    IN p_region VARCHAR(10),
    IN p_business_date DATE
)
BEGIN
    DECLARE v_deleted_trade_count INT DEFAULT 0;
    DECLARE v_deleted_alert_count INT DEFAULT 0;

    DELETE detail
    FROM ficc_wash_alert_history_trade detail
    JOIN ficc_wash_alert_history history
      ON detail.alert_history_id = history.alert_history_id
    WHERE history.appid = p_appid
      AND history.modelid = p_modelid
      AND history.region = UPPER(p_region)
      AND history.business_date = p_business_date;

    SET v_deleted_trade_count = ROW_COUNT();

    DELETE FROM ficc_wash_alert_history
    WHERE appid = p_appid
      AND modelid = p_modelid
      AND region = UPPER(p_region)
      AND business_date = p_business_date;

    SET v_deleted_alert_count = ROW_COUNT();

    SELECT
        v_deleted_alert_count AS deleted_alert_count,
        v_deleted_trade_count AS deleted_trade_count;
END//

CREATE PROCEDURE sp_insert_ficc_wash_calibration_alert_history(
    IN p_alert_fingerprint CHAR(64),
    IN p_alert_id VARCHAR(80),
    IN p_request_id BIGINT,
    IN p_appid INT,
    IN p_modelid INT,
    IN p_region VARCHAR(10),
    IN p_alert_type VARCHAR(80),
    IN p_match_type VARCHAR(80),
    IN p_business_date DATE,
    IN p_first_trade_date DATE,
    IN p_last_trade_date DATE,
    IN p_related_trade_ids VARCHAR(1000),
    IN p_alert_business_key_hash CHAR(64),
    IN p_trade_date DATE,
    IN p_asset_class VARCHAR(50),
    IN p_instrument_id VARCHAR(80),
    IN p_maturity_date DATE,
    IN p_currency CHAR(3),
    IN p_trader_id VARCHAR(500),
    IN p_counterparty_id VARCHAR(500),
    IN p_alert_payload LONGTEXT,
    IN p_one_time_min_total_amount DECIMAL(22, 6),
    IN p_cumulative_min_total_amount DECIMAL(22, 6),
    IN p_quantity_tolerance_percent DECIMAL(22, 6),
    IN p_total_amount_tolerance_percent DECIMAL(22, 6),
    IN p_cumulative_lookup_days INT,
    IN p_dispatch_status VARCHAR(30)
)
BEGIN
    INSERT INTO ficc_wash_calibration_alert_history (
        alert_fingerprint,
        alert_id,
        request_id,
        appid,
        modelid,
        region,
        alert_type,
        match_type,
        business_date,
        first_trade_date,
        last_trade_date,
        related_trade_ids,
        alert_business_key_hash,
        trade_date,
        asset_class,
        instrument_id,
        maturity_date,
        currency,
        trader_id,
        counterparty_id,
        alert_payload,
        one_time_min_total_amount,
        cumulative_min_total_amount,
        quantity_tolerance_percent,
        total_amount_tolerance_percent,
        cumulative_lookup_days,
        dispatch_status
    ) VALUES (
        p_alert_fingerprint,
        p_alert_id,
        p_request_id,
        p_appid,
        p_modelid,
        UPPER(p_region),
        p_alert_type,
        p_match_type,
        p_business_date,
        p_first_trade_date,
        p_last_trade_date,
        p_related_trade_ids,
        p_alert_business_key_hash,
        p_trade_date,
        p_asset_class,
        p_instrument_id,
        p_maturity_date,
        UPPER(p_currency),
        p_trader_id,
        p_counterparty_id,
        p_alert_payload,
        p_one_time_min_total_amount,
        p_cumulative_min_total_amount,
        p_quantity_tolerance_percent,
        p_total_amount_tolerance_percent,
        p_cumulative_lookup_days,
        p_dispatch_status
    );

    SELECT LAST_INSERT_ID() AS calibration_alert_history_id;
END//

CREATE PROCEDURE sp_insert_ficc_wash_calibration_alert_history_trade(
    IN p_calibration_alert_history_id BIGINT,
    IN p_trade_sequence INT,
    IN p_trade_id VARCHAR(50),
    IN p_trade_date DATE,
    IN p_trade_timestamp DATETIME,
    IN p_asset_class VARCHAR(50),
    IN p_instrument_id VARCHAR(80),
    IN p_maturity DATE,
    IN p_currency CHAR(3),
    IN p_side VARCHAR(4),
    IN p_quantity DECIMAL(22, 6),
    IN p_price DECIMAL(22, 8),
    IN p_total_amount DECIMAL(30, 8),
    IN p_counterparty_id VARCHAR(80),
    IN p_account_id VARCHAR(80),
    IN p_beneficial_owner VARCHAR(120),
    IN p_trader_id VARCHAR(80),
    IN p_desk VARCHAR(80),
    IN p_book VARCHAR(80),
    IN p_broker VARCHAR(80),
    IN p_trade_role VARCHAR(30)
)
BEGIN
    INSERT INTO ficc_wash_calibration_alert_history_trade (
        calibration_alert_history_id,
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
    ) VALUES (
        p_calibration_alert_history_id,
        p_trade_sequence,
        p_trade_id,
        p_trade_date,
        p_trade_timestamp,
        p_asset_class,
        p_instrument_id,
        p_maturity,
        p_currency,
        p_side,
        p_quantity,
        p_price,
        p_total_amount,
        p_counterparty_id,
        p_account_id,
        p_beneficial_owner,
        p_trader_id,
        p_desk,
        p_book,
        p_broker,
        p_trade_role
    );
END//

CREATE PROCEDURE sp_find_ficc_wash_calibration_alert_history_by_request(
    IN p_request_id BIGINT
)
BEGIN
    SELECT
        calibration_alert_history_id,
        alert_id,
        request_id,
        appid,
        modelid,
        region,
        alert_type,
        match_type,
        business_date,
        first_trade_date,
        last_trade_date,
        related_trade_ids,
        alert_business_key_hash,
        trade_date,
        asset_class,
        instrument_id,
        maturity_date,
        currency,
        trader_id,
        counterparty_id,
        alert_payload,
        one_time_min_total_amount,
        cumulative_min_total_amount,
        quantity_tolerance_percent,
        total_amount_tolerance_percent,
        cumulative_lookup_days,
        dispatch_status,
        created_at
    FROM ficc_wash_calibration_alert_history
    WHERE request_id = p_request_id
    ORDER BY calibration_alert_history_id;
END//

CREATE PROCEDURE sp_delete_ficc_wash_calibration_alert_history_for_request(
    IN p_request_id BIGINT
)
BEGIN
    DECLARE v_deleted_trade_count INT DEFAULT 0;
    DECLARE v_deleted_alert_count INT DEFAULT 0;

    DELETE detail
    FROM ficc_wash_calibration_alert_history_trade detail
    JOIN ficc_wash_calibration_alert_history history
      ON detail.calibration_alert_history_id = history.calibration_alert_history_id
    WHERE history.request_id = p_request_id;

    SET v_deleted_trade_count = ROW_COUNT();

    DELETE FROM ficc_wash_calibration_alert_history
    WHERE request_id = p_request_id;

    SET v_deleted_alert_count = ROW_COUNT();

    SELECT
        v_deleted_alert_count AS deleted_alert_count,
        v_deleted_trade_count AS deleted_trade_count;
END//

CREATE PROCEDURE sp_get_surveillance_model_threshold(
    IN p_appid INT,
    IN p_modelid INT,
    IN p_region VARCHAR(10),
    IN p_threshold_name VARCHAR(80)
)
BEGIN
    SELECT
        threshold_value,
        lookup_days
    FROM surveillance_model_threshold
    WHERE appid = p_appid
      AND modelid = p_modelid
      AND region = UPPER(p_region)
      AND threshold_name = UPPER(p_threshold_name)
      AND enabled = TRUE;
END//

CREATE PROCEDURE sp_get_surveillance_model_threshold_snapshot(
    IN p_appid INT,
    IN p_modelid INT,
    IN p_region VARCHAR(10)
)
BEGIN
    SELECT
        COALESCE(MAX(CASE
            WHEN threshold_name = 'ONE_TIME_MIN_TOTAL_AMOUNT' THEN threshold_value
        END), 0.000000) AS one_time_min_total_amount,
        COALESCE(MAX(CASE
            WHEN threshold_name = 'CUMULATIVE_MIN_TOTAL_AMOUNT' THEN threshold_value
        END), 0.000000) AS cumulative_min_total_amount,
        COALESCE(MAX(CASE
            WHEN threshold_name = 'QUANTITY_TOLERANCE_PERCENT' THEN threshold_value
        END), 0.000000) AS quantity_tolerance_percent,
        COALESCE(MAX(CASE
            WHEN threshold_name = 'TOTAL_AMOUNT_TOLERANCE_PERCENT' THEN threshold_value
        END), 0.000000) AS total_amount_tolerance_percent,
        COALESCE(MAX(CASE
            WHEN threshold_name = 'CUMULATIVE_MIN_TOTAL_AMOUNT' THEN lookup_days
        END), 0) AS cumulative_lookup_days
    FROM surveillance_model_threshold
    WHERE appid = p_appid
      AND modelid = p_modelid
      AND region = UPPER(p_region)
      AND enabled = TRUE;
END//

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
