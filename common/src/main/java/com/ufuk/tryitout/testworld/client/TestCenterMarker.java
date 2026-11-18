package com.ufuk.tryitout.testworld.client;

import com.ufuk.tryitout.testworld.TestArea;
import com.ufuk.tryitout.testworld.TestBorderShapes;
import com.ufuk.tryitout.testworld.net.TestSettingsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * With an unlimited test area you can wander far: the area's centre stays easy to find. A small marker slides
 * along the top of the screen in the centre's direction (at the edge with an arrow when it is behind you) with the
 * distance, and near the centre a column of light rises from it. Not a world waypoint: nothing is saved or placed.
 */
final class TestCenterMarker {
    /** The marker shows from this far; closer, you are home. */
    private static final double SHOW_FROM = 12.0;
    private static final double BEAM_DISTANCE = 160.0;

    private TestCenterMarker() {
    }

    private static boolean active() {
        return TestClient.inTest() && TestClient.settings().has(TestSettingsPayload.UNLIMITED)
                && TestBorderShapes.clientCentre() != null;
    }

    /** Light column at the centre (client particles, a few per tick). */
    static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!active() || player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        double[] c = TestBorderShapes.clientCentre();
        double dx = c[0] - player.getX();
        double dz = c[1] - player.getZ();
        if (dx * dx + dz * dz > BEAM_DISTANCE * BEAM_DISTANCE) {
            return;
        }
        double base = TestArea.FLOOR_Y + 1;
        for (int i = 0; i < 6; i++) {
            double y = base + mc.level.random.nextDouble() * 64.0;
            mc.level.addParticle(ParticleTypes.END_ROD, c[0] + (mc.level.random.nextDouble() - 0.5) * 0.3, y,
                    c[1] + (mc.level.random.nextDouble() - 0.5) * 0.3, 0.0, 0.02, 0.0);
        }
    }

    /** "⌂ 230 m" on top of the screen, placed in the centre's direction. */
    static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!active() || player == null || mc.options.hideGui) {
            return;
        }
        double[] c = TestBorderShapes.clientCentre();
        double dx = c[0] - player.getX();
        double dz = c[1] - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < SHOW_FROM) {
            return;
        }
        // Angle of the centre relative to where the player looks: 0 ahead, + to the right.
        float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relative = Mth.wrapDegrees(target - player.getYRot());
        float halfFov = mc.options.fov().get() / 2.0F;
        int width = graphics.guiWidth();
        Component text = Component.translatable("tryitout.test.centre_marker", Math.round(distance));
        int textW = mc.font.width(text) + 12;
        int x;
        String arrow;
        if (Math.abs(relative) <= halfFov) {
            x = width / 2 + Math.round(relative / halfFov * (width / 2.0F - textW));
            arrow = "▼";
        } else {
            x = relative > 0 ? width - textW / 2 - 6 : textW / 2 + 6;
            arrow = relative > 0 ? "▶" : "◀";
        }
        int left = x - textW / 2;
        int y = 6;
        graphics.fill(left, y, left + textW, y + 13, 0xA0101820);
        graphics.renderOutline(left, y, textW, 13, 0xFF4FA3FF);
        graphics.drawString(mc.font, text, left + 6, y + 3, 0xFFE8ECF1, false);
        int arrowX = arrow.equals("▼") ? x - mc.font.width(arrow) / 2 : arrow.equals("▶") ? left + textW + 2
                : left - mc.font.width(arrow) - 2;
        int arrowY = arrow.equals("▼") ? y + 14 : y + 3;
        graphics.drawString(mc.font, arrow, arrowX, arrowY, 0xFF4FA3FF, false);
    }
}
