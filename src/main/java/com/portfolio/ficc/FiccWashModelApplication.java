package com.portfolio.ficc;

import com.portfolio.ficc.app.FiccRunRequestWorker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FiccWashModelApplication implements CommandLineRunner {

    private final FiccRunRequestWorker runRequestWorker;

    public FiccWashModelApplication(FiccRunRequestWorker runRequestWorker) {
        this.runRequestWorker = runRequestWorker;
    }

    public static void main(String[] args) {
        SpringApplication.run(FiccWashModelApplication.class, args);
    }

    @Override
    public void run(String... args) {
        runRequestWorker.run(args);
    }
}
