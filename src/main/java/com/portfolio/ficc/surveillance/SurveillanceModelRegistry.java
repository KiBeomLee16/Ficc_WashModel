package com.portfolio.ficc.surveillance;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SurveillanceModelRegistry {

	private final Map<String, AbstractSurveillanceModel> modelsByClassName;

	public SurveillanceModelRegistry(List<AbstractSurveillanceModel> models) {
		Map<String, AbstractSurveillanceModel> registeredModels = new LinkedHashMap<>();
		for (AbstractSurveillanceModel model : models) {
			registeredModels.put(model.getClass().getName(), model);
		}
		this.modelsByClassName = Map.copyOf(registeredModels);
	}

	public AbstractSurveillanceModel getModel(String modelClassName) {
		if (modelClassName == null || modelClassName.isBlank()) {
			throw new IllegalArgumentException("modelClassName is required");
		}
		String normalizedClassName = modelClassName.trim();
		AbstractSurveillanceModel model = modelsByClassName.get(normalizedClassName);
		if (model == null) {
			throw new IllegalArgumentException("No registered surveillance model for class " + normalizedClassName);
		}
		return model;
	}
}
