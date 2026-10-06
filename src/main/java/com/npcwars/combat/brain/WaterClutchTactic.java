package com.npcwars.combat.brain;

import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import com.npcwars.npc.control.NpcController;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * The "water bucket clutch": on a long fall, put water under yourself just before landing so the fall does no damage.
 * It is checked every tick while falling (see {@link CombatBrain#tick}), not only when the brain picks a tactic.
 */
final class WaterClutchTactic implements Tactic {

    private static final double TRIGGER_DISTANCE = 3.5;

    @Override
    public String name() {
        return "waterclutch";
    }

    @Override
    public boolean enabled(Settings settings) {
        return settings.waterClutch;
    }

    @Override
    public double want(CombatContext c) {
        NpcController controller = c.npc.controller();
        if (!Loadout.has(c.loadout.waterBucket) || !controller.isAirborne() || controller.fallDistance() < 5.0
                || c.body.getVelocity().getY() > -0.4) {
            return 0;
        }
        return groundBelow(c) == null ? 0 : 130.0;
    }

    private static Block groundBelow(CombatContext c) {
        RayTraceResult hit = c.body.getWorld().rayTraceBlocks(c.body.getLocation(), new Vector(0, -1, 0), TRIGGER_DISTANCE,
                FluidCollisionMode.NEVER, true);
        return hit == null ? null : hit.getHitBlock();
    }

    @Override
    public boolean start(CombatContext c) {
        Block ground = groundBelow(c);
        if (ground == null || !Loadout.has(c.loadout.waterBucket)) {
            return false;
        }
        Block spot = ground.getRelative(0, 1, 0);
        int slot = c.loadout.waterBucket;
        Hands.select(c.body, slot);
        if (!c.plugin.placed().place(spot, Material.WATER, c.now + c.settings.placedBlockTicks)) {
            return false;
        }
        c.body.swingMainHand();
        int bucketSlot = c.body.getInventory().getHeldItemSlot();
        c.body.getInventory().setItem(bucketSlot, new ItemStack(Material.BUCKET));
        c.npc.controller().cushionLanding(40);
        c.brain.dirty();
        return true;
    }

    @Override
    public boolean tick(CombatContext c) {
        return false;
    }

    @Override
    public void end(CombatContext c) {
        c.brain.dirty();
    }
}
