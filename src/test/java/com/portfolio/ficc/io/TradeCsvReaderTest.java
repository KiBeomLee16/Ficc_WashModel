package com.portfolio.ficc.io;

import com.portfolio.ficc.model.Side;
import com.portfolio.ficc.model.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeCsvReaderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void readParsesTradeCsvFile() throws IOException {
        Path csvPath = tempDirectory.resolve("trades.csv");
        Files.writeString(csvPath, """
                tradeId,timestamp,assetClass,instrumentId,maturity,currency,side,quantity,price,counterpartyId,accountId,beneficialOwner,traderId,desk,book,broker
                T-CSV-001,2026-06-08T09:30:00,Fixed Income,UST-10Y,2036-05-15,USD,buy,10000000,99.8125,CP-ALPHA,ACCT-1,"Alpha, Capital Fund",TRDR-17,Rates,GOVT-RATES-A,BRKR-NY-1
                """);

        List<Trade> trades = new TradeCsvReader().read(csvPath);

        assertEquals(1, trades.size());

        Trade trade = trades.get(0);
        assertEquals("T-CSV-001", trade.tradeId());
        assertEquals(LocalDateTime.of(2026, 6, 8, 9, 30), trade.timestamp());
        assertEquals("Fixed Income", trade.assetClass());
        assertEquals("UST-10Y", trade.instrumentId());
        assertEquals(LocalDate.of(2036, 5, 15), trade.maturity());
        assertEquals("USD", trade.currency());
        assertEquals(Side.BUY, trade.side());
        assertEquals(new BigDecimal("10000000"), trade.quantity());
        assertEquals(new BigDecimal("99.8125"), trade.price());
        assertEquals("CP-ALPHA", trade.counterpartyId());
        assertEquals("Alpha, Capital Fund", trade.beneficialOwner());
    }

    @Test
    void readRejectsRowsWithUnexpectedColumnCount() throws IOException {
        Path csvPath = tempDirectory.resolve("bad-trades.csv");
        Files.writeString(csvPath, """
                tradeId,timestamp,assetClass,instrumentId,maturity,currency,side,quantity,price,counterpartyId,accountId,beneficialOwner,traderId,desk,book,broker
                T-BAD-001,2026-06-08T09:30:00,Fixed Income
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new TradeCsvReader().read(csvPath)
        );

        assertEquals("Expected 16 columns but found 3: T-BAD-001,2026-06-08T09:30:00,Fixed Income",
                exception.getMessage());
    }
}
