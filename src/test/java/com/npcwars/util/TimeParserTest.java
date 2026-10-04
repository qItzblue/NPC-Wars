package com.npcwars.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimeParserTest {

    @Test
    void parsesSingleUnits() {
        assertEquals(30, TimeParser.parseSeconds("30s"));
        assertEquals(300, TimeParser.parseSeconds("5m"));
        assertEquals(3600, TimeParser.parseSeconds("1h"));
        assertEquals(86_400, TimeParser.parseSeconds("1d"));
    }

    @Test
    void bareNumberMeansSeconds() {
        assertEquals(90, TimeParser.parseSeconds("90"));
        assertEquals(0, TimeParser.parseSeconds("0"));
    }

    @Test
    void parsesCombinedUnitsAndIgnoresCaseAndWhitespace() {
        assertEquals(3600 + 30 * 60 + 15, TimeParser.parseSeconds("1h30m15s"));
        assertEquals(125, TimeParser.parseSeconds("  2M5S "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "5x", "m5", "1h 30m", "-5s", "1.5h", "5m30", "s"})
    void rejectsMalformedInput(String input) {
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parseSeconds(input));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parseSeconds(null));
    }

    @Test
    void rejectsOverflow() {
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parseSeconds("99999999999999999999s"));
        assertThrows(IllegalArgumentException.class, () -> TimeParser.parseSeconds("9223372036854775807d"));
    }

    @Test
    void formatsDurations() {
        assertEquals("0s", TimeParser.format(0));
        assertEquals("45s", TimeParser.format(45));
        assertEquals("5m", TimeParser.format(300));
        assertEquals("1h 5m 3s", TimeParser.format(3903));
        assertEquals("1d 2h", TimeParser.format(86_400 + 7_200));
    }

    @Test
    void formatRoundTrips() {
        for (long seconds : new long[] {1, 59, 60, 61, 3599, 3600, 86_399, 90_061}) {
            assertEquals(seconds, TimeParser.parseSeconds(TimeParser.format(seconds).replace(" ", "")));
        }
    }
}
