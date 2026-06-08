package com.portfolio.ficc.io;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AlertDispatcher {

    public void dispatch(String alertPayload) {
        Objects.requireNonNull(alertPayload, "alertPayload is required");

        System.out.println("----- FICC WASH TRADE ALERT -----");
        System.out.println(alertPayload);
        System.out.println();
    }
}
