package com.npcwars.action;

import com.npcwars.NpcWarsPlugin;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Who is running the action and with what common options.
 *
 * @param explicitDurationTicks the {@code for=} value in ticks, or {@code 0} if the sender gave none
 */
public record ActionContext(NpcWarsPlugin plugin, CommandSender sender, long explicitDurationTicks) {

    /** @return the sender if it is a player, otherwise {@code null} */
    public Player player() {
        return sender instanceof Player player ? player : null;
    }

    /** @return a snapshot of the sending player's location, or {@code null} for the console */
    public Location origin() {
        Player player = player();
        return player == null ? null : player.getLocation();
    }

    /** @return {@code true} if the sender asked for a fixed duration */
    public boolean timed() {
        return explicitDurationTicks > 0;
    }
}
