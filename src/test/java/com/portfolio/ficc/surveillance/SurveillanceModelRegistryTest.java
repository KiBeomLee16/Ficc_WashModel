package com.portfolio.ficc.surveillance;

import com.portfolio.ficc.io.DatabaseConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.portfolio.ficc.TestConfigs.alertDispatcher;

class SurveillanceModelRegistryTest {

	@Test
	void resolvesRegisteredModelByClassName() {
		SurveillanceModelRegistry registry = new SurveillanceModelRegistry(List.of(new FiccWashTradeModel(
				new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""), alertDispatcher())));

		AbstractSurveillanceModel model = registry.getModel("com.portfolio.ficc.surveillance.FiccWashTradeModel");

		assertEquals("FICC_WASH_TRADE", model.modelCode());
	}

	@Test
	void rejectsUnregisteredModelClassName() {
		SurveillanceModelRegistry registry = new SurveillanceModelRegistry(List.of(new FiccWashTradeModel(
				new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""), alertDispatcher())));

		assertThrows(IllegalArgumentException.class,
				() -> registry.getModel("com.portfolio.ficc.surveillance.UnknownModel"));
	}
}
