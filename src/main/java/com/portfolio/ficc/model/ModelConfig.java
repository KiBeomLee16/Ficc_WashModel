package com.portfolio.ficc.model;

public record ModelConfig(
        int appId,
        int modelId,
        String region,
        String appName,
        String modelCode,
        String modelName,
        String modelClassName
) {

    public ModelConfig {
        if (appId <= 0) {
            throw new IllegalArgumentException("appId must be positive");
        }
        if (modelId <= 0) {
            throw new IllegalArgumentException("modelId must be positive");
        }
        region = requireText(region, "region").toUpperCase();
        appName = requireText(appName, "appName");
        modelCode = requireText(modelCode, "modelCode");
        modelName = requireText(modelName, "modelName");
        modelClassName = requireText(modelClassName, "modelClassName");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
