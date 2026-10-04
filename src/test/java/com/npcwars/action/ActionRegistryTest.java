package com.npcwars.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionRegistryTest {

    private static NpcAction action(String name, String... aliases) {
        return new NpcAction() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<String> aliases() {
                return List.of(aliases);
            }

            @Override
            public String description() {
                return "test action " + name;
            }

            @Override
            public PreparedAction prepare(ActionContext context, List<String> args) {
                return (npc, elapsed) -> false;
            }
        };
    }

    private ActionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ActionRegistry();
    }

    @Test
    void findsActionsByNameAndAliasIgnoringCase() {
        NpcAction walk = action("walk", "forward", "stroll");
        registry.register(walk);
        assertSame(walk, registry.find("walk"));
        assertSame(walk, registry.find("WALK"));
        assertSame(walk, registry.find("Stroll"));
        assertNull(registry.find("run"));
    }

    @Test
    void rejectsDuplicateNamesAndAliases() {
        registry.register(action("walk", "go"));
        assertThrows(IllegalArgumentException.class, () -> registry.register(action("walk")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(action("run", "go")));
        assertNull(registry.find("run"), "a rejected registration must leave no trace");
    }

    @Test
    void tabCompletionNamesIncludeEveryActionAndAlias() {
        registry.register(action("jump", "hop"));
        registry.register(action("attack", "hit"));
        assertEquals(List.of("attack", "hit", "hop", "jump"), registry.allNames());
        assertEquals(List.of("jump", "attack"), registry.names(), "canonical names keep registration order");
    }

    @Test
    void unregisterRemovesTheActionAndItsAliases() {
        registry.register(action("jump", "hop"));
        assertTrue(registry.unregister("JUMP"));
        assertNull(registry.find("jump"));
        assertNull(registry.find("hop"));
        assertFalse(registry.unregister("jump"));
        registry.register(action("jump"));
        assertTrue(registry.names().contains("jump"));
    }

    @Test
    void newActionsAppearWithoutTouchingTheCommand() {
        int before = registry.all().size();
        registry.register(action("dance"));
        assertEquals(before + 1, registry.all().size());
        assertTrue(registry.allNames().contains("dance"));
    }
}
