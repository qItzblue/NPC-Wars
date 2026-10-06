package com.npcwars.command;

import com.npcwars.config.Messages;
import com.npcwars.npc.Behavior;
import com.npcwars.npc.Npc;
import com.npcwars.util.Completions;
import java.util.ArrayList;
import java.util.List;

/** {@code /npcwars life}: switch NPCs between standing still and living like SMP players. */
final class LifeCommands {

    private LifeCommands() {
    }

    static void register(SubCommandRouter router) {
        router.register(life());
    }

    private static SubCommand life() {
        return SubCommand.of("life", "npcplugin.npc", "life <id|ids|selected|all|team:<n>> <on|off>  |  life status",
                "Make NPCs live like SMP players (wander, look at players, fidget, survive) or stand still", ctx -> {
                    if (ctx.arg(0).equalsIgnoreCase("status")) {
                        int life = 0;
                        for (Npc npc : ctx.plugin().npcs().all()) {
                            if (npc.behavior() == Behavior.LIFE) {
                                life++;
                            }
                        }
                        ctx.send("life.status", Messages.var("life", life), Messages.var("total", ctx.plugin().npcs().count()),
                                Messages.var("enabled", ctx.plugin().settings().lifeEnabled ? "on" : "off (life.enabled: false)"));
                        return;
                    }
                    if (ctx.size() < 2) {
                        throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " life <targets> <on|off>"));
                    }
                    String mode = ctx.arg(ctx.size() - 1).toLowerCase(java.util.Locale.ROOT);
                    boolean on;
                    switch (mode) {
                        case "on", "true", "enable" -> on = true;
                        case "off", "false", "disable" -> on = false;
                        default -> throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " life <targets> <on|off>"));
                    }
                    List<Npc> targets = Targets.manyOf(ctx, ctx.args().subList(0, ctx.size() - 1));
                    for (Npc npc : targets) {
                        npc.setBehavior(on ? Behavior.LIFE : Behavior.STILL);
                        if (!on) {
                            ctx.plugin().life().release(npc);
                        }
                    }
                    ctx.plugin().data().requestSave();
                    ctx.send(on ? "life.enabled" : "life.disabled", Messages.var("count", targets.size()));
                }).minArgs(1).complete(ctx -> {
                    List<String> options = new ArrayList<>(Targets.npcSuggestions(ctx));
                    options.addAll(List.of("on", "off", "status"));
                    return Completions.filter(options, ctx.last());
                });
    }
}
