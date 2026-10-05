package com.npcwars.command;

import com.npcwars.npc.Npc;
import com.npcwars.util.Completions;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

/** {@code /npc say}: make an NPC say something in public chat. */
final class ChatCommands {

    private ChatCommands() {
    }

    static void register(SubCommandRouter router) {
        router.register(SubCommand.of("say", "npcplugin.npc", "say <id> <message...>", "Make an NPC say something in chat", ctx -> {
            Npc npc = Targets.single(ctx, ctx.arg(0));
            String message = String.join(" ", ctx.args().subList(1, ctx.size())).strip();
            String name = npc.label() == null || npc.label().isBlank() ? "NPC " + npc.id() : npc.label();
            Bukkit.broadcast(Component.text("<" + name + "> " + message));
            if (npc.isLive()) {
                npc.entity().swingMainHand();
            }
        }).minArgs(2).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().npcs().idStrings(), ctx.last()) : List.of()));
    }
}
