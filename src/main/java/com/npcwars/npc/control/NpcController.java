package com.npcwars.npc.control;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.combat.DamageCalculator;
import com.npcwars.config.Settings;
import com.npcwars.npc.Npc;
import com.npcwars.path.Path;
import com.npcwars.path.PathFinder;
import com.npcwars.path.PathService;
import com.npcwars.path.Terrain;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.util.Vector;

/**
 * Turns movement intents into motion for one NPC. A Citizens player body has no AI of its own, so every tick this class writes the
 * horizontal velocity (and the jump velocity), pose and rotation itself, while gravity, collisions, step-ups and water
 * physics stay with the server. Intents are either a fixed direction or a goal (walked directly, or along an A* path when the
 * straight line is blocked or the NPC gets stuck).
 */
public final class NpcController {

    /** How fast the NPC moves; {@link #SNEAK} and {@link #SPRINT} map to the speeds in config.yml. */
    public enum Gait { SNEAK, MARCH, WALK, SPRINT }

    /** Upward velocity of a vanilla jump, and the steady rise while swimming up. */
    private static final double JUMP_VELOCITY = 0.42;
    private static final double WATER_RISE = 0.1;
    private static final int STUCK_CHECK_TICKS = 10;
    private static final double STUCK_DISTANCE = 0.08;
    private static final double DIRECT_CHECK_MAX_DISTANCE = 40.0;

    private final NpcWarsPlugin plugin;
    private final Npc npc;

    // Intent
    private Vector direction;
    private Location goal;
    private double arriveDistance;
    private boolean arrived;
    private Gait gait = Gait.WALK;
    private boolean sneaking;
    private boolean swimming;
    private boolean jumpRequested;
    private boolean autoFace = true;

    // Path state
    private Path path;
    private int pathIndex;
    private double pathGoalX;
    private double pathGoalZ;
    private PathService.Request pending;
    private long lastPathRequest = Long.MIN_VALUE / 2;
    private long nextPathCheck;
    private long pathFailedUntil;

    // Runtime state
    private long knockbackUntil;
    private Pose appliedPose;
    private boolean lastJump;
    private long lastSprintTick = Long.MIN_VALUE / 2;
    private double checkX;
    private double checkZ;
    private long checkTick;
    private int stuckChecks;

    // Fall tracking: Citizens bodies never accumulate fall distance, so it is measured here from the highest point.
    private boolean airborne;
    private double peakY;
    private double fallDistance;
    private long cushionUntil;

    public NpcController(NpcWarsPlugin plugin, Npc npc) {
        this.plugin = plugin;
        this.npc = npc;
    }

    // ---------------------------------------------------------------- intents

    /** Walks in a fixed horizontal direction until {@link #stop()} (the vertical component is ignored). */
    public void walkDirection(Vector horizontal, Gait gait) {
        Vector flat = new Vector(horizontal.getX(), 0, horizontal.getZ());
        if (flat.lengthSquared() < 1.0E-6) {
            stop();
            return;
        }
        clearGoal();
        this.direction = flat.normalize();
        this.gait = gait;
    }

    /**
     * Walks to a point, using pathfinding when needed. Safe to call every tick with a moving target: the path is only
     * rebuilt when the goal drifts or the NPC gets stuck.
     */
    public void moveTo(Location target, Gait gait, double arriveDistance) {
        if (goal == null || goal.getWorld() != target.getWorld()) {
            discardPath();
        }
        this.goal = target.clone();
        this.direction = null;
        this.gait = gait;
        this.arriveDistance = Math.max(0.3, arriveDistance);
        this.arrived = false;
    }

    /** Stops walking (stance, pose and jump requests are kept). */
    public void stop() {
        direction = null;
        clearGoal();
    }

