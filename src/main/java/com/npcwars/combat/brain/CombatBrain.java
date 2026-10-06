package com.npcwars.combat.brain;

import com.npcwars.NpcWarsPlugin;
import com.npcwars.combat.AttackPacing;
import com.npcwars.combat.ItemRoles;
import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import com.npcwars.npc.Npc;
import com.npcwars.npc.control.NpcController;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * The fighting mind of one NPC. Every tick it looks at what the NPC carries and what the enemy does, and either runs a
 * tactic that uses an item (a wind charge launch into a mace slam, eating, a pearl, a bow...) or does plain melee:
 * walk up, pick the best weapon, swing now and then with a miss or a pause like a person would.
 */
public final class CombatBrain {

    private static final int LOADOUT_REFRESH_TICKS = 10;

    private final NpcWarsPlugin plugin;
    private final Npc npc;
    private final List<Tactic> tactics;
    private final Tactic waterClutch;
    private final Map<String, Long> cooldownUntil = new HashMap<>();

    private Loadout loadout = new Loadout();
    private long loadoutAt = Long.MIN_VALUE / 2;
    private boolean dirty = true;
    private int bestMelee = Loadout.NONE;

    private Tactic active;
    private long nextEval;
    private long nextAttackTick;
    private long critWaitUntil;

    private LivingEntity sightTarget;
    private boolean hasSight;
    private long nextSightCheck;

    private boolean guarding;
    private long guardRaisedAt;
    private long guardUntil;
    private long shieldDisabledUntil;

    private LivingEntity stunned;
    private long stunnedUntil;

    public CombatBrain(NpcWarsPlugin plugin, Npc npc, long now) {
        this.plugin = plugin;
        this.npc = npc;
        this.nextAttackTick = now + 10;
        this.waterClutch = new WaterClutchTactic();
        this.tactics = List.of(new TotemTactic(), new UseItemTactic(), new RangedTactic(), new WindMaceTactic(),
                new PearlTactic(), new ThrowTactic(), PlaceTactic.cobweb(), PlaceTactic.lava(), new ExplosiveTactic());
    }

    // ---------------------------------------------------------------- state shared with tactics and listeners

    Loadout loadout() {
        return loadout;
    }

    /** Forces a rescan of the inventory before the next decision (after an item was used up or moved). */
    void dirty() {
        dirty = true;
    }

    boolean ready(String key, long now) {
        return now >= cooldownUntil.getOrDefault(key, 0L);
    }

    void cooldown(String key, long now, long ticks) {
        cooldownUntil.put(key, now + ticks);
    }

    /** @return the slot of the best melee weapon, or {@link Loadout#NONE} */
    int bestMelee() {
        return bestMelee;
    }

    public long ticksUntilAttack(long now) {
        return Math.max(0, nextAttackTick - now);
    }

    public boolean isGuardUp(long now) {
        return guarding && now - guardRaisedAt >= 5 && now >= shieldDisabledUntil;
    }

    /** Called by the damage listener when a blocked hit came from an axe: the shield is out for a while. */
    public void disableShield(long now, int ticks) {
        trace("has its shield knocked out for " + ticks / 20 + "s");
        shieldDisabledUntil = now + ticks;
        lowerGuard();
    }

    public void markStunned(LivingEntity target, long until) {
        stunned = target;
        stunnedUntil = until;
    }

    boolean targetStunned(LivingEntity target, long now) {
        return stunned == target && now < stunnedUntil;
    }

    public String describe(long now) {
        return "tactic=" + (active == null ? "melee" : active.name()) + " guard=" + (guarding ? "up" : "down")
                + " nextAttackIn=" + ticksUntilAttack(now) + "t";
    }

    /** Ends whatever the brain is doing and lowers the shield (a fight ended or the NPC was removed). */
    public void shutdown() {
        Player body = npc.entity();
        if (active != null && body != null && body.isValid()) {
            try {
                active.end(contextForShutdown(body));
            } catch (RuntimeException ex) {
                plugin.reportError("combat brain shutdown", ex);
            }
        }
        active = null;
        lowerGuard();
    }

    private CombatContext contextForShutdown(Player body) {
        return new CombatContext(plugin, npc, body, body, plugin.currentTick(), this, 0, 0, false);
    }

    // ---------------------------------------------------------------- the tick

