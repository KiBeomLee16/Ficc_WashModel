package com.portfolio.ficc.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@ConditionalOnProperty(
        prefix = "surveillance.worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class FiccRunRequestScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FiccRunRequestScheduler.class);

    private final FiccRunRequestWorker runRequestWorker;

    public FiccRunRequestScheduler(FiccRunRequestWorker runRequestWorker) {
        this.runRequestWorker = Objects.requireNonNull(runRequestWorker, "runRequestWorker is required");
    }

    @Scheduled(
            initialDelayString = "${surveillance.worker.initial-delay-ms:3000}",
            fixedDelayString = "${surveillance.worker.fixed-delay-ms:5000}"
    )
    public void scanAndRun() {
        LOGGER.debug("Scanning surveillance run request queue.");
        runRequestWorker.run();
    }
}
