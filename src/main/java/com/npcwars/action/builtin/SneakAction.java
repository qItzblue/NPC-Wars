package com.npcwars.action.builtin;

import com.npcwars.action.ActionContext;
import com.npcwars.action.ActionException;
import com.npcwars.action.NpcAction;
import com.npcwars.action.PreparedAction;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.util.Completions;
import java.util.List;
import java.util.Locale;

/** {@code sneak [on|off|toggle]} and {@code unsneak}: change the crouch stance, which stays until changed again. */
public final class SneakAction implements NpcAction {

    private final boolean unsneak;

    public SneakAction(boolean unsneak) {
        this.unsneak = unsneak;
    }

    @Override
    public String name() {
        return unsneak ? "unsneak" : "sneak";
    }

    @Override
    public List<String> aliases() {
        return unsneak ? List.of("stand") : List.of("crouch");
    }

    @Override
    public String description() {
        return unsneak ? "Stop sneaking" : "Start sneaking (on, off or toggle)";
    }

    @Override
    public String usage() {
        return unsneak ? "" : "[on|off|toggle]";
    }

    @Override
    public PreparedAction prepare(ActionContext context, List<String> args) throws ActionException {
        // 1 = on, 0 = off, -1 = toggle
        int mode = unsneak ? 0 : 1;
        if (!unsneak && !args.isEmpty()) {
            mode = switch (args.get(0).toLowerCase(Locale.ROOT)) {
                case "on", "true", "start" -> 1;
                case "off", "false", "stop" -> 0;
                case "toggle" -> -1;
                default -> throw new ActionException("action.bad-argument",
                        Messages.var("argument", args.get(0)), Messages.var("action", name()));
            };
        }
        final int chosen = mode;
        return new PreparedAction() {
            @Override
            public void start(Npc npc) {
                boolean value = chosen == -1 ? !npc.controller().isSneaking() : chosen == 1;
                npc.controller().setSneaking(value);
            }

            @Override
            public boolean tick(Npc npc, long elapsedTicks) {
                // The stance persists in the controller; nothing keeps running.
                return false;
            }
        };
    }

    @Override
    public List<String> complete(ActionContext context, List<String> args) {
        if (unsneak) {
            return List.of();
        }
        return Completions.filter(List.of("on", "off", "toggle"), args.isEmpty() ? "" : args.get(args.size() - 1));
    }
}
