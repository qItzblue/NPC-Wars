package com.npcwars.action.builtin;

import com.npcwars.action.ActionContext;
import com.npcwars.action.ActionException;
import com.npcwars.action.NpcAction;
import com.npcwars.action.PreparedAction;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.util.Completions;
import java.util.List;
import org.bukkit.Location;

/** {@code spin [degrees-per-tick]}: turn on the spot for the duration (negative values spin the other way). */
public final class SpinAction implements NpcAction {

    @Override
    public String name() {
        return "spin";
    }

    @Override
    public String description() {
        return "Spin on the spot";
    }

    @Override
    public String usage() {
        return "[degrees-per-tick]";
    }

    @Override
    public PreparedAction prepare(ActionContext context, List<String> args) throws ActionException {
        float speed = 20f;
        if (!args.isEmpty()) {
            try {
                speed = Float.parseFloat(args.get(0));
            } catch (NumberFormatException ex) {
                throw new ActionException("action.bad-argument", Messages.var("argument", args.get(0)), Messages.var("action", name()));
            }
            if (speed == 0f || Math.abs(speed) > 180f) {
                throw new ActionException("action.bad-argument", Messages.var("argument", args.get(0)), Messages.var("action", name()));
            }
        }
        final float step = speed;
        final long fallback = context.plugin().settings().defaultActionSeconds * 20L;
        return new PreparedAction() {
            @Override
            public void start(Npc npc) {
                npc.controller().setAutoFace(false);
            }

            @Override
            public boolean tick(Npc npc, long elapsedTicks) {
                Location here = npc.currentLocation();
                if (here == null) {
                    return false;
                }
                npc.controller().setLook(here.getYaw() + step, 0f);
                return true;
            }

            @Override
            public void stop(Npc npc) {
                npc.controller().setAutoFace(true);
            }

            @Override
            public long defaultDurationTicks() {
                return fallback;
            }
        };
    }

    @Override
    public List<String> complete(ActionContext context, List<String> args) {
        return Completions.filter(List.of("10", "20", "45", "-20"), args.isEmpty() ? "" : args.get(0));
    }
}
