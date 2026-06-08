package com.portfolio.ficc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ficc.run")
public record RunConfig(
        int defaultAppId,
        String defaultRegion
) {

    public RunConfig {
        if (defaultAppId <= 0) {
            throw new IllegalArgumentException("ficc.run.default-app-id must be positive");
        }
        if (defaultRegion == null || defaultRegion.isBlank()) {
            throw new IllegalArgumentException("ficc.run.default-region is required");
        }
        defaultRegion = defaultRegion.trim().toUpperCase();
    }
}
