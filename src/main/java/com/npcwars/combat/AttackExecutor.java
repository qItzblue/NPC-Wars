package com.npcwars.combat;

import com.google.common.collect.Multimap;
import com.npcwars.NpcWarsPlugin;
import com.npcwars.config.Settings;
import com.npcwars.npc.Npc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * Performs an NPC's melee hit: damage from the held weapon, enchantments, critical hits and knockback, delivered
 * through the normal damage pipeline so armor, protection enchants, shields and other plugins' events all apply.
 */
public final class AttackExecutor {

    private static final double IN_FRONT_COS = Math.cos(Math.toRadians(70));

    private final NpcWarsPlugin plugin;

    public AttackExecutor(NpcWarsPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return the damage of one full-strength hit with this item (empty hand: 1) */
    public double weaponDamage(ItemStack weapon) {
        return DamageCalculator.resolve(DamageCalculator.BASE_ATTACK_DAMAGE, modifiers(weapon, Attribute.ATTACK_DAMAGE));
    }

    /** @return swings per second for this item (empty hand: 4) */
    public double attackSpeed(ItemStack weapon) {
        return DamageCalculator.resolve(DamageCalculator.BASE_ATTACK_SPEED, modifiers(weapon, Attribute.ATTACK_SPEED));
    }

    /** @return ticks this NPC must wait between hits, from its main-hand weapon */
    public int cooldownTicks(Npc npc) {
        Mannequin body = npc.entity();
        ItemStack weapon = body == null ? null : body.getEquipment().getItemInMainHand();
        return DamageCalculator.cooldownTicks(attackSpeed(weapon), plugin.settings().minAttackCooldownTicks);
    }

    /**
     * Swings and hurts the target once.
     *
     * @return {@code false} if the NPC or target is not in a state where a hit makes sense
     */
    public boolean strike(Npc npc, LivingEntity target) {
        Mannequin body = npc.entity();
        if (body == null || !body.isValid() || body.isDead() || !target.isValid() || target.isDead()) {
            return false;
        }
        Settings settings = plugin.settings();
        ItemStack weapon = body.getEquipment().getItemInMainHand();

        double damage = weaponDamage(weapon);
        damage += DamageCalculator.sharpnessBonus(weapon.getEnchantmentLevel(Enchantment.SHARPNESS));
        if (settings.criticalHits && body.getFallDistance() > 0f && !isOnGround(body) && !body.isInWater()) {
            damage = DamageCalculator.critical(damage);
        }
        damage *= settings.damageMultiplier;

        body.swingMainHand();
        npc.controller().face(target.getEyeLocation().add(0, -target.getHeight() * 0.25, 0));

        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
                .withCausingEntity(body)
                .withDirectEntity(body)
                .withDamageLocation(body.getLocation())
                .build();
        target.damage(damage, source);

        if (!target.isValid() || target.isDead()) {
            return true;
        }
        int knockback = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
        boolean sprinting = npc.controller().isSprinting();
        double strength = (knockback + (sprinting ? 1 : 0)) * 0.5;
        if (strength > 0) {
            Vector away = target.getLocation().toVector().subtract(body.getLocation().toVector()).setY(0);
            if (away.lengthSquared() > 1.0E-6) {
                away.normalize().multiply(strength);
                target.setVelocity(target.getVelocity().add(away).add(new Vector(0, 0.1, 0)));
            }
        }
        int fire = weapon.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        if (fire > 0) {
            target.setFireTicks(Math.max(target.getFireTicks(), fire * 80));
        }
        return true;
    }

    /**
     * Hits the nearest enemy within reach that is in front of the NPC (used by the {@code attack} mass action).
     *
     * @return {@code true} if something was hit
     */
    public boolean hitInFront(Npc npc) {
        Mannequin body = npc.entity();
        if (body == null || !body.isValid()) {
            return false;
        }
        double reach = plugin.settings().attackReach;
        Vector facing = body.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1.0E-6) {
            return false;
        }
        facing.normalize();

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : body.getNearbyEntities(reach + 1, reach + 1, reach + 1)) {
            if (!(entity instanceof LivingEntity living) || entity == body || living.isDead()) {
                continue;
            }
            if (plugin.factions().allied(body, living) || plugin.factions().of(living) == Factions.NONE) {
                continue;
            }
            if (living instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Vector toTarget = living.getLocation().toVector().subtract(body.getLocation().toVector()).setY(0);
            double distance = toTarget.length();
            if (distance > reach + 0.5) {
                continue;
            }
            if (distance > 1.0E-6 && toTarget.normalize().dot(facing) < IN_FRONT_COS) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best != null && strike(npc, best);
    }

    /** Collects the modifiers an item applies to an attribute while held in the main hand. */
    private List<DamageCalculator.Modifier> modifiers(ItemStack item, Attribute attribute) {
        List<DamageCalculator.Modifier> out = new ArrayList<>();
        if (item == null || item.getType().isAir()) {
            return out;
        }
        Collection<AttributeModifier> source;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasAttributeModifiers()) {
            Collection<AttributeModifier> custom = meta.getAttributeModifiers(attribute);
            source = custom == null ? List.of() : custom;
        } else {
            Multimap<Attribute, AttributeModifier> defaults = item.getType().getDefaultAttributeModifiers(EquipmentSlot.HAND);
            source = defaults.get(attribute);
        }
        for (AttributeModifier modifier : source) {
            if (!modifier.getSlotGroup().test(EquipmentSlot.HAND)) {
                continue;
            }
            DamageCalculator.Operation operation = switch (modifier.getOperation()) {
                case ADD_NUMBER -> DamageCalculator.Operation.ADD_NUMBER;
                case ADD_SCALAR -> DamageCalculator.Operation.ADD_SCALAR;
                case MULTIPLY_SCALAR_1 -> DamageCalculator.Operation.MULTIPLY_SCALAR_1;
            };
            out.add(new DamageCalculator.Modifier(operation, modifier.getAmount()));
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    private static boolean isOnGround(Mannequin body) {
        return body.isOnGround();
    }
}
