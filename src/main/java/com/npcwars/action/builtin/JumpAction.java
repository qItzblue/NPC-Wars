package com.npcwars.action.builtin;

import com.npcwars.action.ActionContext;
import com.npcwars.action.ActionException;
import com.npcwars.action.NpcAction;
import com.npcwars.action.PreparedAction;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.util.Completions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code jump [times]}: jump once, a number of times, or keep jumping for the whole {@code for=} duration. */
public final class JumpAction implements NpcAction {

    private static final int INTERVAL_TICKS = 12;

    @Override
    public String name() {
        return "jump";
    }

    @Override
    public List<String> aliases() {
        return List.of("hop");
    }

    @Override
    public String description() {
        return "Jump once, several times, or repeatedly with for=<time>";
    }

    @Override
    public String usage() {
        return "[times]";
    }

    @Override
    public PreparedAction prepare(ActionContext context, List<String> args) throws ActionException {
        int times = context.timed() ? Integer.MAX_VALUE : 1;
        if (!args.isEmpty()) {
            try {
                times = Integer.parseInt(args.get(0));
            } catch (NumberFormatException ex) {
                throw new ActionException("action.bad-argument", Messages.var("argument", args.get(0)), Messages.var("action", name()));
            }
            if (times <= 0 || times > 10_000) {
                throw new ActionException("action.bad-argument", Messages.var("argument", args.get(0)), Messages.var("action", name()));
            }
        }
        final int total = times;
        return new PreparedAction() {
            private final Map<Integer, Integer> remaining = new HashMap<>();

            @Override
            public void start(Npc npc) {
                remaining.put(npc.id(), total);
            }

            @Override
            public boolean tick(Npc npc, long elapsedTicks) {
                if (elapsedTicks % INTERVAL_TICKS != 0) {
                    return true;
                }
                npc.controller().requestJump();
                return remaining.merge(npc.id(), -1, Integer::sum) > 0;
            }

            @Override
            public void stop(Npc npc) {
                remaining.remove(npc.id());
            }
        };
    }

    @Override
    public List<String> complete(ActionContext context, List<String> args) {
        return Completions.filter(List.of("1", "3", "5", "10"), args.isEmpty() ? "" : args.get(args.size() - 1));
    }
}
