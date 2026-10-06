package com.npcwars.combat.brain;

import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * Putting a block at the enemy's feet: a cobweb to trap them, or lava (off by default). The block is temporary; the
 * plugin removes it after {@code combat.placed-block-seconds} and restores everything when it stops.
 */
final class PlaceTactic implements Tactic {

    private final String name;
    private final Material block;
    private final Function<Loadout, Integer> slotOf;
    private final Predicate<Settings> enabled;
    private final int cooldown;
    private final double score;
    private final double maxReach;
    private final boolean returnsBucket;

    private int slot = Loadout.NONE;
    private long startedAt;

    private PlaceTactic(String name, Material block, Function<Loadout, Integer> slotOf, Predicate<Settings> enabled,
                        int cooldown, double score, double maxReach, boolean returnsBucket) {
        this.name = name;
        this.block = block;
        this.slotOf = slotOf;
        this.enabled = enabled;
        this.cooldown = cooldown;
        this.score = score;
        this.maxReach = maxReach;
        this.returnsBucket = returnsBucket;
    }

    static PlaceTactic cobweb() {
        return new PlaceTactic("cobweb", Material.COBWEB, l -> l.cobweb, s -> s.cobwebs, 200, 45.0, 5.0, false);
    }

    static PlaceTactic lava() {
        return new PlaceTactic("lava", Material.LAVA, l -> l.lavaBucket, s -> s.lava, 400, 40.0, 4.0, true);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean enabled(Settings settings) {
        return enabled.test(settings);
    }

    @Override
    public double want(CombatContext c) {
        slot = slotOf.apply(c.loadout);
        if (!Loadout.has(slot) || !c.brain.ready(name, c.now) || !c.sees || c.reach > maxReach || c.reach < 1.5) {
            return 0;
        }
        if (!isFree(c.target.getLocation().getBlock())) {
            return 0;
        }
        return ThreadLocalRandom.current().nextDouble() < 0.5 ? score : 0.0;
    }

    private static boolean isFree(Block block) {
        return block.getType().isAir();
    }

    @Override
    public boolean start(CombatContext c) {
        if (!Loadout.has(slot)) {
            return false;
        }
        c.brain.lowerGuard();
        Hands.select(c.body, slot);
        c.npc.controller().stop();
        startedAt = c.now;
        return true;
    }

    @Override
    public boolean tick(CombatContext c) {
        c.npc.controller().face(c.target.getLocation());
        if (c.now - startedAt < 3) {
            return true;
        }
        Block at = c.target.getLocation().getBlock();
        int slotNow = slotOf.apply(Loadout.scan(c.body.getInventory().getContents()));
        if (Loadout.has(slotNow) && isFree(at) && c.plugin.placed().place(at, block, c.now + c.settings.placedBlockTicks)) {
            c.body.swingMainHand();
            c.brain.trace("places " + name);
            if (returnsBucket) {
                c.body.getInventory().setItem(slotNow, new ItemStack(Material.BUCKET));
            } else {
                Hands.consumeOne(c.body, slotNow);
            }
            c.brain.cooldown(name, c.now, cooldown);
        }
        return false;
    }

    @Override
    public void end(CombatContext c) {
        c.brain.dirty();
    }
}
