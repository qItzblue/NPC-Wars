package com.npcwars;

import com.npcwars.action.ActionRegistry;
import com.npcwars.action.ActionRunner;
import com.npcwars.action.builtin.BuiltInActions;
import com.npcwars.combat.AttackExecutor;
import com.npcwars.combat.Factions;
import com.npcwars.combat.FightManager;
import com.npcwars.command.KitAllCommand;
import com.npcwars.command.MassActionCommand;
import com.npcwars.command.NpcCommand;
import com.npcwars.config.DataStore;
import com.npcwars.config.Messages;
import com.npcwars.config.Settings;
import com.npcwars.gui.BaseGui;
import com.npcwars.gui.GuiListener;
import com.npcwars.kit.KitApplier;
import com.npcwars.dependency.DependencyInstaller;
import com.npcwars.dependency.PluginSource;
import com.npcwars.kit.KitRegistry;
import com.npcwars.listener.NpcDamageListener;
import com.npcwars.listener.NpcInteractListener;
import com.npcwars.listener.NpcLifecycleListener;
import com.npcwars.npc.Npc;
import com.npcwars.npc.NpcManager;
import com.npcwars.path.PathService;
import com.npcwars.team.Team;
import com.npcwars.team.TeamManager;
import com.npcwars.team.TeamStorage;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * NPC-Wars: equippable player-like NPCs (Paper {@code Mannequin} entities) with kits, mass actions, numbered teams and
 * team-versus-team fights.
 * <p>
 * Everything runs on the main thread from a single one-tick loop ({@link #tickLoop()}); the only asynchronous work is
 * writing data.yml, and that work never touches the Bukkit API. Other plugins can reach the extension points through
 * {@link #actions()} (new mass actions) and {@link #kits()} (new kit sources).
 */
public final class NpcWarsPlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private DataStore data;
    private TeamManager teams;
    private NpcManager npcs;
    private PathService paths;
    private ActionRegistry actions;
    private ActionRunner runner;
    private KitRegistry kits;
    private KitApplier kitApplier;
    private Factions factions;
    private AttackExecutor attacks;
    private FightManager fights;
    private DependencyInstaller dependencies;
    private BukkitTask loop;
    private long tick;
    private final java.util.Map<String, Long> lastErrorTick = new java.util.HashMap<>();

    @Override
    public void onEnable() {
        if (!mannequinSupported()) {
            getLogger().severe("This server has no Mannequin entity. NPC-Wars needs Paper 1.21.9 or newer; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        settings = new Settings(this);
        messages = new Messages(this);
        data = new DataStore(this);
        teams = new TeamManager();
        npcs = new NpcManager(this);
        paths = new PathService(settings);
        factions = new Factions(this);
        attacks = new AttackExecutor(this);
        actions = new ActionRegistry();
        BuiltInActions.registerAll(actions);
        runner = new ActionRunner(this);
        kits = new KitRegistry(this);
        kitApplier = new KitApplier(this);
        fights = new FightManager(this);
        dependencies = new DependencyInstaller(this);

        loadData();
        data.setSnapshotter(this::snapshot);
        teams.setChangeListener(data::requestSave);

        registerListeners();
        registerCommands();
        loop = Bukkit.getScheduler().runTaskTimer(this, this::tickLoop, 1L, 1L);
        for (var world : Bukkit.getWorlds()) {
            npcs.onWorldLoad(world);
        }
        downloadMissingPlugins();
        getLogger().info("Loaded " + npcs.count() + " NPC(s) and " + teams.teams().size() + " team(s).");
    }

    @Override
    public void onDisable() {
        if (loop != null) {
            loop.cancel();
        }
        if (fights != null) {
            fights.shutdown();
        }
        if (runner != null) {
            runner.stopAll();
        }
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof BaseGui) {
                player.closeInventory();
            }
        }
        if (data != null && npcs != null) {
            data.saveNow();
        }
        Bukkit.getScheduler().cancelTasks(this);
        if (npcs != null) {
            npcs.shutdown();
        }
        if (paths != null) {
            paths.shutdown();
        }
    }

    /** Downloads Citizens / EssentialsX when {@code auto-download} asks for it; they load on the next server start. */
    private void downloadMissingPlugins() {
        if (!settings.autoDownloadOnStartup) {
            return;
        }
        List<PluginSource> wanted = new ArrayList<>();
        for (PluginSource source : dependencies.sources()) {
            if (settings.autoDownloadPlugins.contains(source.configKey())) {
                wanted.add(source);
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        dependencies.install(wanted, outcomes -> {
            for (DependencyInstaller.Outcome outcome : outcomes) {
                switch (outcome.status()) {
                    case INSTALLED -> getLogger().warning("Downloaded " + outcome.source().pluginName() + " ("
                            + outcome.detail() + "). Restart the server to load it.");
                    case UNAVAILABLE -> getLogger().info("Could not auto-download " + outcome.source().pluginName()
                            + ": " + outcome.detail());
                    case FAILED -> getLogger().warning("Could not auto-download " + outcome.source().pluginName()
                            + ": " + outcome.detail());
                    case PRESENT -> { }
                }
            }
        });
    }

    /** Reloads config.yml, messages and kits.yml. NPC and team data stay as they are in memory. */
    public void reloadAll() {
        reloadConfig();
        settings.reload();
        messages.reload();
        kits.reload();
        paths.shutdown();
    }

    // ---------------------------------------------------------------- accessors

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public DataStore data() {
        return data;
    }

    public TeamManager teams() {
        return teams;
    }

    public NpcManager npcs() {
        return npcs;
    }

    public PathService paths() {
        return paths;
    }

    /** Registry of {@code /massaction} actions; register your own {@code NpcAction} here. */
    public ActionRegistry actions() {
        return actions;
    }

    public ActionRunner runner() {
        return runner;
    }

    /** Registry of kit sources; register your own {@code KitProvider} here. */
    public DependencyInstaller dependencies() {
        return dependencies;
    }

    public KitRegistry kits() {
        return kits;
    }

    public KitApplier kitApplier() {
        return kitApplier;
    }

    public Factions factions() {
        return factions;
    }

    public AttackExecutor attacks() {
        return attacks;
    }

    public FightManager fights() {
        return fights;
    }

    /** @return the plugin's own tick counter (advances once per server tick while the plugin is enabled) */
    public long currentTick() {
        return tick;
    }

    // ---------------------------------------------------------------- internals

    private static boolean mannequinSupported() {
        try {
            Class.forName("org.bukkit.entity.Mannequin");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private void loadData() {
        YamlConfiguration yaml = data.load();
        npcs.load(yaml);
        TeamStorage.load(teams, yaml, getLogger());
        // Forget team entries for NPCs that no longer exist (for example data.yml edited by hand).
        for (Team team : new ArrayList<>(teams.teams())) {
            for (int npcId : new ArrayList<>(team.npcIds())) {
                if (npcs.get(npcId) == null) {
                    teams.removeNpc(npcId);
                }
            }
        }
    }

    private YamlConfiguration snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        npcs.save(yaml);
        TeamStorage.save(teams, yaml);
        return yaml;
    }

    private void registerListeners() {
        var manager = getServer().getPluginManager();
        manager.registerEvents(new NpcInteractListener(this), this);
        manager.registerEvents(new NpcDamageListener(this), this);
        manager.registerEvents(new NpcLifecycleListener(this), this);
        manager.registerEvents(new GuiListener(), this);
    }

    private void registerCommands() {
        bind("npc", new NpcCommand(this));
        bind("kitall", new KitAllCommand(this));
        bind("massaction", new MassActionCommand(this));
    }

    private <T extends org.bukkit.command.TabExecutor> void bind(String name, T handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml");
            return;
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    /** The single per-tick driver: path searches, actions, the fight AI, then NPC movement, then bookkeeping. */
    private void tickLoop() {
        tick++;
        // Each stage is isolated, so a failure in one (say the fight AI) never freezes movement or respawning.
        stage("path service", () -> paths.tick(tick));
        stage("mass actions", () -> runner.tick(tick));
        stage("fight manager", () -> fights.tick(tick));
        stage("NPC movement", () -> npcs.tickControllers(tick));
        stage("NPC maintenance", () -> npcs.maintenance(tick));
    }

    private void stage(String name, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException ex) {
            reportError(name, ex);
        }
    }

    /**
     * Logs an exception from the tick loop. Each place is reported at most once every 10 seconds, so a fault that
     * repeats every tick cannot flood the console, but is still visible.
     */
    public void reportError(String where, Throwable error) {
        Long last = lastErrorTick.get(where);
        if (last != null && tick - last < 200) {
            return;
        }
        lastErrorTick.put(where, tick);
        getLogger().log(java.util.logging.Level.SEVERE, "Error in " + where + " (further repeats are hidden for 10s)", error);
    }

    /** Lets other code identify NPC entities without depending on internals. */
    public Npc npcOf(org.bukkit.entity.Entity entity) {
        return npcs.byEntity(entity);
    }
}
