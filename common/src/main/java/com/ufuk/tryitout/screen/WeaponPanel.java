package com.ufuk.tryitout.screen;

import com.ufuk.tryitout.combat.CombatStats;
import com.ufuk.tryitout.scene.WeaponRig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Weapon mode overlay: a stats panel on the right of the scene (damage, speed, cooldown, DPS, crit, reach,
 * dummy distance, last swing, enchantments, other attributes), the attack charge bar, and the floating
 * damage numbers above the dummy.
 */
final class WeaponPanel {
    private static final int WIDTH = 170;
    private static final int LINE = 10;
    private static final int PAD = 5;

    private WeaponPanel() {
    }

    static void render(GuiGraphics graphics, Font font, WeaponRig rig, long gameTime, int viewRight, int viewTop) {
        if (rig.ranged() != null) {
            renderLines(graphics, font, rangedLines(rig, gameTime), rig.drawPower(gameTime), viewRight, viewTop);
            return;
        }
        renderLines(graphics, font, lines(rig, gameTime), rig.charge(gameTime), viewRight, viewTop);
    }

    private static void renderLines(GuiGraphics graphics, Font font, List<Component> lines, double charge,
            int viewRight, int viewTop) {
        int left = viewRight - WIDTH - 4;
        int top = viewTop + 4;
        int height = lines.size() * LINE + PAD * 2 + 8;
        graphics.fill(left, top, left + WIDTH, top + height, 0xB0000000);
        int y = top + PAD;
        for (Component line : lines) {
            graphics.drawString(font, line, left + PAD, y, 0xFFFFFF, false);
            y += LINE;
        }
        // Attack charge / draw bar.
        int barLeft = left + PAD;
        int barWidth = WIDTH - PAD * 2;
        int barY = y + 2;
        graphics.fill(barLeft, barY, barLeft + barWidth, barY + 4, 0xFF333333);
        graphics.fill(barLeft, barY, barLeft + (int) (barWidth * charge), barY + 4, charge >= 1.0 ? 0xFF55FF55 : 0xFFFFAA00);
    }