    /** Stops everything and releases poses, so the NPC goes back to standing still. */
    public void reset() {
        stop();
        sneaking = false;
        swimming = false;
        jumpRequested = false;
        autoFace = true;
        Player body = npc.entity();
        if (body != null && body.isValid() && body.isSprinting()) {
            body.setSprinting(false);
        }
        lastJump = false;
        appliedPose = null;
        applyPose();
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSwimming(boolean swimming) {
        this.swimming = swimming;
    }

    public boolean isSwimming() {
        return swimming;
    }

    /** Makes the NPC jump once as soon as it stands on the ground (or swim up when in water). */
    public void requestJump() {
        jumpRequested = true;
    }

    /** When {@code false} the NPC no longer turns towards its walking direction (used by look and spin). */
    public void setAutoFace(boolean autoFace) {
        this.autoFace = autoFace;
    }

    /** Immediately turns the NPC (and its head) to face a point. */
    public void face(Location point) {
        Player body = npc.entity();
        if (body == null || !body.isValid()) {
            return;
        }
        Location eye = body.getEyeLocation();
        double dx = point.getX() - eye.getX();
        double dy = point.getY() - eye.getY();
        double dz = point.getZ() - eye.getZ();
        double flat = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
        setRotation(body, body.getLocation(), yaw, pitch);
    }

    /** Sets an absolute yaw and pitch. */
    public void setLook(float yaw, float pitch) {
        Player body = npc.entity();
        if (body != null && body.isValid()) {
            setRotation(body, body.getLocation(), yaw, pitch);
        }
    }

    /** Called when the NPC is hurt: pauses velocity writes briefly so knockback is not cancelled. */
    public void onHurt(long tick) {
        knockbackUntil = tick + plugin.settings().knockbackGraceTicks;
    }

    // ---------------------------------------------------------------- fall tracking

    /** @return blocks fallen since the highest point of the current airtime (0 on the ground) */
    public double fallDistance() {
        return fallDistance;
    }

    public boolean isAirborne() {
        return airborne;
    }

    /** Forgets the fall so far (a mace smash or a water landing ends it without damage). */
    public void resetFall() {
        fallDistance = 0;
        Player body = npc.entity();
        peakY = body == null ? 0 : body.getY();
    }

    /** The next landing within {@code ticks} does no fall damage (wind charge or water bucket). */
    public void cushionLanding(int ticks) {
        cushionUntil = plugin.currentTick() + ticks;
        resetFall();
    }

    private void trackFall(Player body, boolean onGround, boolean inWater, long tick) {
        double y = body.getY();
        if (onGround || inWater) {
            if (airborne && onGround && !inWater && tick >= cushionUntil && plugin.settings().fallDamage) {
                double damage = DamageCalculator.fallDamage(peakY - y);
                if (damage > 0) {
                    body.damage(damage, DamageSource.builder(DamageType.FALL).build());
                }
            }
            airborne = false;
            fallDistance = 0;
            peakY = y;
            return;
        }
        if (!airborne) {
            airborne = true;
            peakY = y;
        }
        peakY = Math.max(peakY, y);
        fallDistance = Math.max(0.0, peakY - y);
    }

    // ---------------------------------------------------------------- queries

    public boolean isMoving() {
        return direction != null || (goal != null && !arrived);
    }

    /** @return {@code true} once a {@link #moveTo} goal has been reached */
    public boolean hasArrived() {
        return arrived;
    }

    public boolean isStuck() {
        return stuckChecks >= 2;
    }

    /** @return {@code true} if the NPC was sprinting within the last few ticks (grants sprint knockback on hits) */
    public boolean isSprinting() {
        return plugin.currentTick() - lastSprintTick <= 3;
    }

    public boolean isFollowingPath() {
        return path != null;
    }

    /**
     * @return {@code true} if {@link #tick} has anything to do; idle NPCs on dry land are skipped entirely, which keeps
     *         hundreds of standing NPCs essentially free
     */
    public boolean needsTick() {
        if (direction != null || goal != null || jumpRequested || lastJump || swimming) {
            return true;
        }
        if (desiredPose() != appliedPose) {
            return true;
        }
        Player body = npc.entity();
        return body != null && (body.isInWater() || airborne || !isOnGround(body));
    }

    public String debugSummary() {
        return "goal=" + (goal == null ? "none" : String.format("%.1f,%.1f,%.1f", goal.getX(), goal.getY(), goal.getZ()))
                + " dir=" + (direction == null ? "none" : String.format("%.2f,%.2f", direction.getX(), direction.getZ()))
                + " gait=" + gait + " sneak=" + sneaking + " swim=" + swimming
                + " path=" + (path == null ? "none" : pathIndex + "/" + path.size() + (path.reachesGoal() ? "" : " partial"))
                + " pending=" + (pending != null) + " stuck=" + stuckChecks + " arrived=" + arrived;
    }

    // ---------------------------------------------------------------- tick

    /** Applies the current intent. Called once per server tick for live NPCs. */
    public void tick(long tick) {
        Player body = npc.entity();
        if (body == null || !body.isValid() || body.isDead()) {
            return;
        }
        Settings settings = plugin.settings();
        applyPose();

        Location loc = body.getLocation();
        boolean wantMove = false;
        double dx = 0;
        double dz = 0;
        boolean wantJump = false;
        boolean inWater = body.isInWater();
        boolean onGround = isOnGround(body);
        trackFall(body, onGround, inWater, tick);

        if (goal != null && !arrived) {
            if (goal.getWorld() != loc.getWorld()) {
                stop();
            } else {
                double flat = Math.hypot(goal.getX() - loc.getX(), goal.getZ() - loc.getZ());
                if (flat <= arriveDistance && Math.abs(goal.getY() - loc.getY()) < 2.5) {
                    arrived = true;
                    discardPath();
                } else {
                    planPath(loc, tick);
                    double tx = goal.getX();
                    double tz = goal.getZ();
                    double ty = goal.getY();
                    if (path != null) {
                        Path.Node next = nextWaypoint(loc);
                        if (next != null) {
                            tx = next.centerX();
                            tz = next.centerZ();
                            ty = next.y();
                            if (onGround && ty > loc.getBlockY() + 0.01 && !inWater) {
                                wantJump = true;
                            }
                        }
                    }
                    double vx = tx - loc.getX();
                    double vz = tz - loc.getZ();
                    double len = Math.hypot(vx, vz);
                    if (len > 1.0E-4) {
                        dx = vx / len;
                        dz = vz / len;
                        wantMove = true;
                    }
                    if (inWater && ty > loc.getY() + 1.0) {
                        wantJump = true;
                    }
                }
            }
        } else if (direction != null) {
            dx = direction.getX();
            dz = direction.getZ();
            wantMove = true;
        }

        if (jumpRequested && (onGround || inWater)) {
            wantJump = true;
            jumpRequested = false;
        }
        if (wantMove && settings.autoJump && onGround && !inWater && blockedAhead(loc, dx, dz)) {
            wantJump = true;
        }
        if (inWater && (swimming || headSubmerged(body))) {
            wantJump = true;
        }
        lastJump = wantJump;

        boolean sprinting = wantMove && gait == Gait.SPRINT && !sneaking && !swimming;
        if (sprinting) {
            lastSprintTick = tick;
        }
        if (body.isSprinting() != sprinting) {
            body.setSprinting(sprinting);
        }
        if (tick >= knockbackUntil && (wantMove || wantJump)) {
            Vector current = body.getVelocity();
            double vy = current.getY();
            if (wantJump) {
                if (inWater) {
                    vy = Math.max(vy, WATER_RISE);
                } else if (onGround) {
                    vy = JUMP_VELOCITY;
                }
            }
            double speed = wantMove ? currentSpeed(inWater) : 0.0;
            body.setVelocity(new Vector(wantMove ? dx * speed : current.getX(), vy, wantMove ? dz * speed : current.getZ()));
        }
        if (wantMove && autoFace) {
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            setRotation(body, loc, yaw, 0f);
        }
        trackProgress(loc, tick, wantMove);
    }

    // ---------------------------------------------------------------- internals

    private void planPath(Location loc, long tick) {
        Settings settings = plugin.settings();
        if (!settings.pathfindingEnabled) {
            return;
        }
        if (pending != null) {
            if (pending.isDone()) {
                Path result = pending.path();
                PathService.Request finished = pending;
                pending = null;
                if (result == null || result.isEmpty()) {
                    pathFailedUntil = tick + 40;
                    if (path == null) {
                        pathIndex = 0;
                    }
                } else {
                    path = result;
                    pathIndex = 0;
                    pathGoalX = finished.goalX() + 0.5;
                    pathGoalZ = finished.goalZ() + 0.5;
                }
            }
            return;
        }
        if (tick < nextPathCheck) {
            return;
        }
        nextPathCheck = tick + 5 + (npc.id() % 5);
        if (tick - lastPathRequest < settings.pathRepathTicks) {
            return;
        }

        boolean need;
        if (path != null) {
            boolean goalMoved = Math.hypot(goal.getX() - pathGoalX, goal.getZ() - pathGoalZ) > 3.0;
            need = goalMoved || isStuck();
        } else {
            if (tick < pathFailedUntil) {
                return;
            }
            double flat = Math.hypot(goal.getX() - loc.getX(), goal.getZ() - loc.getZ());
            need = isStuck() || flat > DIRECT_CHECK_MAX_DISTANCE || !PathFinder.straightWalkable(
                    plugin.paths().terrain(loc.getWorld()), loc.getX(), loc.getY(), loc.getZ(), goal.getX(), goal.getZ());
        }
        if (need) {
            lastPathRequest = tick;
            double radius = Math.max(1.0, Math.min(arriveDistance, 2.0));
            pending = plugin.paths().request(loc, goal, radius);
        }
    }

    /** Advances past reached waypoints and returns the one to walk to, or {@code null} when the path is used up. */
    private Path.Node nextWaypoint(Location loc) {
        while (path != null && pathIndex < path.size()) {
            Path.Node node = path.get(pathIndex);
            double flat = Math.hypot(node.centerX() - loc.getX(), node.centerZ() - loc.getZ());
            if (flat < 0.45 && Math.abs(node.y() - loc.getY()) < 1.3) {
                pathIndex++;
            } else {
                return node;
            }
        }
        path = null;
        return null;
    }

    /** @return {@code true} if a one-block obstacle with enough headroom lies directly ahead (so a jump will clear it) */
    private boolean blockedAhead(Location loc, double dx, double dz) {
        World world = loc.getWorld();
        Terrain terrain = plugin.paths().terrain(world);
        int by = (int) Math.floor(loc.getY() + 0.01);
        int ax = (int) Math.floor(loc.getX() + dx * 0.7);
        int az = (int) Math.floor(loc.getZ() + dz * 0.7);
        if (terrain.type(ax, by, az) != Terrain.SOLID) {
            return false;
        }
        int hx = (int) Math.floor(loc.getX());
        int hz = (int) Math.floor(loc.getZ());
        return Terrain.isPassable(terrain.type(ax, by + 1, az))
                && Terrain.isPassable(terrain.type(ax, by + 2, az))
                && Terrain.isPassable(terrain.type(hx, by + 2, hz));
    }

    private boolean headSubmerged(Player body) {
        Location eye = body.getEyeLocation();
        Terrain terrain = plugin.paths().terrain(eye.getWorld());
        return terrain.type(eye.getBlockX(), eye.getBlockY(), eye.getBlockZ()) == Terrain.WATER;
    }

    private double currentSpeed(boolean inWater) {
        Settings settings = plugin.settings();
        double speed;
        if (swimming) {
            speed = settings.swimSpeed;
        } else if (sneaking || gait == Gait.SNEAK) {
            speed = settings.sneakSpeed;
        } else if (gait == Gait.MARCH) {
            speed = settings.marchSpeed;
        } else {
            speed = gait == Gait.SPRINT ? settings.sprintSpeed : settings.walkSpeed;
        }
        return inWater ? Math.min(speed, settings.swimSpeed) : speed;
    }

    private void applyPose() {
        Player body = npc.entity();
        if (body == null || !body.isValid()) {
            return;
        }
        Pose desired = desiredPose();
        if (desired == appliedPose) {
            return;
        }
        body.setSneaking(desired == Pose.SNEAKING);
        body.setSwimming(desired == Pose.SWIMMING);
        appliedPose = desired;
    }

    private Pose desiredPose() {
        return swimming ? Pose.SWIMMING : sneaking ? Pose.SNEAKING : Pose.STANDING;
    }

    /** Turns the body; skips the call when it already faces that way (compared with its real rotation). */
    private static void setRotation(Player body, Location current, float yaw, float pitch) {
        if (Math.abs(wrap(yaw - current.getYaw())) < 0.05f && Math.abs(pitch - current.getPitch()) < 0.05f) {
            return;
        }
        body.setRotation(yaw, pitch);
        body.setBodyYaw(yaw);
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    private void trackProgress(Location loc, long tick, boolean wantMove) {
        if (tick - checkTick < STUCK_CHECK_TICKS) {
            return;
        }
        double moved = Math.hypot(loc.getX() - checkX, loc.getZ() - checkZ);
        if (wantMove && tick >= knockbackUntil && moved < STUCK_DISTANCE) {
            stuckChecks++;
        } else {
            stuckChecks = 0;
        }
        checkX = loc.getX();
        checkZ = loc.getZ();
        checkTick = tick;
    }

    private void clearGoal() {
        goal = null;
        arrived = false;
        discardPath();
    }

    private void discardPath() {
        path = null;
        pathIndex = 0;
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
        stuckChecks = 0;
    }

    @SuppressWarnings("deprecation")
    private static boolean isOnGround(Player body) {
        return body.isOnGround();
    }
}
