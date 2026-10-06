package com.npcwars.action.builtin;

import com.npcwars.action.ActionContext;
import com.npcwars.action.ActionException;
import com.npcwars.action.NpcAction;
import com.npcwars.action.PreparedAction;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.npc.control.NpcController;
import com.npcwars.util.Completions;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** {@code follow [player]}: every NPC chases a player (default: the sender), using pathfinding, until the time is up. */
public final class FollowAction implements NpcAction {

    private static final int REFRESH_TICKS = 10;
    private static final double SPRINT_BEYOND = 8.0;
    private static final double KEEP_DISTANCE = 2.5;

    @Override
    public String name() {
        return "follow";
    }

    @Override
    public String description() {
        return "Follow a player (default: you)";
    }

    @Override
    public String usage() {
        return "[player]";
    }

    @Override
    public PreparedAction prepare(ActionContext context, List<String> args) throws ActionException {
        Player target;
        if (args.isEmpty()) {
            target = context.player();
            if (target == null) {
                throw new ActionException("action.missing-argument", Messages.var("action", name()), Messages.var("usage", usage()));
            }
        } else {
            target = Bukkit.getPlayerExact(args.get(0));
            if (target == null) {
                throw new ActionException("action.player-offline", Messages.var("player", args.get(0)));
            }
        }
        final UUID targetId = target.getUniqueId();
        final long fallback = context.plugin().settings().defaultActionSeconds * 20L;
        return new PreparedAction() {
            @Override
            public boolean tick(Npc npc, long elapsedTicks) {
                if (elapsedTicks % REFRESH_TICKS != 0) {
                    return true;
                }
                Player player = Bukkit.getPlayer(targetId);
                Location here = npc.currentLocation();
                if (player == null || !player.isOnline() || here == null) {
                    return false;
                }
                if (player.getWorld() != here.getWorld()) {
                    return true;
                }
                NpcController.Gait gait = player.getLocation().distance(here) > SPRINT_BEYOND
                        ? NpcController.Gait.SPRINT : NpcController.Gait.WALK;
                npc.controller().moveTo(player.getLocation(), gait, KEEP_DISTANCE);
                return true;
            }

            @Override
            public void stop(Npc npc) {
                npc.controller().stop();
            }

            @Override
            public long defaultDurationTicks() {
                return fallback;
            }
        };
    }

    @Override
    public List<String> complete(ActionContext context, List<String> args) {
        List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return Completions.filter(names, args.isEmpty() ? "" : args.get(0));
    }
}
