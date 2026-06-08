package com.portfolio.ficc.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertDispatcherTest {

    @Test
    void dispatchPrintsAlertHeaderAndPayloadToConsole() {
        AlertDispatcher dispatcher = new AlertDispatcher();
        String payload = "{\"alertId\":\"ficc_wash_alert_1\"}";

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

            dispatcher.dispatch(payload);
        } finally {
            System.setOut(originalOut);
        }

        String consoleOutput = output.toString(StandardCharsets.UTF_8);
        assertTrue(consoleOutput.contains("----- FICC WASH TRADE ALERT -----"));
        assertTrue(consoleOutput.contains(payload));
    }

    @Test
    void dispatchRejectsNullPayload() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new AlertDispatcher().dispatch(null)
        );

        assertEquals("alertPayload is required", exception.getMessage());
    }
}
