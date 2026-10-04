package com.npcwars.command;

import com.npcwars.config.Messages;
import com.npcwars.kit.BuiltInKitProvider;
import com.npcwars.kit.Kit;
import com.npcwars.kit.KitProvider;
import com.npcwars.util.Completions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** {@code /npc kit create|delete|list|providers}: manage the built-in kits and inspect all kit sources. */
final class KitCommands {

    private KitCommands() {
    }

    static void register(SubCommandRouter root) {
        SubCommandRouter kit = new SubCommandRouter();
        kit.register(create()).register(delete()).register(list()).register(providers());
        root.register(SubCommand.of("kit", "npcplugin.kit", "kit <create|delete|list|providers>",
                        "Manage built-in kits", kit::dispatch)
                .complete(kit::complete));
    }

    private static SubCommand create() {
        return SubCommand.of("create", "npcplugin.kit", "create <name> [icon]",
                "Save your armor, off hand and hotbar as a built-in kit", ctx -> {
                    Player player = ctx.player();
                    String name = ctx.arg(0).toLowerCase(Locale.ROOT);
                    if (!BuiltInKitProvider.isValidName(name)) {
                        throw new CommandException("kits.invalid-name", Messages.var("input", ctx.arg(0)));
                    }
                    PlayerInventory inventory = player.getInventory();
                    List<ItemStack> items = new ArrayList<>();
                    for (ItemStack piece : inventory.getArmorContents()) {
                        addIfPresent(items, piece);
                    }
                    addIfPresent(items, inventory.getItemInOffHand());
                    for (int slot = 0; slot < 9; slot++) {
                        addIfPresent(items, inventory.getItem(slot));
                    }
                    if (items.isEmpty()) {
                        throw new CommandException("kits.nothing-to-save");
                    }
                    Material icon = items.get(0).getType();
                    if (ctx.size() > 1) {
                        Material chosen = Material.matchMaterial(ctx.arg(1));
                        if (chosen == null || !chosen.isItem() || chosen.isAir()) {
                            throw new CommandException("kits.invalid-icon", Messages.var("input", ctx.arg(1)));
                        }
                        icon = chosen;
                    }
                    boolean existed = ctx.plugin().kits().builtIn().exists(name);
                    if (!ctx.plugin().kits().builtIn().create(name, icon, items)) {
                        throw new CommandException("kits.save-failed");
                    }
                    ctx.send(existed ? "kits.updated" : "kits.created", Messages.var("kit", name), Messages.var("count", items.size()));
                }).minArgs(1).complete(ctx -> {
            if (ctx.size() == 1) {
                return Completions.filter(ctx.plugin().kits().builtIn().names(), ctx.last());
            }
            return List.of();
        });
    }

    private static SubCommand delete() {
        return SubCommand.of("delete", "npcplugin.kit", "delete <name>", "Delete a built-in kit", ctx -> {
            if (!ctx.plugin().kits().builtIn().delete(ctx.arg(0))) {
                throw new CommandException("kits.not-found", Messages.var("kit", ctx.arg(0)));
            }
            ctx.send("kits.deleted", Messages.var("kit", ctx.arg(0).toLowerCase(Locale.ROOT)));
        }).minArgs(1).complete(ctx -> ctx.size() == 1 ? Completions.filter(ctx.plugin().kits().builtIn().names(), ctx.last()) : List.of());
    }

    private static SubCommand list() {
        return SubCommand.of("list", "npcplugin.kit", "list", "List every kit from every provider", ctx -> {
            List<Kit> kits = ctx.plugin().kits().allKits();
            if (kits.isEmpty()) {
                ctx.send("kits.none");
                return;
            }
            ctx.send("kits.list-header", Messages.var("count", kits.size()));
            for (Kit kit : kits) {
                ctx.send("kits.list-entry", Messages.var("kit", kit.id()), Messages.var("provider", ctx.plugin().kits().providerName(kit)));
            }
        });
    }

    private static SubCommand providers() {
        return SubCommand.of("providers", "npcplugin.kit", "providers", "Show which kit plugins were detected", ctx -> {
            for (KitProvider provider : ctx.plugin().kits().providers()) {
                boolean available;
                try {
                    available = provider.isAvailable();
                } catch (RuntimeException | LinkageError ex) {
                    available = false;
                }
                ctx.send(available ? "kits.provider-available" : "kits.provider-missing",
                        Messages.var("provider", provider.displayName()));
            }
        });
    }

    private static void addIfPresent(List<ItemStack> items, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            items.add(item.clone());
        }
    }
}
