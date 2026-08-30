package com.portfolio.ficc.app;

import com.portfolio.ficc.io.AlertHistoryRepository;
import com.portfolio.ficc.model.AlertHistoryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class AlertReportService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AlertReportService.class);

    private final AlertHistoryRepository alertHistoryRepository;
    private final S3Client s3Client;
    private final boolean enabled;
    private final String bucketName;

    public AlertReportService(
            AlertHistoryRepository alertHistoryRepository,
            @Value("${ficc.aws.s3.enabled:false}") boolean enabled,
            @Value("${ficc.aws.s3.bucket-name:}") String bucketName,
            @Value("${ficc.aws.region:ap-northeast-2}") String awsRegion) {

        this(
                alertHistoryRepository,
                enabled,
                bucketName,
                S3Client.builder()
                        .region(Region.of(awsRegion))
                        .build());
    }

    AlertReportService(
            AlertHistoryRepository alertHistoryRepository,
            boolean enabled,
            String bucketName,
            S3Client s3Client) {

        this.alertHistoryRepository = Objects.requireNonNull(
                alertHistoryRepository,
                "alertHistoryRepository is required");
        this.enabled = enabled;
        this.bucketName = bucketName;
        this.s3Client = Objects.requireNonNull(
                s3Client,
                "s3Client is required");
    }

    public void uploadProductionReport(
            long requestId,
            int appId,
            String region,
            LocalDate businessDate) {

        if (!enabled) {
            LOGGER.info("S3 alert report is disabled.");
            return;
        }

        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException(
                    "FICC_S3_BUCKET_NAME is not configured.");
        }

        List<AlertHistoryResult> alerts =
                alertHistoryRepository
                        .findByRunCriteria(appId, region, businessDate)
                        .stream()
                        .filter(alert -> alert.requestId() == requestId)
                        .toList();

        if (alerts.isEmpty()) {
            LOGGER.info(
                    "No production alerts to upload: requestId={}",
                    requestId);
            return;
        }

        String csv = createCsv(alerts);

        String key = String.format(
                "alerts/%d/%02d/%02d/request-%d.csv",
                businessDate.getYear(),
                businessDate.getMonthValue(),
                businessDate.getDayOfMonth(),
                requestId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("text/csv; charset=UTF-8")
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromBytes(csv.getBytes(StandardCharsets.UTF_8)));

        LOGGER.info(
                "Uploaded Wash Trade alert report: bucket={}, key={}, alerts={}",
                bucketName,
                key,
                alerts.size());
    }

    private String createCsv(List<AlertHistoryResult> alerts) {

        StringBuilder csv = new StringBuilder();

        csv.append(
                "alert_history_id,alert_id,request_id,appid,modelid,region,"
                        + "alert_type,match_type,business_date,first_trade_date,"
                        + "last_trade_date,related_trade_ids,asset_class,"
                        + "instrument_id,maturity_date,currency,trader_id,"
                        + "counterparty_id,dispatch_status,created_at\n");

        for (AlertHistoryResult alert : alerts) {

            csv.append(value(alert.alertHistoryId())).append(',')
                    .append(value(alert.alertId())).append(',')
                    .append(value(alert.requestId())).append(',')
                    .append(value(alert.appId())).append(',')
                    .append(value(alert.modelId())).append(',')
                    .append(value(alert.region())).append(',')
                    .append(value(alert.alertType())).append(',')
                    .append(value(alert.matchType())).append(',')
                    .append(value(alert.businessDate())).append(',')
                    .append(value(alert.firstTradeDate())).append(',')
                    .append(value(alert.lastTradeDate())).append(',')
                    .append(value(alert.relatedTradeIds())).append(',')
                    .append(value(alert.assetClass())).append(',')
                    .append(value(alert.instrumentId())).append(',')
                    .append(value(alert.maturityDate())).append(',')
                    .append(value(alert.currency())).append(',')
                    .append(value(alert.traderId())).append(',')
                    .append(value(alert.counterpartyId())).append(',')
                    .append(value(alert.dispatchStatus())).append(',')
                    .append(value(alert.createdAt()))
                    .append('\n');
        }

        return csv.toString();
    }

    private String value(Object value) {

        if (value == null) {
            return "\"\"";
        }

        String escaped = String.valueOf(value)
                .replace("\"", "\"\"");

        return "\"" + escaped + "\"";
    }
}
