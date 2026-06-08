package com.portfolio.ficc;

import com.portfolio.ficc.app.FiccRunRequestWorker;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class FiccWashModelApplicationTest {

    @Test
    void mainStartsSpringApplication() {
        String[] args = {"1", "NAMR", "2026-06-08"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            FiccWashModelApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(FiccWashModelApplication.class, args));
        }
    }

    @Test
    void commandLineRunnerDelegatesToRunRequestWorker() {
        FiccRunRequestWorker runRequestWorker = mock(FiccRunRequestWorker.class);
        FiccWashModelApplication application = new FiccWashModelApplication(runRequestWorker);
        String[] args = {"--queue"};

        application.run(args);

        verify(runRequestWorker).run(args);
    }
}
