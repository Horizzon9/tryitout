package com.ufuk.pocketsandbox.combat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;

/**
 * What the item does in the main hand of a player: attack damage, attack speed and reach from the item's
 * attribute modifiers (modded attributes included, applied with vanilla's own attribute math on copies of
 * the player's base values), plus enchantment damage. Vanilla evaluates enchantment damage with a
 * {@code ServerLevel}; here unconditional effects (Sharpness) are added, conditional ones (Smite: undead,
 * Bane: arthropods) are listed separately.
 */
public record CombatStats(double damage, double attackSpeed, double reach, double enchantBonus,
        List<EnchantLine> enchants, List<AttributeLine> others) {

    public record EnchantLine(Component name, double bonus, boolean conditional) {
    }

    public record AttributeLine(Component name, double base, double value) {
    }

    public double fullDamage() {
        return CombatMath.damage(damage, enchantBonus, 1.0, false);
    }

    public double critDamage() {
        return CombatMath.damage(damage, enchantBonus, 1.0, true);
    }

    public double dps() {
        return CombatMath.dps(fullDamage(), attackSpeed);
    }

    public double cooldownSeconds() {
        return CombatMath.cooldownTicks(attackSpeed) / 20.0;
    }

    /** {@code base} supplies the base values (a player: damage 1, speed 4, reach 3). */
    public static CombatStats of(ItemStack stack, Player base) {
        Map<Holder<Attribute>, AttributeInstance> attributes = new LinkedHashMap<>();
        instance(attributes, base, Attributes.ATTACK_DAMAGE);
        instance(attributes, base, Attributes.ATTACK_SPEED);
        instance(attributes, base, Attributes.ENTITY_INTERACTION_RANGE);
        stack.forEachModifier(EquipmentSlot.MAINHAND,
                (attribute, modifier) -> instance(attributes, base, attribute).addOrUpdateTransientModifier(modifier));

        double damage = attributes.get(Attributes.ATTACK_DAMAGE).getValue();
        double speed = attributes.get(Attributes.ATTACK_SPEED).getValue();
        double reach = attributes.get(Attributes.ENTITY_INTERACTION_RANGE).getValue();

        List<AttributeLine> others = new ArrayList<>();
        for (Map.Entry<Holder<Attribute>, AttributeInstance> entry : attributes.entrySet()) {
            Holder<Attribute> holder = entry.getKey();
            if (holder == Attributes.ATTACK_DAMAGE || holder == Attributes.ATTACK_SPEED
                    || holder == Attributes.ENTITY_INTERACTION_RANGE) {
                continue;
            }
            AttributeInstance instance = entry.getValue();
            others.add(new AttributeLine(Component.translatable(holder.value().getDescriptionId()),
                    instance.getBaseValue(), instance.getValue()));
        }

        List<EnchantLine> enchants = new ArrayList<>();
        double bonus = enchantmentDamage(stack, enchants);
        return new CombatStats(damage, speed, reach, bonus, List.copyOf(enchants), List.copyOf(others));
    }

    private static AttributeInstance instance(Map<Holder<Attribute>, AttributeInstance> map, Player base,
            Holder<Attribute> attribute) {
        return map.computeIfAbsent(attribute, key -> {
            AttributeInstance instance = new AttributeInstance(key, changed -> {
            });
            instance.setBaseValue(base.getAttributes().hasAttribute(key)
                    ? base.getAttributeBaseValue(key) : key.value().getDefaultValue());
            return instance;
        });
    }

    /** Sum of unconditional enchantment damage; every effect is also listed in {@code lines}. */
    private static double enchantmentDamage(ItemStack stack, List<EnchantLine> lines) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        RandomSource random = RandomSource.create(0L);
        float total = 0.0F;
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();
            Component name = Enchantment.getFullname(enchantment, level);
            for (ConditionalEffect<EnchantmentValueEffect> effect
                    : enchantment.value().getEffects(EnchantmentEffectComponents.DAMAGE)) {
                float after = effect.effect().process(level, random, total);
                float added = after - total;
                boolean conditional = effect.requirements().isPresent();
                lines.add(new EnchantLine(name, added, conditional));
                if (!conditional) {
                    total = after;
                }
            }
        }
        return total;
    }
}
