CREATE DATABASE IF NOT EXISTS ficc_surveillance;
USE ficc_surveillance;

DROP PROCEDURE IF EXISTS sp_get_surveillance_model_config;
DROP PROCEDURE IF EXISTS sp_get_surveillance_model_threshold;
DROP PROCEDURE IF EXISTS sp_get_ficc_trades;
DROP PROCEDURE IF EXISTS sp_claim_next_surveillance_run_request;
DROP PROCEDURE IF EXISTS sp_insert_surveillance_run_request;
DROP PROCEDURE IF EXISTS sp_find_latest_surveillance_run_request;
DROP PROCEDURE IF EXISTS sp_find_surveillance_run_requests;
DROP PROCEDURE IF EXISTS sp_mark_surveillance_run_request_completed;
DROP PROCEDURE IF EXISTS sp_mark_surveillance_run_request_failed;
DROP PROCEDURE IF EXISTS sp_insert_ficc_wash_alert_history;
DROP PROCEDURE IF EXISTS sp_insert_ficc_wash_alert_history_trade;
DROP PROCEDURE IF EXISTS sp_find_ficc_wash_alert_history;
DROP PROCEDURE IF EXISTS sp_delete_ficc_wash_alert_history_for_run;
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
    appid INT NOT NULL,
    modelid INT NOT NULL,
    region VARCHAR(10) NOT NULL,
    alert_type VARCHAR(80) NOT NULL,
    match_type VARCHAR(80) NOT NULL,
    business_date DATE NOT NULL,
    first_trade_date DATE NOT NULL,
    last_trade_date DATE NOT NULL,
    related_trade_ids VARCHAR(1000) NOT NULL,
    alert_payload LONGTEXT NOT NULL,
    dispatch_status VARCHAR(30) NOT NULL DEFAULT 'DISPATCHED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ficc_wash_alert_history_fingerprint (alert_fingerprint),
    INDEX idx_ficc_wash_alert_history_run (appid, modelid, region, business_date),
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
(3, 'APAC', 'APAC FICC_WASH Model', 'FICC_WASH_TRADE', 'FICC Trade Surveillance', 'com.portfolio.ficc.surveillance.FiccWashTradeModel', 'Asia Pacific FICC_WASH Model.', TRUE);

INSERT INTO surveillance_model_config (
    appid,
    modelid,
    region,
    enabled
) VALUES
(1, 1, 'NAMR', TRUE),
(2, 1, 'EMEA', TRUE),
(3, 1, 'APAC', TRUE);

