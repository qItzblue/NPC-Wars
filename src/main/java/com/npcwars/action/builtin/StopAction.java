package com.npcwars.action.builtin;

import com.npcwars.action.ActionContext;
import com.npcwars.action.NpcAction;
import com.npcwars.action.PreparedAction;
import com.npcwars.npc.Npc;
import java.util.List;

/** {@code stop}: cancels the current action and returns the NPC to a plain standing state. */
public final class StopAction implements NpcAction {

    @Override
    public String name() {
        return "stop";
    }

    @Override
    public List<String> aliases() {
        return List.of("halt", "idle", "reset");
    }

    @Override
    public String description() {
        return "Cancel the current action and stand still";
    }

    @Override
    public PreparedAction prepare(ActionContext context, List<String> args) {
        return new PreparedAction() {
            @Override
            public void start(Npc npc) {
                npc.controller().reset();
            }

            @Override
            public boolean tick(Npc npc, long elapsedTicks) {
                return false;
            }
        };
    }
}
