package com.portfolio.ficc.web;

import com.portfolio.ficc.io.CalibrationThresholdRepository;
import com.portfolio.ficc.io.RunRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalibrationRunRequestControllerTest {

    @Mock
    private CalibrationThresholdRepository calibrationThresholdRepository;

    @Mock
    private RunRequestRepository runRequestRepository;

    @Test
    void submitCalibrationRunRequestUpdatesThresholdsThenCreatesRunRequest() {
        LocalDate businessDate = LocalDate.of(2026, 6, 5);
        BigDecimal oneTimeMinTotalAmount = new BigDecimal("90000000");
        BigDecimal cumulativeMinTotalAmount = new BigDecimal("4500000");
        BigDecimal quantityTolerancePercent = new BigDecimal("4.5");
        BigDecimal totalAmountTolerancePercent = new BigDecimal("4.25");
        when(runRequestRepository.insertRunRequest(4, "NAMRC", businessDate, "calibration-user"))
                .thenReturn(77L);

        CalibrationRunRequestController controller = new CalibrationRunRequestController(
                calibrationThresholdRepository,
                runRequestRepository
        );

        ResponseEntity<CalibrationRunRequestController.CalibrationRunRequestResponse> response =
                controller.submitCalibrationRunRequest(
                        4,
                        "namrc",
                        businessDate,
                        oneTimeMinTotalAmount,
                        cumulativeMinTotalAmount,
                        quantityTolerancePercent,
                        totalAmountTolerancePercent,
                        3,
                        "calibration-user"
                );

        assertEquals(202, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(77L, response.getBody().requestId());
        assertEquals(4, response.getBody().appId());
        assertEquals("NAMRC", response.getBody().region());
        assertEquals("PENDING", response.getBody().status());
        assertEquals(oneTimeMinTotalAmount, response.getBody().oneTimeMinTotalAmount());
        assertEquals(cumulativeMinTotalAmount, response.getBody().cumulativeMinTotalAmount());
        assertEquals(quantityTolerancePercent, response.getBody().quantityTolerancePercent());
        assertEquals(totalAmountTolerancePercent, response.getBody().totalAmountTolerancePercent());
        assertEquals(3, response.getBody().cumulativeLookupDays());

        InOrder order = inOrder(calibrationThresholdRepository, runRequestRepository);
        order.verify(calibrationThresholdRepository).updateCalibrationThresholds(
                4,
                "NAMRC",
                oneTimeMinTotalAmount,
                cumulativeMinTotalAmount,
                quantityTolerancePercent,
                totalAmountTolerancePercent,
                3
        );
        order.verify(runRequestRepository).insertRunRequest(4, "NAMRC", businessDate, "calibration-user");
    }
}
