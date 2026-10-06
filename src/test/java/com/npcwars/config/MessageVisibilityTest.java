package com.npcwars.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MessageVisibilityTest {

    @Test
    void feedbackAndAnnouncementsAreQuietByDefault() {
        for (String key : new String[] {"npc.spawned", "npc.spawned-many", "npc.removed", "team.added", "fight.started",
                "fight.winner-team", "fight.countdown", "fight.start-ok", "route.created", "route.run-started",
                "route.finished", "life.enabled", "appearance.randomized", "kits.applied", "massaction.started",
                "general.reloaded", "general.saved", "npc.healed"}) {
            assertFalse(Messages.alwaysShown(key), key + " should be silent unless debug is on");
        }
    }

    @Test
    void errorsAnswersAndDebugAreAlwaysShown() {
        for (String key : new String[] {"general.usage", "general.no-permission", "general.players-only",
                "general.unknown-subcommand", "help.header", "help.entry", "npc.list-header", "npc.list-entry", "npc.info",
                "team.list-entry", "team.info", "route.list-entry", "route.info", "route.info-point", "life.status",
                "appearance.pool-list", "kits.list-entry", "massaction.list-flags", "status.line", "status.debug",
                "deps.installed", "deps.failed", "debug.turned-on", "debug.turned-off", "debug.is-on"}) {
            assertTrue(Messages.alwaysShown(key), key + " must always be shown");
        }
    }
}
