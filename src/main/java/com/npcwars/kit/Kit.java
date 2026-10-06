package com.npcwars.kit;

import org.bukkit.Material;

/**
 * A kit offered by some {@link KitProvider}.
 *
 * @param providerId id of the provider that owns it ({@code builtin}, {@code essentials}, {@code cmi}, ...)
 * @param id         the kit's name inside that provider
 * @param icon       material shown in the kit menu
 */
public record Kit(String providerId, String id, Material icon) {
}