INSERT INTO surveillance_run_request (
    appid,
    region,
    business_date,
    requested_by
) VALUES
(1, 'NAMR', '2026-06-04', 'portfolio-seed'),
(1, 'NAMR', '2026-06-05', 'portfolio-seed'),
(1, 'NAMR', '2026-06-06', 'portfolio-seed'),
(1, 'NAMR', '2026-06-07', 'portfolio-seed'),
(1, 'NAMR', '2026-06-08', 'portfolio-seed'),
(2, 'EMEA', '2026-06-04', 'portfolio-seed'),
(2, 'EMEA', '2026-06-05', 'portfolio-seed'),
(2, 'EMEA', '2026-06-06', 'portfolio-seed'),
(2, 'EMEA', '2026-06-07', 'portfolio-seed'),
(2, 'EMEA', '2026-06-08', 'portfolio-seed'),
(3, 'APAC', '2026-06-04', 'portfolio-seed'),
(3, 'APAC', '2026-06-05', 'portfolio-seed'),
(3, 'APAC', '2026-06-06', 'portfolio-seed'),
(3, 'APAC', '2026-06-07', 'portfolio-seed'),
(3, 'APAC', '2026-06-08', 'portfolio-seed');

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
(3, 1, 'APAC', 'TOTAL_AMOUNT_TOLERANCE_PERCENT', 5.000000, 0, TRUE);

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
('T-NAMR-0604-OT-001', 'NAMR', '2026-06-04', '2026-06-04 09:15:00', 'Fixed Income', 'UST-2Y-0604', '2028-06-30', 'USD', 'BUY', 2000000, 100.2500, 'CP-OT-0604', 'ACCT-OT-0604-A', 'Delta Rates Master Fund', 'TRDR-104', 'Rates', 'GOVT-OT-0604', 'BRKR-NY-1'),
('T-NAMR-0604-OT-002', 'NAMR', '2026-06-04', '2026-06-04 09:15:04', 'Fixed Income', 'UST-2Y-0604', '2028-06-30', 'USD', 'SELL', 1990000, 100.2510, 'CP-OT-0604', 'ACCT-OT-0604-B', 'Delta Rates Master Fund', 'TRDR-104', 'Rates', 'GOVT-OT-0604', 'BRKR-NY-1'),
('T-NAMR-0604-CUM-BUY-1', 'NAMR', '2026-06-04', '2026-06-04 10:00:00', 'Currencies', 'EUR/USD-CUM-0604', '2026-06-10', 'USD', 'BUY', 3000000, 1.10000, 'CP-CUM-0604', 'ACCT-CUM-0604-A', 'Aurora Macro Fund', 'TRDR-204', 'FX', 'FX-CUM-0604-A', 'BRKR-LON-1'),
('T-NAMR-0604-CUM-BUY-2', 'NAMR', '2026-06-04', '2026-06-04 10:00:11', 'Currencies', 'EUR/USD-CUM-0604', '2026-06-10', 'USD', 'BUY', 2000000, 1.10000, 'CP-CUM-0604', 'ACCT-CUM-0604-A', 'Aurora Macro Fund', 'TRDR-204', 'FX', 'FX-CUM-0604-A', 'BRKR-LON-1'),
('T-NAMR-0604-CUM-SELL-1', 'NAMR', '2026-06-04', '2026-06-04 10:00:25', 'Currencies', 'EUR/USD-CUM-0604', '2026-06-10', 'USD', 'SELL', 2500000, 1.10020, 'CP-CUM-0604', 'ACCT-CUM-0604-B', 'Aurora Macro Fund', 'TRDR-205', 'FX', 'FX-CUM-0604-B', 'BRKR-LON-1'),
('T-NAMR-0604-CUM-SELL-2', 'NAMR', '2026-06-04', '2026-06-04 10:00:39', 'Currencies', 'EUR/USD-CUM-0604', '2026-06-10', 'USD', 'SELL', 2450000, 1.10020, 'CP-CUM-0604', 'ACCT-CUM-0604-B', 'Aurora Macro Fund', 'TRDR-205', 'FX', 'FX-CUM-0604-B', 'BRKR-LON-1'),
('T-NAMR-0604-NA-001', 'NAMR', '2026-06-04', '2026-06-04 13:20:00', 'Commodities', 'WTI-DEC26-NA-0604', '2026-12-20', 'USD', 'BUY', 1000, 75.0000, 'CP-NA-0604', 'ACCT-NA-0604-A', 'Physical Energy Fund', 'TRDR-304', 'Commodities', 'ENERGY-NA-0604', 'BRKR-HOU-1'),
('T-NAMR-0604-NA-002', 'NAMR', '2026-06-04', '2026-06-04 13:27:00', 'Commodities', 'WTI-DEC26-NA-0604', '2026-12-20', 'USD', 'SELL', 1800, 75.5000, 'CP-NA-0604', 'ACCT-NA-0604-B', 'Physical Energy Fund', 'TRDR-305', 'Commodities', 'ENERGY-NA-0604', 'BRKR-HOU-1'),
('T-NAMR-0605-CUM-BUY-1', 'NAMR', '2026-06-04', '2026-06-04 15:10:00', 'Currencies', 'GBP/USD-CUM-0605', '2026-06-12', 'USD', 'BUY', 2000000, 1.26000, 'CP-CUM-0605', 'ACCT-CUM-0605-A', 'Boreal Macro Fund', 'TRDR-215', 'FX', 'FX-CUM-0605-A', 'BRKR-LON-2'),
('T-NAMR-0605-OT-001', 'NAMR', '2026-06-05', '2026-06-05 09:18:00', 'Fixed Income', 'CORP-ALPHA-2029', '2029-09-15', 'USD', 'BUY', 2500000, 101.2500, 'CP-OT-0605', 'ACCT-OT-0605-A', 'Orion Credit Fund', 'TRDR-105', 'Credit', 'CREDIT-OT-0605', 'BRKR-NY-2'),
('T-NAMR-0605-OT-002', 'NAMR', '2026-06-05', '2026-06-05 09:18:05', 'Fixed Income', 'CORP-ALPHA-2029', '2029-09-15', 'USD', 'SELL', 2490000, 101.2510, 'CP-OT-0605', 'ACCT-OT-0605-B', 'Orion Credit Fund', 'TRDR-105', 'Credit', 'CREDIT-OT-0605', 'BRKR-NY-2'),
('T-NAMR-0605-CUM-BUY-2', 'NAMR', '2026-06-05', '2026-06-05 10:05:00', 'Currencies', 'GBP/USD-CUM-0605', '2026-06-12', 'USD', 'BUY', 3000000, 1.26000, 'CP-CUM-0605', 'ACCT-CUM-0605-A', 'Boreal Macro Fund', 'TRDR-215', 'FX', 'FX-CUM-0605-A', 'BRKR-LON-2'),
('T-NAMR-0605-CUM-SELL-1', 'NAMR', '2026-06-05', '2026-06-05 10:05:18', 'Currencies', 'GBP/USD-CUM-0605', '2026-06-12', 'USD', 'SELL', 2500000, 1.26010, 'CP-CUM-0605', 'ACCT-CUM-0605-B', 'Boreal Macro Fund', 'TRDR-216', 'FX', 'FX-CUM-0605-B', 'BRKR-LON-2'),
('T-NAMR-0605-CUM-SELL-2', 'NAMR', '2026-06-05', '2026-06-05 10:05:36', 'Currencies', 'GBP/USD-CUM-0605', '2026-06-12', 'USD', 'SELL', 2450000, 1.26010, 'CP-CUM-0605', 'ACCT-CUM-0605-B', 'Boreal Macro Fund', 'TRDR-216', 'FX', 'FX-CUM-0605-B', 'BRKR-LON-2'),
('T-NAMR-0605-NA-001', 'NAMR', '2026-06-05', '2026-06-05 14:00:00', 'Fixed Income', 'UST-5Y-NA-0605', '2031-06-30', 'USD', 'BUY', 5000000, 99.0000, 'CP-NA-0605', 'ACCT-NA-0605-A', 'Mismatch Rates Fund', 'TRDR-315', 'Rates', 'RATES-NA-0605', 'BRKR-CHI-1'),
('T-NAMR-0605-NA-002', 'NAMR', '2026-06-05', '2026-06-05 14:00:09', 'Fixed Income', 'UST-5Y-NA-0605', '2031-06-30', 'USD', 'SELL', 4000000, 99.0000, 'CP-NA-0605', 'ACCT-NA-0605-B', 'Mismatch Rates Fund', 'TRDR-316', 'Rates', 'RATES-NA-0605', 'BRKR-CHI-1'),
('T-NAMR-0606-CUM-BUY-1', 'NAMR', '2026-06-05', '2026-06-05 15:05:00', 'Currencies', 'AUD/USD-CUM-0606', '2026-06-13', 'USD', 'BUY', 5000000, 0.66000, 'CP-CUM-0606', 'ACCT-CUM-0606-A', 'Cascade Macro Fund', 'TRDR-226', 'FX', 'FX-CUM-0606-A', 'BRKR-SYD-1'),
('T-NAMR-0606-CUM-BUY-2', 'NAMR', '2026-06-05', '2026-06-05 15:05:12', 'Currencies', 'AUD/USD-CUM-0606', '2026-06-13', 'USD', 'BUY', 4000000, 0.66000, 'CP-CUM-0606', 'ACCT-CUM-0606-A', 'Cascade Macro Fund', 'TRDR-226', 'FX', 'FX-CUM-0606-A', 'BRKR-SYD-1'),
('T-NAMR-0606-OT-001', 'NAMR', '2026-06-06', '2026-06-06 09:21:00', 'Fixed Income', 'UST-5Y-0606', '2031-06-30', 'USD', 'BUY', 5000000, 98.5000, 'CP-OT-0606', 'ACCT-OT-0606-A', 'Northstar Rates Fund', 'TRDR-106', 'Rates', 'GOVT-OT-0606', 'BRKR-NY-3'),
('T-NAMR-0606-OT-002', 'NAMR', '2026-06-06', '2026-06-06 09:21:04', 'Fixed Income', 'UST-5Y-0606', '2031-06-30', 'USD', 'SELL', 4980000, 98.5010, 'CP-OT-0606', 'ACCT-OT-0606-B', 'Northstar Rates Fund', 'TRDR-106', 'Rates', 'GOVT-OT-0606', 'BRKR-NY-3'),
('T-NAMR-0606-CUM-SELL-1', 'NAMR', '2026-06-06', '2026-06-06 10:12:00', 'Currencies', 'AUD/USD-CUM-0606', '2026-06-13', 'USD', 'SELL', 4500000, 0.66010, 'CP-CUM-0606', 'ACCT-CUM-0606-B', 'Cascade Macro Fund', 'TRDR-227', 'FX', 'FX-CUM-0606-B', 'BRKR-SYD-1'),
('T-NAMR-0606-CUM-SELL-2', 'NAMR', '2026-06-06', '2026-06-06 10:12:17', 'Currencies', 'AUD/USD-CUM-0606', '2026-06-13', 'USD', 'SELL', 4400000, 0.66010, 'CP-CUM-0606', 'ACCT-CUM-0606-B', 'Cascade Macro Fund', 'TRDR-227', 'FX', 'FX-CUM-0606-B', 'BRKR-SYD-1'),
('T-NAMR-0606-NA-001', 'NAMR', '2026-06-06', '2026-06-06 13:15:00', 'Commodities', 'XAU-AUG26-NA-0606', '2026-08-31', 'USD', 'BUY', 1000, 2350.0000, 'CP-NA-0606-A', 'ACCT-NA-0606-A', 'Metals Opportunity Fund', 'TRDR-326', 'Commodities', 'METALS-NA-0606', 'BRKR-CHI-2'),
('T-NAMR-0606-NA-002', 'NAMR', '2026-06-06', '2026-06-06 13:16:00', 'Commodities', 'XAU-AUG26-NA-0606', '2026-08-31', 'USD', 'SELL', 1000, 2350.0000, 'CP-NA-0606-B', 'ACCT-NA-0606-B', 'Metals Opportunity Fund', 'TRDR-327', 'Commodities', 'METALS-NA-0606', 'BRKR-CHI-2'),
('T-NAMR-0607-CUM-BUY-1', 'NAMR', '2026-06-06', '2026-06-06 15:45:00', 'Currencies', 'NZD/USD-CUM-0607', '2026-06-14', 'USD', 'BUY', 5000000, 0.61000, 'CP-CUM-0607', 'ACCT-CUM-0607-A', 'Dawn Macro Fund', 'TRDR-237', 'FX', 'FX-CUM-0607-A', 'BRKR-SYD-2'),
('T-NAMR-0607-CUM-BUY-2', 'NAMR', '2026-06-06', '2026-06-06 15:45:15', 'Currencies', 'NZD/USD-CUM-0607', '2026-06-14', 'USD', 'BUY', 4000000, 0.61000, 'CP-CUM-0607', 'ACCT-CUM-0607-A', 'Dawn Macro Fund', 'TRDR-237', 'FX', 'FX-CUM-0607-A', 'BRKR-SYD-2'),
('T-NAMR-0607-OT-001', 'NAMR', '2026-06-07', '2026-06-07 09:24:00', 'Fixed Income', 'AGENCY-MBS-2031', '2031-01-25', 'USD', 'BUY', 1500000, 101.1000, 'CP-OT-0607', 'ACCT-OT-0607-A', 'Helios Income Fund', 'TRDR-107', 'Securitized Products', 'MBS-OT-0607', 'BRKR-NY-4'),
('T-NAMR-0607-OT-002', 'NAMR', '2026-06-07', '2026-06-07 09:24:05', 'Fixed Income', 'AGENCY-MBS-2031', '2031-01-25', 'USD', 'SELL', 1490000, 101.1010, 'CP-OT-0607', 'ACCT-OT-0607-B', 'Helios Income Fund', 'TRDR-107', 'Securitized Products', 'MBS-OT-0607', 'BRKR-NY-4'),
('T-NAMR-0607-CUM-SELL-1', 'NAMR', '2026-06-07', '2026-06-07 10:18:00', 'Currencies', 'NZD/USD-CUM-0607', '2026-06-14', 'USD', 'SELL', 4500000, 0.61010, 'CP-CUM-0607', 'ACCT-CUM-0607-B', 'Dawn Macro Fund', 'TRDR-238', 'FX', 'FX-CUM-0607-B', 'BRKR-SYD-2'),
('T-NAMR-0607-CUM-SELL-2', 'NAMR', '2026-06-07', '2026-06-07 10:18:19', 'Currencies', 'NZD/USD-CUM-0607', '2026-06-14', 'USD', 'SELL', 4400000, 0.61010, 'CP-CUM-0607', 'ACCT-CUM-0607-B', 'Dawn Macro Fund', 'TRDR-238', 'FX', 'FX-CUM-0607-B', 'BRKR-SYD-2'),
('T-NAMR-0607-NA-001', 'NAMR', '2026-06-07', '2026-06-07 13:05:00', 'Fixed Income', 'CORP-BETA-NA-0607', '2030-03-15', 'USD', 'BUY', 2000000, 100.5000, 'CP-NA-0607-A', 'ACCT-NA-0607-A', 'Pair Break Credit Fund', 'TRDR-337', 'Credit', 'CREDIT-NA-0607', 'BRKR-NY-5'),
('T-NAMR-0607-NA-002', 'NAMR', '2026-06-07', '2026-06-07 13:05:06', 'Fixed Income', 'CORP-BETA-NA-0607', '2030-03-15', 'USD', 'SELL', 1990000, 100.5000, 'CP-NA-0607-B', 'ACCT-NA-0607-B', 'Pair Break Credit Fund', 'TRDR-338', 'Credit', 'CREDIT-NA-0607', 'BRKR-NY-5'),
('T-NAMR-UST-001', 'NAMR', '2026-06-08', '2026-06-08 09:30:00', 'Fixed Income', 'UST-10Y', '2036-05-15', 'USD', 'BUY', 10000000, 99.8125, 'CP-ALPHA', 'ACCT-RATES-ALPHA', 'Alpha Capital Master Fund', 'TRDR-17', 'Rates', 'GOVT-RATES-A', 'BRKR-NY-1'),
('T-NAMR-UST-002', 'NAMR', '2026-06-08', '2026-06-08 09:30:03', 'Fixed Income', 'UST-10Y', '2036-05-15', 'USD', 'SELL', 9980000, 99.8130, 'CP-ALPHA', 'ACCT-RATES-ALPHA', 'Alpha Capital Master Fund', 'TRDR-17', 'Rates', 'GOVT-RATES-A', 'BRKR-NY-1'),
('T-NAMR-FX-001', 'NAMR', '2026-06-06', '2026-06-06 09:42:00', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'BUY', 3000000, 1.08450, 'CP-BETA', 'ACCT-FX-BETA', 'Beta Macro Fund', 'TRDR-42', 'FX', 'FX-SPOT-A', 'BRKR-LON-2'),
('T-NAMR-FX-002', 'NAMR', '2026-06-07', '2026-06-07 09:42:10', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'BUY', 2000000, 1.08450, 'CP-BETA', 'ACCT-FX-BETA', 'Beta Macro Fund', 'TRDR-42', 'FX', 'FX-SPOT-A', 'BRKR-LON-2'),
('T-NAMR-FX-003', 'NAMR', '2026-06-08', '2026-06-08 09:42:20', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'SELL', 2500000, 1.08495, 'CP-BETA', 'ACCT-FX-OMEGA', 'Omega Global Fund', 'TRDR-42', 'Macro FX', 'FX-SPOT-B', 'BRKR-NY-9'),
('T-NAMR-FX-004', 'NAMR', '2026-06-08', '2026-06-08 09:42:30', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'SELL', 2450000, 1.08495, 'CP-BETA', 'ACCT-FX-OMEGA', 'Omega Global Fund', 'TRDR-42', 'Macro FX', 'FX-SPOT-B', 'BRKR-NY-9'),
('T-NAMR-CMD-001', 'NAMR', '2026-06-08', '2026-06-08 10:30:00', 'Commodities', 'WTI-DEC26', '2026-12-20', 'USD', 'BUY', 1000, 75.00, 'CP-GAMMA', 'ACCT-CMD-GAMMA', 'Gamma Energy Fund', 'TRDR-88', 'Commodities', 'ENERGY-A', 'BRKR-HOU-4'),
('T-NAMR-CMD-002', 'NAMR', '2026-06-08', '2026-06-08 10:40:00', 'Commodities', 'WTI-DEC26', '2026-12-20', 'USD', 'SELL', 1200, 76.00, 'CP-DELTA', 'ACCT-CMD-DELTA', 'Delta Physical Trading', 'TRDR-91', 'Commodities', 'ENERGY-B', 'BRKR-HOU-8'),
('T-UST-001', 'APAC', '2026-06-08', '2026-06-08 09:30:00', 'Fixed Income', 'UST-10Y', '2036-05-15', 'USD', 'BUY', 10000000, 99.8125, 'CP-ALPHA', 'ACCT-RATES-ALPHA', 'Alpha Capital Master Fund', 'TRDR-17', 'Rates', 'GOVT-RATES-A', 'BRKR-NY-1'),
('T-UST-002', 'APAC', '2026-06-08', '2026-06-08 09:30:03', 'Fixed Income', 'UST-10Y', '2036-05-15', 'USD', 'SELL', 9980000, 99.8130, 'CP-ALPHA', 'ACCT-RATES-ALPHA', 'Alpha Capital Master Fund', 'TRDR-17', 'Rates', 'GOVT-RATES-A', 'BRKR-NY-1'),
('T-FX-001', 'APAC', '2026-06-06', '2026-06-06 09:42:00', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'BUY', 3000000, 1.08450, 'CP-BETA', 'ACCT-FX-BETA', 'Beta Macro Fund', 'TRDR-42', 'FX', 'FX-SPOT-A', 'BRKR-LON-2'),
('T-FX-002', 'APAC', '2026-06-07', '2026-06-07 09:42:10', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'BUY', 2000000, 1.08450, 'CP-BETA', 'ACCT-FX-BETA', 'Beta Macro Fund', 'TRDR-42', 'FX', 'FX-SPOT-A', 'BRKR-LON-2'),
('T-FX-003', 'APAC', '2026-06-08', '2026-06-08 09:42:20', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'SELL', 2500000, 1.08495, 'CP-BETA', 'ACCT-FX-OMEGA', 'Omega Global Fund', 'TRDR-42', 'Macro FX', 'FX-SPOT-B', 'BRKR-NY-9'),
('T-FX-004', 'APAC', '2026-06-08', '2026-06-08 09:42:30', 'Currencies', 'EUR/USD-SPOT', '2026-06-10', 'USD', 'SELL', 2450000, 1.08495, 'CP-BETA', 'ACCT-FX-OMEGA', 'Omega Global Fund', 'TRDR-42', 'Macro FX', 'FX-SPOT-B', 'BRKR-NY-9'),
('T-CMD-001', 'APAC', '2026-06-08', '2026-06-08 10:30:00', 'Commodities', 'WTI-DEC26', '2026-12-20', 'USD', 'BUY', 1000, 75.00, 'CP-GAMMA', 'ACCT-CMD-GAMMA', 'Gamma Energy Fund', 'TRDR-88', 'Commodities', 'ENERGY-A', 'BRKR-HOU-4'),
('T-CMD-002', 'APAC', '2026-06-08', '2026-06-08 10:40:00', 'Commodities', 'WTI-DEC26', '2026-12-20', 'USD', 'SELL', 1200, 76.00, 'CP-DELTA', 'ACCT-CMD-DELTA', 'Delta Physical Trading', 'TRDR-91', 'Commodities', 'ENERGY-B', 'BRKR-HOU-8');

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
    IN p_appid INT,
    IN p_modelid INT,
    IN p_region VARCHAR(10),
    IN p_alert_type VARCHAR(80),
    IN p_match_type VARCHAR(80),
    IN p_business_date DATE,
    IN p_first_trade_date DATE,
    IN p_last_trade_date DATE,
    IN p_related_trade_ids VARCHAR(1000),
    IN p_alert_payload LONGTEXT,
    IN p_dispatch_status VARCHAR(30)
)
BEGIN
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
    ) VALUES (
        p_alert_fingerprint,
        p_alert_id,
        p_appid,
        p_modelid,
        UPPER(p_region),
        p_alert_type,
        p_match_type,
        p_business_date,
        p_first_trade_date,
        p_last_trade_date,
        p_related_trade_ids,
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

CREATE PROCEDURE sp_get_ficc_trades(
    IN p_appid INT,
    IN p_modelid INT,
    IN p_region VARCHAR(20),
    IN p_business_date DATE
)
BEGIN
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
     AND threshold.region = UPPER(p_region)
     AND threshold.threshold_name = 'CUMULATIVE_MIN_TOTAL_AMOUNT'
     AND threshold.enabled = TRUE
    WHERE trade.region = UPPER(p_region)
      AND trade.trade_date BETWEEN DATE_SUB(p_business_date, INTERVAL threshold.lookup_days DAY)
                               AND p_business_date
    ORDER BY trade.trade_timestamp, trade.trade_id;
END//

DELIMITER ;
