package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class TradeCsvReader {

	private static final int EXPECTED_COLUMNS = 16;

	public List<Trade> read(Path csvPath) throws IOException {
		try (var lines = Files.lines(csvPath)) {
			return lines.skip(1).filter(line -> !line.isBlank()).map(this::parseTrade).toList();
		}
	}

	private Trade parseTrade(String line) {
		List<String> columns = parseCsvLine(line);
		if (columns.size() != EXPECTED_COLUMNS) {
			throw new IllegalArgumentException(
					"Expected " + EXPECTED_COLUMNS + " columns but found " + columns.size() + ": " + line);
		}

		return new Trade(columns.get(0), LocalDateTime.parse(columns.get(1)), columns.get(2), columns.get(3),
				LocalDate.parse(columns.get(4)), columns.get(5), Side.valueOf(columns.get(6).trim().toUpperCase()),
				new BigDecimal(columns.get(7)), new BigDecimal(columns.get(8)), columns.get(9), columns.get(10),
				columns.get(11), columns.get(12), columns.get(13), columns.get(14), columns.get(15));
	}

	private List<String> parseCsvLine(String line) {
		List<String> columns = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean insideQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char character = line.charAt(i);
			if (character == '"') {
				if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
				} else {
					insideQuotes = !insideQuotes;
				}
			} else if (character == ',' && !insideQuotes) {
				columns.add(current.toString().trim());
				current.setLength(0);
			} else {
				current.append(character);
			}
		}

		columns.add(current.toString().trim());
		return columns;
	}
}
