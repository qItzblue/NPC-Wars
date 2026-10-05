package com.npcwars.config;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.util.TimeParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed snapshot of config.yml (everything except the {@code messages} section). Fields are public and read-only by
 * convention; {@link #reload()} refreshes them in place, so hold on to the {@code Settings} object, not its values.
 */
public final class Settings {

    /** What NPCs without a team do during a fight. */
    public enum UnteamedMode { FREE_FOR_ALL, IDLE }

    /** What happens to an NPC that dies during a fight. */
    public enum DeathPolicy { REMOVE, RESPAWN_ON_FIGHT_END, RESPAWN_DELAY }

    /** Who receives fight announcements. */
    public enum Announce { ALL, STAFF, NONE }

    private final NpcWarsPlugin plugin;

    // npc
    public boolean showNametag;
    public boolean invulnerableWhenIdle;
    public int maxNpcs;
    public String defaultSkin;
    public double maxHealth;
    public int idleRespawnDelayTicks;

    // movement
    public double walkSpeed;
    public double sprintSpeed;
    public double sneakSpeed;
    public double swimSpeed;
    public int knockbackGraceTicks;
    public boolean autoJump;

    // pathfinding
    public boolean pathfindingEnabled;
    public int pathMaxNodes;
    public int pathMaxRange;
    public long pathBudgetNanos;
    public int pathCacheTtlTicks;
    public int pathRepathTicks;
    public double pathHeuristicWeight;
    public int pathMaxDrop;

    // fight
    public double targetRadius;
    public int retargetIntervalTicks;
    public int gridRefreshTicks;
    public double attackReach;
    public double sprintDistance;
    public double switchTargetFactor;
    public UnteamedMode unteamedNpcs;
    public boolean targetPlayers;
    public boolean ignoreCreativePlayers;
    public boolean friendlyFire;
    public boolean retaliate;
    public boolean autoEnd;
    public DeathPolicy deathPolicy;
    public int respawnDelayTicks;
    public boolean healOnStart;
    public boolean healOnEnd;
    public double damageMultiplier;
    public boolean criticalHits;
    public int minAttackCooldownTicks;
    public Announce announce;
    public List<Integer> countdownMarks = List.of();
    public boolean requireLineOfSight;

    // massaction
    public boolean attackDealsDamage;
    public boolean allowMassActionDuringFight;
    public long defaultActionSeconds;

    // kits
    public boolean clearBeforeApply;

    // auto-download
    public boolean autoDownloadOnStartup;
    public Set<String> autoDownloadPlugins = Set.of();

    public Settings(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Re-reads every value from the plugin's current {@link FileConfiguration}. */
    public void reload() {
        FileConfiguration c = plugin.getConfig();

        showNametag = c.getBoolean("npc.show-nametag", false);
        invulnerableWhenIdle = c.getBoolean("npc.invulnerable-when-idle", true);
        maxNpcs = Math.max(1, c.getInt("npc.max-npcs", 500));
        defaultSkin = c.getString("npc.default-skin", "");
        maxHealth = clamp(c.getDouble("npc.max-health", 20.0), 1.0, 1024.0);
        idleRespawnDelayTicks = Math.max(0, c.getInt("npc.idle-respawn-delay-seconds", 3)) * 20;

        walkSpeed = clamp(c.getDouble("movement.walk-speed", 0.215), 0.01, 1.0);
        sprintSpeed = clamp(c.getDouble("movement.sprint-speed", 0.28), 0.01, 1.0);
        sneakSpeed = clamp(c.getDouble("movement.sneak-speed", 0.065), 0.01, 1.0);
        swimSpeed = clamp(c.getDouble("movement.swim-speed", 0.14), 0.01, 1.0);
        knockbackGraceTicks = Math.max(0, c.getInt("movement.knockback-grace-ticks", 6));
        autoJump = c.getBoolean("movement.auto-jump", true);

        pathfindingEnabled = c.getBoolean("pathfinding.enabled", true);
        pathMaxNodes = Math.max(50, c.getInt("pathfinding.max-nodes", 1500));
        pathMaxRange = Math.max(8, c.getInt("pathfinding.max-range", 64));
        pathBudgetNanos = Math.max(100L, c.getLong("pathfinding.budget-micros-per-tick", 1500L)) * 1000L;
        pathCacheTtlTicks = Math.max(10, c.getInt("pathfinding.cache-ttl-ticks", 100));
        pathRepathTicks = Math.max(5, c.getInt("pathfinding.repath-interval-ticks", 20));
        pathHeuristicWeight = clamp(c.getDouble("pathfinding.heuristic-weight", 1.3), 1.0, 5.0);
        pathMaxDrop = Math.max(1, Math.min(20, c.getInt("pathfinding.max-drop", 3)));

        targetRadius = Math.max(0.0, c.getDouble("fight.target-radius", 64.0));
        retargetIntervalTicks = Math.max(1, c.getInt("fight.retarget-interval-ticks", 10));
        gridRefreshTicks = Math.max(1, c.getInt("fight.grid-refresh-ticks", 4));
        attackReach = clamp(c.getDouble("fight.attack-reach", 3.0), 1.0, 8.0);
        sprintDistance = Math.max(0.0, c.getDouble("fight.sprint-distance", 6.0));
        switchTargetFactor = clamp(c.getDouble("fight.switch-target-factor", 0.6), 0.1, 1.0);
        unteamedNpcs = parseEnum(UnteamedMode.class, c.getString("fight.unteamed-npcs"), UnteamedMode.FREE_FOR_ALL);
        targetPlayers = c.getBoolean("fight.target-players", true);
        ignoreCreativePlayers = c.getBoolean("fight.ignore-creative-players", true);
        friendlyFire = c.getBoolean("fight.friendly-fire", false);
        retaliate = c.getBoolean("fight.retaliate", true);
        autoEnd = c.getBoolean("fight.auto-end", true);
        deathPolicy = parseEnum(DeathPolicy.class, c.getString("fight.on-death"), DeathPolicy.RESPAWN_ON_FIGHT_END);
        respawnDelayTicks = Math.max(1, c.getInt("fight.respawn-delay-seconds", 10)) * 20;
        healOnStart = c.getBoolean("fight.heal-on-start", true);
        healOnEnd = c.getBoolean("fight.heal-on-end", true);
        damageMultiplier = clamp(c.getDouble("fight.damage-multiplier", 1.0), 0.0, 100.0);
        criticalHits = c.getBoolean("fight.critical-hits", true);
        minAttackCooldownTicks = Math.max(1, c.getInt("fight.min-attack-cooldown-ticks", 4));
        announce = parseEnum(Announce.class, c.getString("fight.announce"), Announce.ALL);
        requireLineOfSight = c.getBoolean("fight.require-line-of-sight", true);
        List<Integer> marks = new ArrayList<>(c.getIntegerList("fight.countdown-marks"));
        marks.removeIf(mark -> mark <= 0);
        marks.sort(java.util.Comparator.reverseOrder());
        countdownMarks = List.copyOf(marks);

        autoDownloadOnStartup = c.getBoolean("auto-download.enabled", true) && c.getBoolean("auto-download.on-startup", true);
        Set<String> wanted = new java.util.HashSet<>();
        for (String key : new String[] {"citizens", "essentialsx"}) {
            if (c.getBoolean("auto-download.plugins." + key, true)) {
                wanted.add(key);
            }
        }
        autoDownloadPlugins = Set.copyOf(wanted);

        attackDealsDamage = c.getBoolean("massaction.attack-deals-damage", true);
        allowMassActionDuringFight = c.getBoolean("massaction.allow-during-fight", false);
        defaultActionSeconds = parseSeconds(c.getString("massaction.default-duration"), 10L);

        clearBeforeApply = c.getBoolean("kits.clear-before-apply", true);
    }

    private long parseSeconds(String text, long fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            long seconds = TimeParser.parseSeconds(text);
            return seconds > 0 ? seconds : fallback;
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid duration '" + text + "' in config.yml, using " + fallback + "s");
            return fallback;
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String text, E fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid value '" + text + "' in config.yml, using " + fallback);
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
