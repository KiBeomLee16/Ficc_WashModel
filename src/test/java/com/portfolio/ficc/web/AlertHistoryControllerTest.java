package com.portfolio.ficc.web;

import com.portfolio.ficc.io.AlertHistoryRepository;
import com.portfolio.ficc.io.RunRequestRepository;
import com.portfolio.ficc.model.AlertHistoryResult;
import com.portfolio.ficc.model.RunRequestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertHistoryControllerTest {

    @Mock
    private AlertHistoryRepository alertHistoryRepository;

    @Mock
    private RunRequestRepository runRequestRepository;

    @Test
    void searchAlertHistoryReturnsRowsForSelectedCriteria() {
        LocalDate businessDate = LocalDate.of(2026, 6, 8);
        AlertHistoryResult alert = new AlertHistoryResult(
                11,
                "ficc_wash_alert_11",
                3,
                1,
                "APAC",
                "FICC_WASH_TRADE",
                "ONE_TIME_TRANSACTION",
                businessDate,
                businessDate,
                businessDate,
                "T-UST-001,T-UST-002",
                "{\"reasons\":[\"same counterparty\"]}",
                "DISPATCHED",
                LocalDateTime.of(2026, 6, 8, 9, 45)
        );
        when(alertHistoryRepository.findByRunCriteria(3, "APAC", businessDate))
                .thenReturn(List.of(alert));
        RunRequestStatus runRequest = new RunRequestStatus(
                18,
                3,
                "APAC",
                businessDate,
                "COMPLETED",
                1,
                LocalDateTime.of(2026, 6, 8, 9, 40),
                LocalDateTime.of(2026, 6, 8, 9, 41),
                LocalDateTime.of(2026, 6, 8, 9, 42),
                null
        );
        when(runRequestRepository.findByRunCriteria(3, "APAC", businessDate))
                .thenReturn(List.of(runRequest));

        AlertHistoryController controller = new AlertHistoryController(alertHistoryRepository, runRequestRepository);

        ResponseEntity<AlertHistoryController.AlertHistorySearchResponse> response =
                controller.searchAlertHistory(3, "apac", businessDate);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().appId());
        assertEquals("APAC", response.getBody().region());
        assertEquals(businessDate, response.getBody().businessDate());
        assertEquals(1, response.getBody().alertCount());
        assertEquals(18L, response.getBody().runRequest().requestId());
        assertEquals("COMPLETED", response.getBody().runRequest().status());
        assertEquals(1, response.getBody().runRequests().size());
        assertEquals(18L, response.getBody().runRequests().get(0).requestId());
        assertEquals("ficc_wash_alert_11", response.getBody().alerts().get(0).alertId());

        verify(alertHistoryRepository).findByRunCriteria(3, "APAC", businessDate);
        verify(runRequestRepository).findByRunCriteria(3, "APAC", businessDate);
    }
}
