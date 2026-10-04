package com.npcwars.command;

import com.npcwars.NpcWarsPlugin;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/** The {@code /npc} root command: wires every sub-command group into one {@link SubCommandRouter}. */
public final class NpcCommand implements TabExecutor {

    private final NpcWarsPlugin plugin;
    private final SubCommandRouter router = new SubCommandRouter();

    public NpcCommand(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        NpcManagementCommands.register(router);
        TeamCommands.register(router);
        FightCommands.register(router);
        KitCommands.register(router);
        AdminCommands.register(router);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        router.dispatch(new CommandContext(plugin, sender, label, List.of(args)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return router.complete(new CommandContext(plugin, sender, alias, List.of(args)));
    }
}
