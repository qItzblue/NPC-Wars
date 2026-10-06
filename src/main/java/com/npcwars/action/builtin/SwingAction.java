package com.npcwars.action.builtin;

import com.npcwars.NpcWarsPlugin;
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
import org.bukkit.entity.Player;

/**
 * {@code swing} plays the arm animation; {@code attack} also hurts whatever enemy stands in reach in front of the
 * NPC (unless {@code massaction.attack-deals-damage} is off). Both repeat {@code times} times, one swing per interval.
 */
public final class SwingAction implements NpcAction {

    private static final int DEFAULT_INTERVAL_TICKS = 8;

    private final String name;
    private final List<String> aliases;
    private final boolean dealsDamage;
    private final String description;

    public SwingAction(String name, List<String> aliases, boolean dealsDamage, String description) {
        this.name = name;
        this.aliases = aliases;
        this.dealsDamage = dealsDamage;
        this.description = description;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String usage() {
        return "[times] [interval-ticks]";
    }

    @Override
    public PreparedAction prepare(ActionContext context, List<String> args) throws ActionException {
        int times = context.timed() ? Integer.MAX_VALUE : 1;
        int interval = DEFAULT_INTERVAL_TICKS;
        if (!args.isEmpty()) {
            times = parsePositive(args.get(0));
        }
        if (args.size() > 1) {
            interval = parsePositive(args.get(1));
        }
        return new Prepared(context.plugin(), times, interval);
    }

    @Override
    public List<String> complete(ActionContext context, List<String> args) {
        String last = args.isEmpty() ? "" : args.get(args.size() - 1);
        return Completions.filter(args.size() <= 1 ? List.of("1", "3", "10") : List.of("4", "8", "12", "20"), last);
    }

    private int parsePositive(String text) throws ActionException {
        try {
            int value = Integer.parseInt(text);
            if (value > 0 && value <= 100_000) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the error below
        }
        throw new ActionException("action.bad-argument", Messages.var("argument", text), Messages.var("action", name));
    }

    private final class Prepared implements PreparedAction {
        private final NpcWarsPlugin plugin;
        private final int times;
        private final int interval;
        private final Map<Integer, Integer> remaining = new HashMap<>();

        Prepared(NpcWarsPlugin plugin, int times, int interval) {
            this.plugin = plugin;
            this.times = times;
            this.interval = interval;
        }

        @Override
        public void start(Npc npc) {
            remaining.put(npc.id(), times);
        }

        @Override
        public boolean tick(Npc npc, long elapsedTicks) {
            if (elapsedTicks % interval != 0) {
                return true;
            }
            Player body = npc.entity();
            if (body == null) {
                return false;
            }
            body.swingMainHand();
            if (dealsDamage && plugin.settings().attackDealsDamage) {
                plugin.attacks().hitInFront(npc);
            }
            int left = remaining.merge(npc.id(), -1, Integer::sum);
            return left > 0;
        }

        @Override
        public void stop(Npc npc) {
            remaining.remove(npc.id());
        }
    }
}
