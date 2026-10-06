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
import com.npcwars.appearance.Pools;
import com.npcwars.config.DataStore;
import com.npcwars.route.RouteManager;
import com.npcwars.route.RouteRunner;
import com.npcwars.route.RouteStorage;
import com.npcwars.life.LifeAi;
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
 * NPC-Wars: equippable player NPCs (Citizens bodies) with kits, mass actions, numbered teams and
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
    private Pools pools;
    private LifeAi life;
    private com.npcwars.stick.StickManager stick;
    private final PlacedBlocks placed = new PlacedBlocks();
    private final java.util.Set<java.util.UUID> explosives = new java.util.HashSet<>();
    private RouteManager routes;
    private RouteRunner routeRunner;
    private DependencyInstaller dependencies;
    private BukkitTask loop;
    private long tick;
    private final java.util.Map<String, Long> lastErrorTick = new java.util.HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(this);
        dependencies = new DependencyInstaller(this);
        if (!citizensReady()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        messages = new Messages(this);
        data = new DataStore(this);
        teams = new TeamManager();
        npcs = new NpcManager(this);
        pools = new Pools(this);
        paths = new PathService(settings);
        factions = new Factions(this);
        attacks = new AttackExecutor(this);
        actions = new ActionRegistry();
        BuiltInActions.registerAll(actions);
        runner = new ActionRunner(this);
        kits = new KitRegistry(this);
        kitApplier = new KitApplier(this);
        fights = new FightManager(this);
        life = new LifeAi(this);
        stick = new com.npcwars.stick.StickManager(this);
        routes = new RouteManager();
        routeRunner = new RouteRunner(this);

        loadData();
        data.setSnapshotter(this::snapshot);
        teams.setChangeListener(data::requestSave);
        routes.setChangeListener(data::requestSave);

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
        if (routeRunner != null) {
            routeRunner.cancelAll();
        }
        placed.restoreAll();
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
                            + outcome.detail() + "). Restart the server to load it."
                            + ("Citizens".equals(outcome.source().pluginName())
                                    ? " Citizens also uses /npcwars, so use /npcwars for NPC-Wars." : ""));
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
        pools.reload();
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

    public RouteManager routes() {
        return routes;
    }

    public RouteRunner routeRunner() {
        return routeRunner;
    }

    /** Temporary blocks NPCs placed in fights; they are removed again, and all restored on shutdown. */
    public PlacedBlocks placed() {
        return placed;
    }

    /** Entities (TNT, end crystals) lit by NPCs, so their explosions can be kept from breaking blocks. */
    public java.util.Set<java.util.UUID> explosives() {
        return explosives;
    }

    public com.npcwars.stick.StickManager stick() {
        return stick;
    }

    public LifeAi life() {
        return life;
    }

    /** @return {@code true} if a fight, a mass action or a route currently controls this NPC (life AI stays out) */
    public boolean isNpcBusy(Npc npc) {
        return fights.hasFighter(npc) || runner.isRunning(npc) || routeRunner.isOnRoute(npc) || stick.isWalking(npc);
    }

    public Pools pools() {
        return pools;
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

    /**
     * NPC bodies are Citizens player NPCs, so Citizens has to be running. If it is not installed it is downloaded once
     * (when {@code auto-download} allows) and the server needs a restart; if it is installed but did not start, the most
     * likely cause is a build that does not support this Minecraft version.
     */
    private boolean citizensReady() {
        org.bukkit.plugin.Plugin citizens = getServer().getPluginManager().getPlugin("Citizens");
        if (citizens != null && citizens.isEnabled()) {
            return true;
        }
        if (citizens != null) {
            getLogger().severe("Citizens is installed but did not start. NPC-Wars is built for Citizens "
                    + com.npcwars.dependency.CitizensSource.VERSION + " (build " + com.npcwars.dependency.CitizensSource.BUILD
                    + "); a newer Citizens build only supports the newest Minecraft version. Check Citizens' own error above.");
            return false;
        }
        getLogger().severe("NPC-Wars needs the Citizens plugin for its NPC bodies, and it is not installed.");
        PluginSource source = dependencies.source("citizens");
        if (settings.autoDownloadEnabled && settings.autoDownloadPlugins.contains("citizens") && source != null) {
            for (DependencyInstaller.Outcome outcome : dependencies.installBlocking(List.of(source))) {
                if (outcome.status() == DependencyInstaller.Status.INSTALLED) {
                    getLogger().severe("Downloaded " + outcome.detail() + " into the plugins folder. Restart the server to start NPC-Wars.");
                } else {
                    getLogger().severe("Could not download Citizens (" + outcome.status() + " " + outcome.detail()
                            + "). Install Citizens " + com.npcwars.dependency.CitizensSource.VERSION + " build "
                            + com.npcwars.dependency.CitizensSource.BUILD + " by hand.");
                }
            }
        } else {
            getLogger().severe("Install Citizens " + com.npcwars.dependency.CitizensSource.VERSION + " (build "
                    + com.npcwars.dependency.CitizensSource.BUILD + ") or enable auto-download in config.yml.");
        }
        return false;
    }

    private void loadData() {
        YamlConfiguration yaml = data.load();
        npcs.load(yaml);
        messages.setDebug(yaml.getBoolean("debug", false));
        TeamStorage.load(teams, yaml, getLogger());
        RouteStorage.load(routes, yaml, getLogger());
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
        yaml.set("debug", messages.debug());
        npcs.save(yaml);
        TeamStorage.save(teams, yaml);
        RouteStorage.save(routes, yaml);
        return yaml;
    }

    private void registerListeners() {
        var manager = getServer().getPluginManager();
        manager.registerEvents(new NpcInteractListener(this), this);
        manager.registerEvents(new NpcDamageListener(this), this);
        manager.registerEvents(new NpcLifecycleListener(this), this);
        manager.registerEvents(new GuiListener(), this);
        manager.registerEvents(new com.npcwars.listener.StickListener(this), this);
        manager.registerEvents(new com.npcwars.listener.CombatItemListener(this), this);
    }

    private void registerCommands() {
        bind("npcwars", new NpcCommand(this));
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
        stage("routes", () -> routeRunner.tick(tick));
        stage("fight manager", () -> fights.tick(tick));
        stage("placed blocks", () -> placed.tick(tick));
        stage("dupe walkers", () -> stick.tick(tick));
        stage("life AI", () -> life.tick(tick));
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
