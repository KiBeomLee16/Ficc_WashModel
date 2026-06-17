package com.portfolio.ficc;

import com.portfolio.ficc.io.AlertDispatcher;
import com.portfolio.ficc.io.AlertHistoryRepository;
import com.portfolio.ficc.io.CalibrationResultRepository;
import com.portfolio.ficc.io.DatabaseConfig;

public final class TestConfigs {

	public static AlertDispatcher alertDispatcher() {
		DatabaseConfig databaseConfig = new DatabaseConfig("jdbc:mysql://unit-test-host:3306/unit", "unit", "");
		return new AlertDispatcher(new AlertHistoryRepository(databaseConfig),
				new CalibrationResultRepository(databaseConfig));
	}

	private TestConfigs() {
	}
}
