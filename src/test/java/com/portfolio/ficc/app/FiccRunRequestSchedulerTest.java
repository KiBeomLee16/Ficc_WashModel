package com.portfolio.ficc.app;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FiccRunRequestSchedulerTest {

    @Test
    void scanAndRunDelegatesToRunRequestWorker() {
        FiccRunRequestWorker runRequestWorker = mock(FiccRunRequestWorker.class);
        FiccRunRequestScheduler scheduler = new FiccRunRequestScheduler(runRequestWorker);

        scheduler.scanAndRun();

        verify(runRequestWorker).run();
    }
}
