package com.npcwars.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.npcwars.util.Completions;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArgFlagsAndCompletionsTest {

    @Test
    void separatesFlagsFromPositionalWords() {
        ArgFlags flags = ArgFlags.parse(List.of("walk", "north", "for=30s", "TEAM=2", "5"), Set.of("for", "team", "npc"));
        assertEquals(List.of("walk", "north", "5"), flags.positional());
        assertEquals("30s", flags.get("for"));
        assertEquals("2", flags.get("team"), "keys are case-insensitive");
        assertFalse(flags.has("npc"));
        assertNull(flags.get("npc"));
    }

    @Test
    void unknownKeysStayPositional() {
        ArgFlags flags = ArgFlags.parse(List.of("a=b", "=x", "skin="), Set.of("skin"));
        assertEquals(List.of("a=b", "=x"), flags.positional());
        assertTrue(flags.has("skin"));
        assertEquals("", flags.get("skin"));
    }

    @Test
    void completionsFilterByPrefixIgnoringCase() {
        List<String> names = List.of("Attack", "attack-all", "jump", "swim");
        assertEquals(List.of("Attack", "attack-all"), Completions.filter(names, "AT"));
        assertEquals(names, Completions.filter(names, ""));
        assertEquals(List.of(), Completions.filter(names, "zzz"));
        assertEquals(List.of("jump"), Completions.filter("j", "jump", "swim"));
    }
}
