package com.npcwars.combat;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Messages;
import com.npcwars.config.Settings;
import com.npcwars.npc.Npc;
import com.npcwars.npc.control.NpcController;
import com.npcwars.util.TimeParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * The fight state machine (idle, countdown, running) and the combat AI that drives every NPC while it runs.
 * <p>
 * Each tick, every fighting NPC (1) validates its target, (2) every few ticks looks for a nearer enemy through the
 * {@link TargetSelector} grid, (3) either walks towards the target (pathfinding when blocked) or, once in reach, faces
 * it and hits whenever its weapon cooldown is over. Everything runs on the main thread from the plugin's tick loop.
 */
public final class FightManager {

    public enum State { IDLE, COUNTDOWN, RUNNING }

    /** Outcome of a start or schedule request. */
    public enum StartResult { STARTED, SCHEDULED, ALREADY_RUNNING, NO_NPCS, NOT_ENOUGH_SIDES }

    private enum EndReason { MANUAL, WINNER, DRAW, SHUTDOWN }

    private static final int END_CHECK_INTERVAL = 10;
    private static final int SYNC_INTERVAL = 20;

    private final NpcWarsPlugin plugin;
    private final TargetSelector selector;
    private final Map<Integer, Fighter> fighters = new HashMap<>();
    private final Set<Integer> announcedMarks = new HashSet<>();
    private State state = State.IDLE;
    private long startAtTick;
    private long lastEndCheck;
    private long lastSync;
    private long fightStartedTick;

    public FightManager(NpcWarsPlugin plugin) {
        this.plugin = plugin;
        this.selector = new TargetSelector(plugin);
    }

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public int fighterCount() {
        return fighters.size();
    }

    /** @return seconds until a scheduled fight starts (0 if none is scheduled) */
    public long secondsUntilStart() {
        return state == State.COUNTDOWN ? Math.max(0, (startAtTick - plugin.currentTick() + 19) / 20) : 0;
    }

    public long secondsRunning() {
        return state == State.RUNNING ? (plugin.currentTick() - fightStartedTick) / 20 : 0;
    }

    // ---------------------------------------------------------------- control

    /** Starts a fight now (cancelling a pending countdown). */
    public StartResult startNow() {
        if (state == State.RUNNING) {
            return StartResult.ALREADY_RUNNING;
        }
        StartResult problem = validate();
        if (problem != null) {
            return problem;
        }
        state = State.IDLE;
        return begin() ? StartResult.STARTED : StartResult.NOT_ENOUGH_SIDES;
    }

    /** Schedules a fight to start after the given number of seconds. */
    public StartResult schedule(long seconds) {
        if (state == State.RUNNING) {
            return StartResult.ALREADY_RUNNING;
        }
        StartResult problem = validate();
        if (problem != null) {
            return problem;
        }
        state = State.COUNTDOWN;
        startAtTick = plugin.currentTick() + seconds * 20L;
        announcedMarks.clear();
        plugin.messages().announce("fight.scheduled", Messages.var("time", TimeParser.format(seconds)));
        return StartResult.SCHEDULED;
    }

