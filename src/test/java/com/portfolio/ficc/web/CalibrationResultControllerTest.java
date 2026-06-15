package com.portfolio.ficc.web;

import com.portfolio.ficc.io.AlertHistoryRepository;
import com.portfolio.ficc.io.CalibrationResultRepository;
import com.portfolio.ficc.io.RunRequestRepository;
import com.portfolio.ficc.model.AlertHistoryResult;
import com.portfolio.ficc.model.CalibrationAlertHistoryResult;
import com.portfolio.ficc.model.RunRequestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalibrationResultControllerTest {

    @Mock
    private RunRequestRepository runRequestRepository;

    @Mock
    private CalibrationResultRepository calibrationResultRepository;

    @Mock
    private AlertHistoryRepository alertHistoryRepository;

    @Test
    void calibrationRunRequestsReturnsCalibrationRequestRows() {
        RunRequestStatus request = runRequestStatus();
        when(runRequestRepository.findCalibrationRunRequests()).thenReturn(List.of(request));

        CalibrationResultController controller = new CalibrationResultController(
                runRequestRepository,
                calibrationResultRepository,
                alertHistoryRepository
        );

        ResponseEntity<CalibrationResultController.CalibrationRunRequestsResponse> response =
                controller.calibrationRunRequests();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().requests().size());
        assertEquals(24L, response.getBody().requests().get(0).requestId());
        verify(runRequestRepository).findCalibrationRunRequests();
    }

    @Test
    void calibrationResultsReturnsComparisonRowsAgainstProductionHistory() {
        RunRequestStatus request = runRequestStatus();
        CalibrationAlertHistoryResult sameCalibrationAlert = calibrationAlert(
                99,
                "ficc_wash_alert_cal_1",
                "ONE_TIME_TRANSACTION",
                "T-1,T-2",
                "HASH-SAME"
        );
        CalibrationAlertHistoryResult newCalibrationAlert = calibrationAlert(
                100,
                "ficc_wash_alert_cal_2",
                "ONE_TIME_TRANSACTION",
                "T-5,T-6",
                "HASH-NEW"
        );
        AlertHistoryResult sameProductionAlert = productionAlert(
                11,
                88,
                "ficc_wash_alert_prod_1",
                "ONE_TIME_TRANSACTION",
                "T-9,T-10",
                "HASH-SAME"
        );
        AlertHistoryResult removedProductionAlert = productionAlert(
                12,
                89,
                "ficc_wash_alert_prod_2",
                "CUMULATIVE_TRANSACTION",
                "T-3,T-4",
                "HASH-REMOVED"
        );
        when(runRequestRepository.findByRequestId(24L)).thenReturn(Optional.of(request));
        when(calibrationResultRepository.findByRequestId(24L))
                .thenReturn(List.of(sameCalibrationAlert, newCalibrationAlert));
        when(alertHistoryRepository.findByRunCriteria(1, "NAMR", request.businessDate()))
                .thenReturn(List.of(sameProductionAlert, removedProductionAlert));

        CalibrationResultController controller = new CalibrationResultController(
                runRequestRepository,
                calibrationResultRepository,
                alertHistoryRepository
        );

        ResponseEntity<CalibrationResultController.CalibrationResultResponse> response =
                controller.calibrationResults(24L);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(24L, response.getBody().runRequest().requestId());
        assertEquals(3, response.getBody().alertCount());
        assertEquals("SAME_AS_PRODUCTION", response.getBody().alerts().get(0).comparisonStatus());
        assertEquals("CALIBRATION_NEW", response.getBody().alerts().get(1).comparisonStatus());
        assertEquals("PRODUCTION_REMOVED", response.getBody().alerts().get(2).comparisonStatus());
        assertEquals("ficc_wash_alert_cal_1", response.getBody().alerts().get(0).alertId());
        assertEquals(new BigDecimal("5.000000"), response.getBody().alerts().get(0).quantityTolerancePercent());
        assertEquals("ficc_wash_alert_prod_2", response.getBody().alerts().get(2).alertId());
        verify(runRequestRepository).findByRequestId(24L);
        verify(calibrationResultRepository).findByRequestId(24L);
        verify(alertHistoryRepository).findByRunCriteria(1, "NAMR", request.businessDate());
    }

    private static RunRequestStatus runRequestStatus() {
        return new RunRequestStatus(
                24,
                4,
                "NAMRC",
                LocalDate.of(2026, 6, 8),
                "COMPLETED",
                1,
                LocalDateTime.of(2026, 6, 8, 9, 40),
                LocalDateTime.of(2026, 6, 8, 9, 41),
                LocalDateTime.of(2026, 6, 8, 9, 42),
                null
        );
    }

    private static CalibrationAlertHistoryResult calibrationAlert(
            long calibrationAlertHistoryId,
            String alertId,
            String matchType,
            String relatedTradeIds,
            String alertBusinessKeyHash
    ) {
        LocalDate businessDate = LocalDate.of(2026, 6, 8);
        return new CalibrationAlertHistoryResult(
                calibrationAlertHistoryId,
                alertId,
                24,
                4,
                1,
                "NAMRC",
                "FICC_WASH_TRADE",
                matchType,
                businessDate,
                businessDate,
                businessDate,
                relatedTradeIds,
                alertBusinessKeyHash,
                businessDate,
                "Fixed Income",
                "UST-10Y",
                LocalDate.of(2036, 6, 8),
                "USD",
                "TRDR-1",
                "CP-ALPHA",
                "{}",
                new BigDecimal("100000000.000000"),
                new BigDecimal("5000000.000000"),
                new BigDecimal("5.000000"),
                new BigDecimal("5.000000"),
                4,
                "DISPATCHED",
                LocalDateTime.of(2026, 6, 8, 9, 45)
        );
    }

    private static AlertHistoryResult productionAlert(
            long alertHistoryId,
            long requestId,
            String alertId,
            String matchType,
            String relatedTradeIds,
            String alertBusinessKeyHash
    ) {
        LocalDate businessDate = LocalDate.of(2026, 6, 8);
        return new AlertHistoryResult(
                alertHistoryId,
                alertId,
                requestId,
                1,
                1,
                "NAMR",
                "FICC_WASH_TRADE",
                matchType,
                businessDate,
                businessDate,
                businessDate,
                relatedTradeIds,
                alertBusinessKeyHash,
                businessDate,
                "Fixed Income",
                "UST-10Y",
                LocalDate.of(2036, 6, 8),
                "USD",
                "TRDR-1",
                "CP-ALPHA",
                "{}",
                "DISPATCHED",
                LocalDateTime.of(2026, 6, 8, 9, 45)
        );
    }
}
