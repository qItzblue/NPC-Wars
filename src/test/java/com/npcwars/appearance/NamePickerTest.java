package com.npcwars.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NamePickerTest {

    private final Random random = new Random(7);

    @Test
    void neverReusesATakenNameWhileFreeOnesRemain() {
        List<String> pool = List.of("Ash", "Kai", "Nova");
        Set<String> taken = new HashSet<>(Set.of("ash", "KAI"));
        for (int i = 0; i < 50; i++) {
            assertEquals("Nova", NamePicker.unique(pool, taken, random));
        }
    }

    @Test
    void handsOutEveryPoolNameBeforeNumbering() {
        List<String> pool = List.of("Ash", "Kai", "Nova");
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            taken.add(NamePicker.unique(pool, taken, random));
        }
        assertEquals(3, taken.size());
        String fourth = NamePicker.unique(pool, taken, random);
        assertFalse(pool.contains(fourth));
        assertTrue(fourth.matches("(Ash|Kai|Nova)2"), fourth);
    }

    @Test
    void numberedNamesStayUniqueAndWithinSixteenCharacters() {
        List<String> pool = List.of("AVeryLongNameIndeed");
        Set<String> taken = new HashSet<>(Set.of("AVeryLongNameIndeed"));
        for (int i = 0; i < 30; i++) {
            String name = NamePicker.unique(pool, taken, random);
            assertTrue(name.length() <= 16, name);
            assertTrue(taken.add(name.toLowerCase()), "duplicate " + name);
        }
    }

    @Test
    void emptyPoolFallsBackToPlayer() {
        assertEquals("Player2", NamePicker.unique(List.of(), Set.of(), random));
        assertNull(NamePicker.any(List.of(), random));
    }

    @Test
    void validationMatchesNameAndSkinRules() {
        assertTrue(Pools.isValid(Pools.Kind.NAMES, "Mining Mike"));
        assertFalse(Pools.isValid(Pools.Kind.SKINS, "Mining Mike"));
        assertTrue(Pools.isValid(Pools.Kind.SKINS, "jeb_"));
        assertFalse(Pools.isValid(Pools.Kind.NAMES, "ThisNameIsFarTooLongForMinecraft"));
        assertFalse(Pools.isValid(Pools.Kind.NAMES, " "));
        assertEquals(Pools.Kind.NAMES, Pools.Kind.parse("name"));
        assertEquals(Pools.Kind.SKINS, Pools.Kind.parse("skins"));
        assertNull(Pools.Kind.parse("hats"));
    }
}