    /** Cancels a countdown or ends a running fight. */
    public boolean stop() {
        switch (state) {
            case COUNTDOWN -> {
                state = State.IDLE;
                plugin.messages().announce("fight.countdown-cancelled");
                return true;
            }
            case RUNNING -> {
                end(EndReason.MANUAL, 0);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Ends everything silently (plugin disable or reload). */
    public void shutdown() {
        if (state == State.RUNNING) {
            end(EndReason.SHUTDOWN, 0);
        }
        state = State.IDLE;
        fighters.clear();
        selector.clear();
    }

    private StartResult validate() {
        if (plugin.npcs().count() == 0) {
            return StartResult.NO_NPCS;
        }
        if (liveSides(true).size() < 2) {
            return StartResult.NOT_ENOUGH_SIDES;
        }
        return null;
    }

    /**
     * The distinct sides that could take part: every NPC (spawned or not, if {@code includeUnspawned}) and every
     * eligible player. A fight needs at least two.
     */
    private Set<Integer> liveSides(boolean includeUnspawned) {
        Set<Integer> sides = new HashSet<>();
        for (Npc npc : plugin.npcs().all()) {
            if (npc.isLive() || (includeUnspawned && !npc.isSuppressed())) {
                sides.add(plugin.factions().of(npc));
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (selector.isEligible(player)) {
                sides.add(plugin.factions().of(player));
            }
        }
        return sides;
    }

    /**
     * Spawns missing bodies, heals and starts the fight.
     *
     * @return {@code false} (and stays idle) if fewer than two sides can actually fight, for example because the
     *         NPCs' chunks are not loaded
     */
    private boolean begin() {
        Settings settings = plugin.settings();
        long tick = plugin.currentTick();
        plugin.runner().stopAll();
        for (Npc npc : plugin.npcs().all()) {
            npc.setSuppressed(false);
            npc.setRespawnAtTick(0);
            if (!npc.isLive()) {
                plugin.npcs().spawnBody(npc);
            }
            npc.controller().reset();
            if (settings.healOnStart) {
                plugin.npcs().heal(npc);
            }
        }
        if (liveSides(false).size() < 2) {
            state = State.IDLE;
            return false;
        }
        state = State.RUNNING;
        fightStartedTick = tick;
        lastEndCheck = tick;
        lastSync = tick;
        fighters.clear();
        selector.clear();
        syncFighters(tick);
        plugin.messages().announce("fight.started");
        return true;
    }

    private void end(EndReason reason, int winnerSide) {
        Settings settings = plugin.settings();
        state = State.IDLE;
        fighters.clear();
        selector.clear();
        for (Npc npc : plugin.npcs().all()) {
            npc.controller().reset();
            npc.setSuppressed(false);
            if (settings.healOnEnd && npc.isLive()) {
                plugin.npcs().heal(npc);
            }
        }
        if (reason == EndReason.SHUTDOWN) {
            return;
        }
        if (reason == EndReason.MANUAL) {
            plugin.messages().announce("fight.stopped");
            return;
        }
        if (reason == EndReason.DRAW) {
            plugin.messages().announce("fight.draw");
            return;
        }
        if (winnerSide > 0) {
            plugin.messages().announce("fight.winner-team", Messages.var("team", winnerSide));
        } else if (winnerSide < 0) {
            plugin.messages().announce("fight.winner-npc", Messages.var("id", -winnerSide));
        } else {
            plugin.messages().announce("fight.winner-players");
        }
    }

    // ---------------------------------------------------------------- events from listeners

    /** Called when a fighting NPC dies. */
    public void onNpcDeath(Npc npc) {
        if (state != State.RUNNING) {
            return;
        }
        fighters.remove(npc.id());
        Settings settings = plugin.settings();
        switch (settings.deathPolicy) {
            case REMOVE -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.npcs().get(npc.id()) == npc) {
                    plugin.npcs().remove(npc);
                }
            });
            case RESPAWN_DELAY -> npc.setRespawnAtTick(plugin.currentTick() + settings.respawnDelayTicks);
            case RESPAWN_ON_FIGHT_END -> npc.setSuppressed(true);
        }
    }

    /** Called when a fighting NPC is hurt by another entity, so it can hit back. */
    public void onNpcDamaged(Npc npc, Entity damager) {
        if (state != State.RUNNING || !plugin.settings().retaliate) {
            return;
        }
        Fighter fighter = fighters.get(npc.id());
        if (fighter == null || !(damager instanceof LivingEntity attacker) || !attacker.isValid()) {
            return;
        }
        Mannequin body = npc.entity();
        if (body == null || plugin.factions().allied(body, attacker) || plugin.factions().of(attacker) == Factions.NONE) {
            return;
        }
        if (fighter.target == null || (attacker != fighter.target && distance(body, attacker) < distance(body, fighter.target))) {
            fighter.target = attacker;
        }
    }

    /** Called when an NPC record is deleted. */
    public void onNpcRemoved(Npc npc) {
        fighters.remove(npc.id());
    }

    // ---------------------------------------------------------------- ticking

    public void tick(long tick) {
        switch (state) {
            case COUNTDOWN -> tickCountdown(tick);
            case RUNNING -> tickRunning(tick);
            default -> { }
        }
    }

    private void tickCountdown(long tick) {
        if (tick >= startAtTick) {
            if (!begin()) {
                plugin.messages().announce("fight.cancelled-not-enough-sides");
            }
            return;
        }
        int remaining = (int) Math.ceil((startAtTick - tick) / 20.0);
        if (plugin.settings().countdownMarks.contains(remaining) && announcedMarks.add(remaining)) {
            plugin.messages().announce("fight.countdown", Messages.var("seconds", remaining));
        }
    }

    private void tickRunning(long tick) {
        Settings settings = plugin.settings();
        selector.refresh(tick);
        if (tick - lastSync >= SYNC_INTERVAL) {
            lastSync = tick;
            syncFighters(tick);
        }
        for (Fighter fighter : fighters.values().toArray(new Fighter[0])) {
            if (fighters.get(fighter.npc.id()) == fighter) {
                think(fighter, tick);
            }
        }
        if (settings.autoEnd && state == State.RUNNING && tick - lastEndCheck >= END_CHECK_INTERVAL) {
            lastEndCheck = tick;
            checkForWinner();
        }
    }

    /** Adds NPCs that joined mid-fight (respawns, newly spawned) and drops fighters that are gone. */
    private void syncFighters(long tick) {
        Settings settings = plugin.settings();
        fighters.values().removeIf(f -> !f.npc.isLive());
        for (Npc npc : plugin.npcs().all()) {
            if (!npc.isLive() || fighters.containsKey(npc.id())) {
                continue;
            }
            boolean unteamed = plugin.teams().teamOfNpc(npc.id()) == 0;
            if (unteamed && settings.unteamedNpcs == Settings.UnteamedMode.IDLE) {
                continue;
            }
            fighters.put(npc.id(), new Fighter(npc, tick, settings.retargetIntervalTicks));
        }
    }

    private void checkForWinner() {
        Set<Integer> sides = liveSides(false);
        if (sides.size() > 1) {
            return;
        }
        if (sides.isEmpty()) {
            end(EndReason.DRAW, 0);
            return;
        }
        end(EndReason.WINNER, sides.iterator().next());
    }

    // ---------------------------------------------------------------- combat AI

    private void think(Fighter fighter, long tick) {
        Npc npc = fighter.npc;
        Mannequin body = npc.entity();
        if (!npc.isLive() || body == null) {
            return;
        }
        Settings settings = plugin.settings();
        NpcController controller = npc.controller();
        int side = plugin.factions().of(npc);

        if (fighter.target != null && !isValidTarget(body, fighter.target, side)) {
            fighter.target = null;
        }
        if (tick >= fighter.nextSearchTick) {
            fighter.nextSearchTick = tick + (fighter.target == null ? 5 + Math.floorMod(npc.id(), 5) : settings.retargetIntervalTicks);
            TargetSelector.Candidate best = selector.nearestEnemy(body, side, settings.targetRadius);
            if (best != null) {
                if (fighter.target == null) {
                    fighter.target = best.entity();
                } else if (best.entity() != fighter.target
                        && distance(body, best.entity()) < distance(body, fighter.target) * settings.switchTargetFactor) {
                    fighter.target = best.entity();
                }
            }
        }

        LivingEntity target = fighter.target;
        if (target == null) {
            if (controller.isMoving()) {
                controller.stop();
            }
            return;
        }

        if (reachDistance(body, target) <= settings.attackReach) {
            if (controller.isMoving()) {
                controller.stop();
            }
            controller.face(target.getLocation().add(0, target.getHeight() * 0.6, 0));
            if (tick >= fighter.nextAttackTick
                    && (!settings.requireLineOfSight || body.hasLineOfSight(target))) {
                if (plugin.attacks().strike(npc, target)) {
                    fighter.nextAttackTick = tick + plugin.attacks().cooldownTicks(npc);
                }
            }
            return;
        }

        double distance = distance(body, target);
        NpcController.Gait gait = distance > settings.sprintDistance ? NpcController.Gait.SPRINT : NpcController.Gait.WALK;
        controller.moveTo(target.getLocation(), gait, Math.max(0.8, settings.attackReach - 0.8));
    }

    private boolean isValidTarget(Mannequin body, LivingEntity target, int side) {
        if (!target.isValid() || target.isDead() || target.getWorld() != body.getWorld()) {
            return false;
        }
        int targetSide = plugin.factions().of(target);
        if (targetSide == Factions.NONE || targetSide == side) {
            return false;
        }
        if (target instanceof Player player && !selector.isEligible(player)) {
            return false;
        }
        double radius = plugin.settings().targetRadius;
        return radius <= 0 || distance(body, target) <= radius * 1.5;
    }

    /** Distance from the NPC's eyes to the closest point of the target's hitbox, like the vanilla reach check. */
    private static double reachDistance(Mannequin body, LivingEntity target) {
        Location eye = body.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double dx = eye.getX() - clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double dy = eye.getY() - clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double dz = eye.getZ() - clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double distance(Entity a, Entity b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------------------------------------------------------- diagnostics

    /** @return the sides still in play, as human-readable text for {@code /npc fight status} */
    public List<String> describeSides() {
        List<String> out = new ArrayList<>();
        for (int side : liveSides(false)) {
            out.add(side > 0 ? "team " + side : side < 0 ? "npc #" + (-side) : "players");
        }
        return out;
    }

    /** @return a one-line debug description of what the NPC is currently targeting */
    public String describeFighter(Npc npc) {
        Fighter fighter = fighters.get(npc.id());
        if (fighter == null) {
            return "not fighting";
        }
        LivingEntity target = fighter.target;
        String name = target == null ? "none" : target instanceof Player player ? player.getName()
                : plugin.npcs().byEntity(target) != null ? "npc #" + plugin.npcs().byEntity(target).id() : target.getType().name();
        return "target=" + name + " nextAttackIn=" + Math.max(0, fighter.nextAttackTick - plugin.currentTick()) + "t";
    }
}