    /** Decides and acts for one tick. {@code target} is the enemy the NPC currently fights. */
    public void tick(long now, LivingEntity target) {
        Player body = npc.entity();
        if (!npc.isLive() || body == null) {
            return;
        }
        refreshLoadout(body, now);
        double reach = reachDistance(body, target);
        double horizontal = Math.hypot(target.getX() - body.getX(), target.getZ() - body.getZ());
        CombatContext c = new CombatContext(plugin, npc, body, target, now, this, reach, horizontal, canSee(body, target, now));
        Settings settings = c.settings;

        NpcController controller = npc.controller();
        if (settings.waterClutch && controller.isAirborne() && controller.fallDistance() > 5.0 && waterClutch != active
                && waterClutch.want(c) > 0) {
            interrupt(c);
            if (waterClutch.start(c)) {
                active = waterClutch;
                trace("starts waterclutch");
            }
        }

        if (active != null) {
            boolean keep;
            try {
                keep = active.tick(c);
            } catch (RuntimeException ex) {
                plugin.reportError("combat tactic " + active.name(), ex);
                keep = false;
            }
            if (keep) {
                return;
            }
            endActive(c);
        }

        updateGuard(c);
        if (now >= nextEval) {
            nextEval = now + 4 + Math.floorMod(npc.id(), 3);
            chooseTactic(c);
            if (active != null) {
                return;
            }
        }
        melee(c);
    }

    private void chooseTactic(CombatContext c) {
        Tactic best = null;
        double bestScore = 0.0;
        for (Tactic tactic : tactics) {
            if (!tactic.enabled(c.settings)) {
                continue;
            }
            double score;
            try {
                score = tactic.want(c);
            } catch (RuntimeException ex) {
                plugin.reportError("combat tactic " + tactic.name(), ex);
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                best = tactic;
            }
        }
        if (best == null) {
            return;
        }
        lowerGuard();
        boolean started;
        try {
            started = best.start(c);
        } catch (RuntimeException ex) {
            plugin.reportError("combat tactic " + best.name(), ex);
            started = false;
        }
        if (started) {
            active = best;
            trace("starts " + best.name());
        } else {
            cooldown(best.name(), c.now, 40); // could not start: do not retry every 4 ticks
        }
    }

    /** With debug on, the console shows what each NPC decides (tactic starts and key events). */
    public void trace(String what) {
        if (plugin.messages().debug()) {
            plugin.getLogger().info("[combat] NPC #" + npc.id() + " " + what);
        }
    }

    private void interrupt(CombatContext c) {
        if (active != null) {
            endActive(c);
        }
    }

    private void endActive(CombatContext c) {
        Tactic ending = active;
        active = null;
        dirty = true;
        try {
            ending.end(c);
        } catch (RuntimeException ex) {
            plugin.reportError("combat tactic " + ending.name(), ex);
        }
    }

    // ---------------------------------------------------------------- loadout

    private void refreshLoadout(Player body, long now) {
        if (!dirty && now - loadoutAt < LOADOUT_REFRESH_TICKS) {
            return;
        }
        dirty = false;
        loadoutAt = now;
        loadout = Loadout.scan(body.getInventory().getContents());
        bestMelee = Loadout.NONE;
        double bestScore = 0.0;
        for (int slot : new int[] {loadout.sword, loadout.axe, loadout.spear, loadout.mace, loadout.trident}) {
            if (!Loadout.has(slot)) {
                continue;
            }
            ItemStack item = body.getInventory().getItem(slot);
            double score = plugin.attacks().weaponDamage(item) * plugin.attacks().attackSpeed(item);
            if (score > bestScore) {
                bestScore = score;
                bestMelee = slot;
            }
        }
    }

    // ---------------------------------------------------------------- shield

