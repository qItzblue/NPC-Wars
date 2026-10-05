package com.npcwars.command;

import com.npcwars.chat.ChatService;
import com.npcwars.config.Messages;
import com.npcwars.npc.Npc;
import com.npcwars.util.Completions;
import java.util.List;

/** {@code /npc say}, {@code ask}, {@code persona} and {@code ai}: NPC chat and its AI backend. */
final class ChatCommands {

    private static final int MAX_PERSONA = 600;

    private ChatCommands() {
    }

    static void register(SubCommandRouter router) {
        router.register(say()).register(ask()).register(persona()).register(ai());
    }

    private static SubCommand say() {
        return SubCommand.of("say", "npcplugin.npc", "say <id> <message...>", "Make an NPC say something in chat", ctx -> {
            Npc npc = Targets.single(ctx, ctx.arg(0));
            String message = String.join(" ", ctx.args().subList(1, ctx.size())).strip();
            ctx.plugin().chat().say(npc, message);
        }).minArgs(2).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().npcs().idStrings(), ctx.last()) : List.of());
    }

    private static SubCommand ask() {
        return SubCommand.of("ask", "npcplugin.npc", "ask <id> <message...>",
                "Talk to an NPC as if you had said it in chat; its AI answers (works from the console)", ctx -> {
                    ChatService chat = ctx.plugin().chat();
                    if (!chat.isEnabled()) {
                        throw new CommandException("ai.disabled");
                    }
                    if (!chat.hasKey()) {
                        throw new CommandException("ai.no-key");
                    }
                    Npc npc = Targets.single(ctx, ctx.arg(0));
                    if (!npc.isLive()) {
                        throw new CommandException("ai.not-spawned", Messages.var("id", npc.id()));
                    }
                    String message = String.join(" ", ctx.args().subList(1, ctx.size())).strip();
                    if (!chat.respond(npc, ctx.sender().getName(), message)) {
                        throw new CommandException("ai.busy");
                    }
                    ctx.send("ai.asked", Messages.var("id", npc.id()));
                }).minArgs(2).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().npcs().idStrings(), ctx.last()) : List.of());
    }

    private static SubCommand persona() {
        return SubCommand.of("persona", "npcplugin.npc", "persona <id> [text...|clear]",
                "Show or set the personality an NPC chats with", ctx -> {
                    Npc npc = Targets.single(ctx, ctx.arg(0));
                    if (ctx.size() == 1) {
                        ctx.send("ai.persona-show", Messages.var("id", npc.id()),
                                Messages.var("persona", npc.persona() == null ? "(default from config.yml)" : npc.persona()));
                        return;
                    }
                    String text = String.join(" ", ctx.args().subList(1, ctx.size())).strip();
                    if (text.equalsIgnoreCase("clear") || text.equalsIgnoreCase("reset")) {
                        npc.setPersona(null);
                        ctx.plugin().data().requestSave();
                        ctx.send("ai.persona-cleared", Messages.var("id", npc.id()));
                        return;
                    }
                    if (text.length() > MAX_PERSONA) {
                        throw new CommandException("ai.persona-too-long", Messages.var("max", MAX_PERSONA));
                    }
                    npc.setPersona(text);
                    ctx.plugin().chat().forget(npc);
                    ctx.plugin().data().requestSave();
                    ctx.send("ai.persona-set", Messages.var("id", npc.id()));
                }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().npcs().idStrings(), ctx.last()) : List.of("clear"));
    }

    private static SubCommand ai() {
        return SubCommand.of("ai", "npcplugin.npc", "ai status", "Show whether the AI chat is on and how it is doing", ctx -> {
            ChatService chat = ctx.plugin().chat();
            ctx.send("ai.status", Messages.var("enabled", chat.isEnabled() ? "on" : "off (ai.enabled: false)"),
                    Messages.var("key", chat.hasKey() ? "set" : "missing"),
                    Messages.var("model", ctx.plugin().settings().aiModel),
                    Messages.var("inflight", chat.inFlight()), Messages.var("replies", chat.replies()),
                    Messages.var("failures", chat.failures()),
                    Messages.var("error", chat.lastError().isEmpty() ? "none" : chat.lastError()));
        }).complete(ctx -> ctx.size() == 1 ? Completions.filter(List.of("status"), ctx.last()) : List.of());
    }
}
