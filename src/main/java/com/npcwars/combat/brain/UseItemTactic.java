package com.npcwars.combat.brain;

import com.npcwars.combat.ItemRoles;
import com.npcwars.combat.Loadout;
import com.npcwars.config.Settings;
import com.npcwars.npc.control.NpcController;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Eating and drinking: a golden apple or a healing potion when hurt, a strength, speed or resistance potion before a
 * fight gets going, plain food when hungry and not under attack. The item is used the way a player uses it (held up for
 * a moment), so it really is consumed with all its effects, and the NPC backs away from the enemy while it does.
 */
final class UseItemTactic implements Tactic {

    private static final int MAX_TICKS = 60;

    private int slot = Loadout.NONE;
    private boolean buff;
    private long startedAt;

    @Override
    public String name() {
        return "use";
    }

    @Override
    public boolean enabled(Settings settings) {
        return settings.eating || settings.potions;
    }

    @Override
    public double want(CombatContext c) {
        slot = Loadout.NONE;
        buff = false;
        if (!c.brain.ready(name(), c.now)) {
            return 0;
        }
        Settings s = c.settings;
        double hp = c.health();
        boolean low = hp <= s.eatBelowHealth;
        double best = 0;
        if (s.potions && low) {
            int potion = findPotion(c, PotionKinds.Kind.HEAL, false);
            if (Loadout.has(potion)) {
                best = 105.0 - hp * 3.0;
                slot = potion;
            }
        }
        if (s.eating && low && Loadout.has(c.loadout.goldenApple) && (c.reach > 3.0 || hp <= 7.0)) {
            double score = 100.0 - hp * 3.0;
            if (score > best) {
                best = score;
                slot = c.loadout.goldenApple;
            }
        }
        if (best == 0 && s.potions && c.brain.ready("buff", c.now) && c.reach < 20.0 && c.sees) {
            int potion = findPotion(c, PotionKinds.Kind.BUFF, true);
            if (Loadout.has(potion)) {
                best = 40.0;
                slot = potion;
                buff = true;
            }
        }
        if (best == 0 && s.eating && c.body.getFoodLevel() <= 12 && !c.loadout.food.isEmpty() && c.reach > 6.0) {
            best = 25.0;
            slot = c.loadout.food.get(0);
        }
        return best;
    }

    /** First drinkable potion of a kind; for buffs, one whose first effect the NPC does not already have. */
    private int findPotion(CombatContext c, PotionKinds.Kind kind, boolean skipActive) {
        for (int potionSlot : c.loadout.potions) {
            ItemStack item = c.body.getInventory().getItem(potionSlot);
            if (item == null || item.getType() != org.bukkit.Material.POTION || PotionKinds.of(item) != kind) {
                continue;
            }
            if (skipActive && item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta
                    && meta.getBasePotionType() != null) {
                boolean has = meta.getBasePotionType().getPotionEffects().stream()
                        .anyMatch(effect -> c.body.hasPotionEffect(effect.getType()));
                if (has) {
                    continue;
                }
            }
            return potionSlot;
        }
        return Loadout.NONE;
    }

    @Override
    public boolean start(CombatContext c) {
        if (!Loadout.has(slot)) {
            return false;
        }
        c.brain.lowerGuard();
        Hands.select(c.body, slot);
        c.npc.controller().stop();
        c.body.startUsingItem(EquipmentSlot.HAND);
        if (!c.body.hasActiveItem()) {
            return false;
        }
        startedAt = c.now;
        c.brain.trace("uses " + c.body.getInventory().getItemInMainHand().getType());
        return true;
    }

    @Override
    public boolean tick(CombatContext c) {
        Player body = c.body;
        if (!body.hasActiveItem() || c.now - startedAt > MAX_TICKS) {
            return false;
        }
        if (c.reach < 8.0) {
            Vector away = body.getLocation().toVector().subtract(c.target.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-6) {
                c.npc.controller().walkDirection(away, NpcController.Gait.WALK);
            }
        }
        return true;
    }

    @Override
    public void end(CombatContext c) {
        if (c.body.hasActiveItem()) {
            c.body.clearActiveItem();
        }
        c.npc.controller().stop();
        c.brain.cooldown(name(), c.now, ThreadLocalRandom.current().nextInt(30, 60));
        if (buff) {
            c.brain.cooldown("buff", c.now, 400);
        }
        c.brain.dirty();
    }

    /** Whether the stack in this slot is something to eat or drink (used by tests and the dupe stick). */
    static boolean isConsumable(ItemStack item) {
        return item != null && (ItemRoles.isGoldenApple(item.getType()) || ItemRoles.isPotion(item.getType()) || item.getType().isEdible());
    }
}
