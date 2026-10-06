package com.npcwars.command;

import com.npcwars.config.Messages;
import com.npcwars.stick.StickBehavior;
import com.npcwars.stick.StickManager;
import com.npcwars.stick.StickMode;
import com.npcwars.util.Completions;
import java.util.List;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /npcwars stick}: get the dupe stick, or set its mode and behavior without clicking. */
final class StickCommands {

    private StickCommands() {
    }

    static void register(SubCommandRouter router) {
        router.register(SubCommand.of("stick", "npcplugin.stick", "stick [mode <copy|fill|single>] [then <stand|walk|march>] [clear]",
                "Give yourself the dupe stick (turns players into NPCs); set its mode or what the copies do", ctx -> {
                    Player player = ctx.player();
                    StickManager stick = ctx.plugin().stick();
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (ctx.size() == 0) {
                        player.getInventory().addItem(stick.create(player, StickMode.COPY_PLAYERS, StickBehavior.STAND));
                        ctx.send("stick.given");
                        return;
                    }
                    if (ctx.arg(0).equalsIgnoreCase("clear")) {
                        stick.clearSelection(player);
                        if (stick.isStick(held)) {
                            stick.setMode(player, held, stick.mode(held));
                        }
                        ctx.send("stick.cleared");
                        return;
                    }
                    if (!stick.isStick(held)) {
                        throw new CommandException("stick.hold-it");
                    }
                    if (ctx.size() < 2) {
                        throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " stick [mode <copy|fill|single>] [then <stand|walk>]"));
                    }
                    String what = ctx.arg(0).toLowerCase(Locale.ROOT);
                    switch (what) {
                        case "mode" -> {
                            StickMode mode = StickMode.parse(ctx.arg(1), null);
                            if (mode == null) {
                                throw new CommandException("stick.invalid-mode", Messages.var("input", ctx.arg(1)));
                            }
                            stick.setMode(player, held, mode);
                        }
                        case "then", "behavior" -> {
                            StickBehavior behavior = StickBehavior.parse(ctx.arg(1), null);
                            if (behavior == null) {
                                throw new CommandException("stick.invalid-behavior", Messages.var("input", ctx.arg(1)));
                            }
                            stick.setBehavior(player, held, behavior);
                        }
                        default -> throw new CommandException("general.usage", Messages.var("usage", ctx.label() + " stick [mode <copy|fill|single>] [then <stand|walk>]"));
                    }
                }).complete(ctx -> {
            if (ctx.size() == 1) {
                return Completions.filter(List.of("mode", "then", "clear"), ctx.last());
            }
            if (ctx.size() == 2 && ctx.arg(0).equalsIgnoreCase("mode")) {
                return Completions.filter(List.of("copy", "fill", "single"), ctx.last());
            }
            if (ctx.size() == 2) {
                return Completions.filter(List.of("stand", "walk", "march"), ctx.last());
            }
            return List.of();
        }));
    }
}
