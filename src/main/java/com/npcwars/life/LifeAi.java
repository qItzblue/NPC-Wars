package com.npcwars.life;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Settings;
import com.npcwars.life.LifePlanner.Activity;
import com.npcwars.npc.Behavior;
import com.npcwars.npc.Npc;
import com.npcwars.npc.control.NpcController;
import com.npcwars.path.Terrain;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Peaceful "SMP player" behaviour for NPCs in {@link Behavior#LIFE}: stand around, wander near home, look at players,
 * and fidget. It only acts while nothing else controls the NPC (a fight, a mass action or a route); the moment one of
 * those takes over the NPC's life state is dropped and it starts fresh afterwards.
 */
public final class LifeAi {

    private static final int WANDER_TIMEOUT_TICKS = 20 * 20;
    private static final int LOOK_INTERVAL = 10;

    private static final class State {
        Activity activity = Activity.IDLE;
        long until;
        long started;
        long nextLook;
        long nextSwing;
        int swingsLeft;
        boolean fresh = true;
    }

    private final NpcWarsPlugin plugin;
    private final Map<Integer, State> states = new HashMap<>();

    public LifeAi(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Called once per server tick from the plugin's tick loop. */
    public void tick(long tick) {
        Settings settings = plugin.settings();
        if (!settings.lifeEnabled) {
            states.clear();
            return;
        }
        for (Npc npc : plugin.npcs().all()) {
            if (npc.behavior() != Behavior.LIFE || !npc.isLive() || plugin.isNpcBusy(npc)) {
                states.remove(npc.id());
                continue;
            }
            State state = states.get(npc.id());
            if (state == null) {
                state = new State();
                state.until = tick + Math.floorMod(npc.id() * 7, 60) + 1;
                states.put(npc.id(), state);
            }
            try {
                step(npc, state, tick, settings);
            } catch (RuntimeException ex) {
                plugin.reportError("life AI", ex);
            }
        }
    }

    /** Forgets an NPC's state and releases whatever it was doing (used when life mode is switched off). */
    public void release(Npc npc) {
        State state = states.remove(npc.id());
        if (state != null && npc.isLive()) {
            npc.controller().reset();
        }
    }

    public void clear() {
        states.clear();
    }

    /** @return a short description for {@code /npcwars info} */
    public String describe(Npc npc) {
        State state = states.get(npc.id());
        return state == null ? "idle" : state.activity.name().toLowerCase(java.util.Locale.ROOT);
    }

    // ---------------------------------------------------------------- behaviour

    private void step(Npc npc, State state, long tick, Settings settings) {
        Player body = npc.entity();
        NpcController controller = npc.controller();
        if (state.fresh) {
            state.fresh = false;
            controller.stop();
        }
        switch (state.activity) {
            case WANDER -> {
                boolean arrived = controller.hasArrived();
                boolean gaveUp = tick >= state.until || (controller.isStuck() && tick - state.started > 60);
                if (arrived || gaveUp || !controller.isMoving()) {
                    controller.stop();
                    startIdle(state, tick, settings);
                }
            }
            case SNEAK -> {
                if (tick >= state.until) {
                    controller.setSneaking(false);
                    startIdle(state, tick, settings);
                }
            }
            case SWING -> {
                if (tick >= state.nextSwing) {
                    body.swingMainHand();
                    state.swingsLeft--;
                    state.nextSwing = tick + 8 + ThreadLocalRandom.current().nextInt(7);
                    if (state.swingsLeft <= 0) {
                        startIdle(state, tick, settings);
                    }
                }
            }
            case LOOK_AROUND -> {
                if (tick >= state.nextLook) {
                    var random = ThreadLocalRandom.current();
                    controller.setLook(random.nextFloat() * 360f - 180f, random.nextFloat() * 30f - 10f);
                    state.nextLook = tick + 20 + random.nextInt(30);
                }
                if (tick >= state.until) {
                    startIdle(state, tick, settings);
                }
            }
            case JUMP, IDLE -> {
                if (tick >= state.until) {
                    decide(npc, state, tick, settings);
                }
            }
        }
        boolean standing = state.activity == Activity.IDLE || state.activity == Activity.LOOK_AROUND;
        if (standing && settings.lifeLookRadius > 0 && (tick + npc.id()) % LOOK_INTERVAL == 0) {
            lookAtNearestPlayer(npc, settings.lifeLookRadius);
        }
    }

    private void startIdle(State state, long tick, Settings settings) {
        state.activity = Activity.IDLE;
        state.until = tick + LifePlanner.ticksBetween(ThreadLocalRandom.current(),
                settings.lifeDecisionMinTicks / 2, settings.lifeDecisionMaxTicks / 2);
    }

    private void decide(Npc npc, State state, long tick, Settings settings) {
        var random = ThreadLocalRandom.current();
        NpcController controller = npc.controller();
        Activity next = LifePlanner.choose(random, settings.lifeWeights);
        switch (next) {
            case WANDER -> {
                Location target = pickWanderTarget(npc, settings);
                if (target == null) {
                    startIdle(state, tick, settings);
                    return;
                }
                controller.moveTo(target, NpcController.Gait.WALK, 1.2);
                state.activity = Activity.WANDER;
                state.started = tick;
                state.until = tick + WANDER_TIMEOUT_TICKS;
            }
            case JUMP -> {
                controller.requestJump();
                state.activity = Activity.JUMP;
                state.until = tick + 20 + random.nextInt(20);
            }
            case SNEAK -> {
                controller.setSneaking(true);
                state.activity = Activity.SNEAK;
                state.until = tick + 30 + random.nextInt(50);
            }
            case SWING -> {
                state.activity = Activity.SWING;
                state.swingsLeft = 1 + random.nextInt(3);
                state.nextSwing = tick;
                state.until = tick + 100;
            }
            case LOOK_AROUND -> {
                state.activity = Activity.LOOK_AROUND;
                state.nextLook = tick;
                state.until = tick + LifePlanner.ticksBetween(random, 40, 100);
            }
            case IDLE -> startIdle(state, tick, settings);
        }
    }

    /** @return a standable spot near the NPC's home, or {@code null} if a few tries found none */
    private Location pickWanderTarget(Npc npc, Settings settings) {
        Location home = npc.home();
        if (home == null) {
            return null;
        }
        World world = home.getWorld();
        Terrain terrain = plugin.paths().terrain(world);
        var random = ThreadLocalRandom.current();
        double currentY = npc.entity().getLocation().getY();
        for (int attempt = 0; attempt < 5; attempt++) {
            double[] offset = LifePlanner.ringOffset(random, settings.lifeWanderMin, settings.lifeWanderRadius);
            int x = (int) Math.floor(home.getX() + offset[0]);
            int z = (int) Math.floor(home.getZ() + offset[1]);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int ground = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int y = ground + 1;
            if (Math.abs(y - currentY) > 6) {
                continue;
            }
            if (terrain.type(x, y - 1, z) == Terrain.SOLID && Terrain.isPassable(terrain.type(x, y, z))
                    && Terrain.isPassable(terrain.type(x, y + 1, z))) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private void lookAtNearestPlayer(Npc npc, double radius) {
        Player body = npc.entity();
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : body.getWorld().getNearbyPlayers(body.getLocation(), radius)) {
            if (player.getGameMode() == GameMode.SPECTATOR || plugin.npcs().byEntity(player) != null) {
                continue; // spectators, and NPC bodies (which are player entities too)
            }
            double distance = player.getLocation().distanceSquared(body.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) {
            npc.controller().face(nearest.getEyeLocation());
        }
    }
}
