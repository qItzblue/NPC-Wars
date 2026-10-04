package com.npcwars.kit;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * One source of kits (EssentialsX, CMI, the built-in kit file, ...). Providers are soft hooks: they are only asked for
 * kits while {@link #isAvailable()} is {@code true}, and any exception they throw is caught by the
 * {@link KitRegistry}, so a missing or incompatible kit plugin can never break {@code /kitall}.
 * <p>
 * To add another kit plugin, implement this interface and register it with {@link KitRegistry#register(KitProvider)}.
 */
public interface KitProvider {

    /** Stable lower-case id, e.g. {@code essentials}. */
    String id();

    /** Name shown in the kit menu, e.g. {@code EssentialsX}. */
    String displayName();

    /** @return {@code true} if the underlying plugin is installed and enabled (or the source is always there) */
    boolean isAvailable();

    /** @return every kit this provider currently offers */
    List<Kit> kits() throws Exception;

    /**
     * Resolves a kit to the items it contains. Must not run commands, charge money or start cooldowns.
     *
     * @param context the staff member who picked the kit (some plugins need a player to expand placeholders)
     */
    List<ItemStack> items(Kit kit, Player context) throws Exception;
}
