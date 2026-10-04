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
import org.bukkit.Location;

/** {@code look}: turn to face the sender, a compass direction, up/down, or an explicit yaw and pitch. */
public final class LookAction implements NpcAction {

    @Override
    public String name() {
        return "look";
    }

    @Override
    public List<String> aliases() {
        return List.of("face");
    }

    @Override
    public String description() {
        return "Face me, a compass direction, up/down, or <yaw> [pitch]";
    }

    @Override
    public String usage() {
        return "<me|north|south|east|west|up|down|yaw [pitch]>";
    }

    @Override
    public PreparedAction prepare(ActionContext context, List<String> args) throws ActionException {
        if (args.isEmpty()) {
            throw new ActionException("action.missing-argument", Messages.var("action", name()), Messages.var("usage", usage()));
        }
        String first = args.get(0).toLowerCase(Locale.ROOT);
        switch (first) {
            case "me", "here", "player" -> {
                Location origin = context.player() == null ? null : context.player().getEyeLocation();
                if (origin == null) {
                    throw new ActionException("action.need-player-in-world", Messages.var("direction", first));
                }
                return once(npc -> npc.controller().face(origin));
            }
            case "north" -> {
                return once(npc -> npc.controller().setLook(180f, 0f));
            }
            case "south" -> {
                return once(npc -> npc.controller().setLook(0f, 0f));
            }
            case "east" -> {
                return once(npc -> npc.controller().setLook(-90f, 0f));
            }
            case "west" -> {
                return once(npc -> npc.controller().setLook(90f, 0f));
            }
            case "up" -> {
                return once(npc -> npc.controller().setLook(currentYaw(npc), -90f));
            }
            case "down" -> {
                return once(npc -> npc.controller().setLook(currentYaw(npc), 90f));
            }
            default -> {
                try {
                    float yaw = Float.parseFloat(args.get(0));
                    float pitch = args.size() > 1 ? Float.parseFloat(args.get(1)) : 0f;
                    float clamped = Math.max(-90f, Math.min(90f, pitch));
                    return once(npc -> npc.controller().setLook(yaw, clamped));
                } catch (NumberFormatException ex) {
                    throw new ActionException("action.bad-argument", Messages.var("argument", args.get(0)), Messages.var("action", name()));
                }
            }
        }
    }

    @Override
    public List<String> complete(ActionContext context, List<String> args) {
        if (args.size() > 1) {
            return List.of();
        }
        return Completions.filter(List.of("me", "north", "south", "east", "west", "up", "down"), args.isEmpty() ? "" : args.get(0));
    }

    private static float currentYaw(Npc npc) {
        Location here = npc.currentLocation();
        return here == null ? 0f : here.getYaw();
    }

    private static PreparedAction once(java.util.function.Consumer<Npc> apply) {
        return new PreparedAction() {
            @Override
            public void start(Npc npc) {
                apply.accept(npc);
            }

            @Override
            public boolean tick(Npc npc, long elapsedTicks) {
                return false;
            }
        };
    }
}