    private void updateGuard(CombatContext c) {
        if (!c.settings.useShield) {
            return;
        }
        ItemStack off = c.body.getInventory().getItemInOffHand();
        boolean hasShield = ItemRoles.isShield(off.getType());
        if (!hasShield && !ItemRoles.isTotem(off.getType()) && Loadout.has(c.loadout.shield) && c.loadout.shield != Hands.OFF_HAND) {
            Hands.swap(c.body, c.loadout.shield, Hands.OFF_HAND);
            dirty = true;
            return;
        }
        if (!hasShield) {
            if (guarding) {
                lowerGuard();
            }
            return;
        }
        boolean disabled = c.now < shieldDisabledUntil;
        if (guarding) {
            if (disabled || c.now >= guardUntil || c.now >= nextAttackTick) {
                lowerGuard();
            }
            return;
        }
        boolean attackSoon = c.now >= nextAttackTick - 2;
        if (!disabled && !attackSoon && c.reach < 5.0 && c.sees && (c.now + npc.id()) % 10 == 0
                && ThreadLocalRandom.current().nextDouble() < 0.5) {
            c.body.startUsingItem(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
            guarding = true;
            guardRaisedAt = c.now;
            guardUntil = c.now + 20 + ThreadLocalRandom.current().nextInt(30);
            trace("raises its shield");
        }
    }

    void lowerGuard() {
        if (!guarding) {
            return;
        }
        guarding = false;
        Player body = npc.entity();
        if (body != null && body.isValid() && body.hasActiveItem()) {
            body.clearActiveItem();
        }
    }

    // ---------------------------------------------------------------- melee

    private void melee(CombatContext c) {
        Settings settings = c.settings;
        NpcController controller = npc.controller();
        boolean inReach = c.reach <= settings.attackReach;

        if (inReach && c.sees) {
            if (controller.isMoving()) {
                controller.stop();
            }
            controller.face(c.target.getLocation().add(0, c.target.getHeight() * 0.6, 0));
            chooseWeapon(c);
            if (c.now < nextAttackTick) {
                return;
            }
            lowerGuard();
            if (waitForCritical(c, controller)) {
                return;
            }
            var pacing = settings.pacing();
            var random = ThreadLocalRandom.current();
            boolean done = AttackPacing.connects(random, pacing)
                    ? plugin.attacks().strike(npc, c.target)
                    : plugin.attacks().miss(npc, c.target);
            if (done) {
                nextAttackTick = c.now + AttackPacing.nextDelay(random, plugin.attacks().cooldownTicks(npc), pacing);
            }
            return;
        }

        // Out of reach, or "in reach" but behind a wall, fence or glass pane: keep closing in (around the obstacle).
        double distance = c.body.getLocation().distance(c.target.getLocation());
        NpcController.Gait gait = distance > settings.sprintDistance ? NpcController.Gait.SPRINT : NpcController.Gait.WALK;
        double arrive = inReach ? 0.5 : Math.max(0.8, settings.attackReach - 0.8);
        controller.moveTo(c.target.getLocation(), gait, arrive);
    }

    /**
     * A jump hit is a critical hit. Now and then, when a swing is due, the NPC hops first and swings on the way down.
     *
     * @return {@code true} while it is waiting for the right moment
     */
    private boolean waitForCritical(CombatContext c, NpcController controller) {
        if (!c.settings.criticalHits) {
            return false;
        }
        if (controller.isAirborne()) {
            if (controller.fallDistance() > 0.05 || c.now >= critWaitUntil) {
                critWaitUntil = 0;
                return false;
            }
            return true;
        }
        if (critWaitUntil == 0 && ThreadLocalRandom.current().nextDouble() < 0.25 && !ItemRoles.isMace(heldType(c))) {
            critWaitUntil = c.now + 14;
            controller.requestJump();
            return true;
        }
        if (critWaitUntil != 0 && c.now >= critWaitUntil) {
            critWaitUntil = 0;
        }
        return critWaitUntil != 0;
    }

    private static org.bukkit.Material heldType(CombatContext c) {
        return c.body.getInventory().getItemInMainHand().getType();
    }

    /** The axe against a raised shield, otherwise the weapon with the best damage per second. */
    private void chooseWeapon(CombatContext c) {
        int slot = bestMelee;
        if (c.settings.axeStun && Loadout.has(loadout.axe) && Shields.isBlocking(plugin, c.target)) {
            slot = loadout.axe;
        }
        if (Loadout.has(slot)) {
            Hands.select(c.body, slot);
            if (slot >= 9) {
                dirty = true;
            }
        }
    }

    // ---------------------------------------------------------------- sight and distance

    private boolean canSee(Player body, LivingEntity target, long now) {
        if (!plugin.settings().requireLineOfSight) {
            return true;
        }
        if (sightTarget != target || now >= nextSightCheck) {
            sightTarget = target;
            hasSight = body.hasLineOfSight(target);
            nextSightCheck = now + 5;
        }
        return hasSight;
    }

    /** Distance from the NPC's eyes to the closest point of the target's hitbox, like the vanilla reach check. */
    static double reachDistance(Player body, LivingEntity target) {
        Location eye = body.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double dx = eye.getX() - clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double dy = eye.getY() - clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double dz = eye.getZ() - clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
