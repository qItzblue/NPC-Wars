package com.npcwars.command;

import com.npcwars.combat.FightManager;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.util.Completions;
import java.util.List;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** {@code /npcwars reload}, {@code save}, {@code status} and {@code debug}. */
final class AdminCommands {

    private AdminCommands() {
    }

    static void register(SubCommandRouter router) {
        router.register(reload()).register(save()).register(status()).register(debug());
    }

    private static SubCommand reload() {
        return SubCommand.of("reload", "npcplugin.reload", "reload",
                "Reload config.yml and kits.yml (NPC data stays as it is)", ctx -> {
                    ctx.plugin().reloadAll();
                    ctx.send("general.reloaded");
                });
    }

    private static SubCommand save() {
        return SubCommand.of("save", "npcplugin.reload", "save", "Write data.yml now", ctx -> {
            ctx.plugin().data().saveNow();
            ctx.send("general.saved");
        });
    }

    private static SubCommand status() {
        return SubCommand.of("status", "npcplugin.use", "status", "Show the fight state and NPC counts", ctx -> {
            FightManager fights = ctx.plugin().fights();
            int live = 0;
            for (Npc npc : ctx.plugin().npcs().all()) {
                if (npc.isLive()) {
                    live++;
                }
            }
            String state = switch (fights.state()) {
                case IDLE -> "idle";
                case COUNTDOWN -> "starts in " + fights.secondsUntilStart() + "s";
                case RUNNING -> "running for " + fights.secondsRunning() + "s";
            };
            ctx.send("status.line", Messages.var("state", state), Messages.var("total", ctx.plugin().npcs().count()),
                    Messages.var("live", live), Messages.var("fighters", fights.fighterCount()),
                    Messages.var("teams", ctx.plugin().teams().teams().size()),
                    Messages.var("paths", ctx.plugin().paths().queued()));
        });
    }

    private static SubCommand debug() {
        return SubCommand.of("debug", "npcplugin.debug", "debug [on|off|<id|look>]",
                "Turn plugin messages on or off, or print one NPC's movement and combat state", ctx -> {
                    if (ctx.size() == 0) {
                        ctx.plugin().messages().sendAlways(ctx.sender(), ctx.plugin().messages().debug() ? "debug.is-on" : "debug.is-off");
                        return;
                    }
                    String arg = ctx.arg(0).toLowerCase(java.util.Locale.ROOT);
                    if (arg.equals("on") || arg.equals("off")) {
                        ctx.plugin().messages().setDebug(arg.equals("on"));
                        ctx.plugin().data().requestSave();
                        ctx.plugin().messages().sendAlways(ctx.sender(), arg.equals("on") ? "debug.turned-on" : "debug.turned-off");
                        return;
                    }
                    Npc npc = Targets.single(ctx, ctx.arg(0));
                    Player body = npc.entity();
                    String position = "not spawned";
                    String health = "-";
                    if (body != null && body.isValid()) {
                        Location at = body.getLocation();
                        position = String.format(Locale.ROOT, "%.2f %.2f %.2f", at.getX(), at.getY(), at.getZ());
                        health = String.format(Locale.ROOT, "%.1f", body.getHealth());
                    }
                    ctx.send("status.debug", Messages.var("id", npc.id()), Messages.var("position", position),
                            Messages.var("health", health), Messages.var("controller", npc.controller().debugSummary()),
                            Messages.var("combat", ctx.plugin().fights().describeFighter(npc)));
                }).complete(ctx -> {
            List<String> options = new java.util.ArrayList<>(List.of("on", "off"));
            if (ctx.size() == 1) {
                options.addAll(ctx.plugin().npcs().idStrings());
            }
            return ctx.size() == 1 ? Completions.filter(options, ctx.last()) : List.of();
        });
    }
}
