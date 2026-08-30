package com.portfolio.ficc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "surveillance.worker.enabled=false")
class FiccWashModelApplicationContextTest {

	@Test
	void contextLoadsWithS3ReportDisabled() {
	}
}
