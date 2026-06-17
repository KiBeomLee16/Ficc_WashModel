package com.portfolio.ficc.web;

import com.portfolio.ficc.io.RunRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunRequestControllerTest {

	@Mock
	private RunRequestRepository runRequestRepository;

	@Test
	void submitRunRequestRegistersPendingQueueRow() {
		LocalDate businessDate = LocalDate.of(2026, 6, 8);
		when(runRequestRepository.insertRunRequest(1, "NAMR", businessDate, "local-demo")).thenReturn(123L);

		RunRequestController controller = new RunRequestController(runRequestRepository);

		ResponseEntity<RunRequestController.RunRequestSubmissionResponse> response = controller.submitRunRequest(1,
				"NAMR", businessDate, "local-demo");

		assertEquals(202, response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(123L, response.getBody().requestId());
		assertEquals(1, response.getBody().appId());
		assertEquals("NAMR", response.getBody().region());
		assertEquals(businessDate, response.getBody().businessDate());
		assertEquals("PENDING", response.getBody().status());

		verify(runRequestRepository).insertRunRequest(1, "NAMR", businessDate, "local-demo");
	}
}
