package com.portfolio.ficc;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class FiccWashModelApplicationTest {

    @Test
    void mainStartsSpringApplication() {
        String[] args = {"1", "NAMR", "2026-06-08"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            FiccWashModelApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(FiccWashModelApplication.class, args));
        }
    }
}
