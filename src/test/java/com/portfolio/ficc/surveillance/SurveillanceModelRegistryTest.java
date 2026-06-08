package com.portfolio.ficc.surveillance;

import com.portfolio.ficc.io.AlertDispatcher;
import com.portfolio.ficc.io.DatabaseConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SurveillanceModelRegistryTest {

    @Test
    void resolvesRegisteredModelByClassName() {
        SurveillanceModelRegistry registry = new SurveillanceModelRegistry(
                List.of(new FiccWashTradeModel(
                        new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""),
                        new AlertDispatcher()
                ))
        );

        AbstractSurveillanceModel model = registry.getModel("com.portfolio.ficc.surveillance.FiccWashTradeModel");

        assertEquals("FICC_WASH_TRADE", model.modelCode());
    }

    @Test
    void rejectsUnregisteredModelClassName() {
        SurveillanceModelRegistry registry = new SurveillanceModelRegistry(
                List.of(new FiccWashTradeModel(
                        new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", ""),
                        new AlertDispatcher()
                ))
        );

        assertThrows(IllegalArgumentException.class,
                () -> registry.getModel("com.portfolio.ficc.surveillance.UnknownModel"));
    }
}