    private static List<Component> lines(WeaponRig rig, long gameTime) {
        CombatStats stats = rig.stats();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tryitout.weapon.title").withStyle(ChatFormatting.GOLD));
        lines.add(stat("damage", num(stats.fullDamage())));
        lines.add(stat("crit", num(stats.critDamage())));
        lines.add(stat("speed", num(stats.attackSpeed())));
        lines.add(stat("cooldown", num(stats.cooldownSeconds())));
        lines.add(stat("dps", num(stats.dps())));
        lines.add(stat("reach", num(stats.reach())));
        boolean inReach = rig.inReach();
        lines.add(Component.translatable("tryitout.weapon.distance", num(rig.reachDistance()), "")
                .append(" ").append(Component.translatable(inReach ? "tryitout.weapon.in_reach"
                        : "tryitout.weapon.out_of_reach").withStyle(inReach ? ChatFormatting.GREEN : ChatFormatting.RED)));
        lines.add(Component.translatable("tryitout.weapon.crit_hint").withStyle(ChatFormatting.DARK_GRAY));
        WeaponRig.Swing last = rig.lastSwing();
        if (last != null) {
            if (!last.hit()) {
                lines.add(Component.translatable("tryitout.weapon.last_miss").withStyle(ChatFormatting.RED));
            } else if (last.critical()) {
                lines.add(Component.translatable("tryitout.weapon.last_crit", num(last.damage()),
                        num(last.normalDamage()), num(last.damage() - last.normalDamage())).withStyle(ChatFormatting.GOLD));
            } else {
                lines.add(Component.translatable("tryitout.weapon.last_hit", num(last.damage()),
                        Math.round(last.charge() * 100)).withStyle(ChatFormatting.YELLOW));
            }
        }
        for (CombatStats.EnchantLine enchant : stats.enchants()) {
            Component value = Component.literal(" +" + num(enchant.bonus()));
            lines.add(Component.empty().append(enchant.name()).append(value)
                    .append(enchant.conditional()
                            ? Component.translatable("tryitout.weapon.conditional").withStyle(ChatFormatting.GRAY)
                            : Component.empty()).withStyle(ChatFormatting.AQUA));
        }
        for (CombatStats.AttributeLine attribute : stats.others()) {
            lines.add(Component.empty().append(attribute.name())
                    .append(": " + num(attribute.base()) + " → " + num(attribute.value())).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static List<Component> rangedLines(WeaponRig rig, long gameTime) {
        var profile = rig.ranged();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tryitout.weapon.ranged_title",
                Component.translatable("tryitout.weapon.kind." + profile.kind().name().toLowerCase(Locale.ROOT)))
                .withStyle(ChatFormatting.GOLD));
        if (profile.estimated()) {
            lines.add(Component.translatable("tryitout.weapon.estimated").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(stat("velocity", num(profile.speed())));
        if (profile.baseDamage() > 0) {
            int[] close = profile.damage(profile.speed(), 1.0);
            lines.add(stat("arrow_damage", close[0] == close[1] ? String.valueOf(close[0]) : close[0] + "-" + close[1]));
        }
        lines.add(stat("flat_range", num(rig.flatRange())));
        lines.add(stat("max_range", num(rig.maxRange())));
        lines.add(Component.translatable("tryitout.weapon.distance", num(rig.distance()), ""));
        if (profile.charges()) {
            lines.add(Component.translatable("tryitout.weapon.draw", Math.round(rig.drawPower(gameTime) * 100)));
        }
        WeaponRig.Swing last = rig.lastSwing();
        if (last != null && last.ranged()) {
            lines.add(Component.translatable(last.critical() ? "tryitout.weapon.shot_crit" : "tryitout.weapon.shot_hit",
                    num(last.damage()), num(last.distance()), Math.round(last.charge() * 100))
                    .withStyle(last.critical() ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
        }
        lines.add(stat("melee", num(stats(rig).fullDamage())));
        return lines;
    }

    private static CombatStats stats(WeaponRig rig) {
        return rig.stats();
    }

    /** Distance numbers along the path on the floor. */
    static void renderRuler(GuiGraphics graphics, Font font, WeaponRig rig, Matrix4f viewProjection, int left,
            int top, int width, int height) {
        for (WeaponRig.Popup mark : rig.ruler()) {
            Vector4f clip = viewProjection.transform(new Vector4f((float) mark.position().x,
                    (float) mark.position().y, (float) mark.position().z, 1.0F));
            if (clip.w <= 0) {
                continue;
            }
            int sx = Math.round(left + (clip.x / clip.w * 0.5F + 0.5F) * width);
            int sy = Math.round(top + (0.5F - clip.y / clip.w * 0.5F) * height);
            graphics.drawString(font, mark.text(), sx - font.width(mark.text()) / 2, sy - 4, mark.color(), true);
        }
    }

    private static Component stat(String key, String value) {
        return Component.translatable("tryitout.weapon." + key, value);
    }

    private static String num(double value) {
        if (Double.isInfinite(value)) {
            return "∞";
        }
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Damage numbers above the dummy, rising and fading. */
    static void renderPopups(GuiGraphics graphics, Font font, WeaponRig rig, long gameTime, float partialTick,
            Matrix4f viewProjection, int left, int top, int width, int height) {
        for (WeaponRig.Popup popup : rig.popups()) {
            float age = (gameTime - popup.born()) + partialTick;
            float rise = age * 0.03F;
            Vector4f clip = viewProjection.transform(new Vector4f((float) popup.position().x,
                    (float) popup.position().y + rise, (float) popup.position().z, 1.0F));
            if (clip.w <= 0) {
                continue;
            }
            int sx = Math.round(left + (clip.x / clip.w * 0.5F + 0.5F) * width);
            int sy = Math.round(top + (0.5F - clip.y / clip.w * 0.5F) * height);
            if (popup.big()) {
                graphics.pose().pushPose();
                graphics.pose().translate(sx, sy, 0);
                graphics.pose().scale(1.6F, 1.6F, 1.0F);
                graphics.drawString(font, popup.text(), -font.width(popup.text()) / 2, 0, popup.color(), true);
                graphics.pose().popPose();
            } else {
                graphics.drawString(font, popup.text(), sx - font.width(popup.text()) / 2, sy, popup.color(), true);
            }
        }
    }
}
