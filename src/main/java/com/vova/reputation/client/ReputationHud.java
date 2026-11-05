package com.vova.reputation.client;

import com.vova.reputation.ReputationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Pixel-art reputation HUD with an interactive, screen-scaled rank legend. */
@EventBusSubscriber(modid = ReputationMod.ID, value = Dist.CLIENT)
public final class ReputationHud {
    private ReputationHud() {}
    private static int syncedScore = 0;
    private static final int[] THRESHOLDS = {-100, -80, -60, -20, 0, 100, 120, 140, 160, 180, 200};
    private static final String[] TITLES = {
        "reputation.rank.dark_lord", "reputation.rank.villain", "reputation.rank.enemy",
        "reputation.rank.unfriendly", "reputation.rank.neutral", "reputation.rank.acquaintance",
        "reputation.rank.ally", "reputation.rank.reliable_ally", "reputation.rank.good_ally",
        "reputation.rank.best_ally", "reputation.rank.hero_of_light"
    };
    private static final String[] SYMBOLS = {"✦", "☠", "⚔", "!", "•", "◇", "◆", "❖", "★", "✶", "☀"};

    public static void updateScore(int score) {
        syncedScore = Math.max(-100, Math.min(200, score));
    }

    private static int rankIndex(int score) {
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (score >= THRESHOLDS[i]) return i;
        }
        return 0;
    }

    private static int colorAt(int score) {
        if (score < -20) return 0xFFAA263C;
        if (score < 0) return 0xFFAA7687;
        if (score < 100) return 0xFF85818B;
        return score < 160 ? 0xFFE8AF32 : 0xFFFFD34B;
    }

    private static void roundedBar(GuiGraphics gui, int x, int y, int w, int h) {
        // Pixel-rounded end caps, dark embossed frame and segmented XP-like track.
        gui.fill(x + 5, y - 3, x + w - 5, y + h + 3, 0xFF100D14);
        gui.fill(x + 2, y, x + w - 2, y + h, 0xFF100D14);
        gui.fill(x + 5, y - 2, x + w - 5, y + h + 2, 0xFFB4863A);
        gui.fill(x + 3, y + 2, x + w - 3, y + h - 2, 0xFFB4863A);
        gui.fill(x + 6, y, x + w - 6, y + h, 0xFF241820);
        for (int i = 7; i < w - 7; i += 2) {
            int value = -100 + (int) ((i - 7) * 300.0 / (w - 14));
            int color = colorAt(value);
            gui.fill(x + i, y + 2, x + Math.min(w - 7, i + 2), y + h - 2, color);
            gui.fill(x + i, y + 2, x + Math.min(w - 7, i + 2), y + 4, 0x44FFFFFF);
            gui.fill(x + i, y + h - 4, x + Math.min(w - 7, i + 2), y + h - 2, 0x55000000);
        }
        for (int i = 1; i < 15; i++) {
            int tick = x + 7 + i * (w - 14) / 15;
            gui.fill(tick, y + 2, tick + 1, y + h - 2, 0xAA24131B);
        }
    }

    /** Red wing and gold sun crest, drawn in crisp Minecraft GUI pixels. */
    private static void drawCrest(GuiGraphics gui, int x, int y) {
        // The original reference has a split dark/light emblem attached to the bar.
        int[] wing = {0x00110, 0x01331, 0x13331, 0x33331, 0x33311, 0x33110, 0x31100, 0x11000};
        for (int row = 0; row < wing.length; row++) {
            for (int col = 0; col < 5; col++) {
                int digit = (wing[row] >> ((4 - col) * 4)) & 15;
                if (digit == 0) continue;
                int shade = digit == 3 ? 0xFFE54248 : digit == 2 ? 0xFF9C1C31 : 0xFF57131F;
                gui.fill(x + col * 3, y + row * 3, x + col * 3 + 3, y + row * 3 + 3, shade);
            }
        }
        gui.fill(x + 14, y + 4, x + 18, y + 24, 0xFF180E13);
        gui.fill(x + 18, y + 5, x + 21, y + 23, 0xFFFFD450);
        gui.fill(x + 21, y + 9, x + 24, y + 19, 0xFFFFE78A);
        gui.fill(x + 24, y + 12, x + 27, y + 16, 0xFFFFD450);
        gui.fill(x + 18, y, x + 21, y + 5, 0xFFFFC531);
        gui.fill(x + 18, y + 23, x + 21, y + 28, 0xFFFFC531);
        gui.fill(x + 23, y + 4, x + 26, y + 8, 0xFFFFC531);
        gui.fill(x + 23, y + 20, x + 26, y + 24, 0xFFFFC531);
    }

    private static void drawHud(GuiGraphics gui, Minecraft mc) {
        int x = 12, y = 20, w = 176, h = 12;
        int score = syncedScore;
        String label = net.minecraft.network.chat.Component.translatable("reputation.hud.label").getString() + ": ";
        String number = (score > 0 ? "+" : "") + score;
        // Use Minecraft's own pixel font and scale it up without smoothing.
        gui.pose().pushPose();
        gui.pose().translate(x + 13, y - 19, 0);
        gui.pose().scale(1.22f, 1.22f, 1.0f);
        gui.drawString(mc.font, label, 0, 0, 0xFFFFFFFF, true);
        gui.drawString(mc.font, number, mc.font.width(label) + 3, 0, 0xFFFFD44C, true);
        gui.pose().popPose();
        roundedBar(gui, x, y, w, h);
        drawCrest(gui, x - 15, y - 7);
        int marker = x + 7 + Math.round((score + 100) * (w - 14) / 300.0f);
        gui.fill(marker - 3, y - 5, marker + 4, y + h + 5, 0xFF402709);
        gui.fill(marker - 2, y - 4, marker + 3, y + h + 4, 0xFFFFC943);
        gui.fill(marker - 1, y - 3, marker + 2, y + h + 3, 0xFFFFFFA5);
        gui.fill(marker - 5, y - 6, marker + 6, y - 4, 0xFFFFD957);
        gui.fill(marker - 5, y + h + 4, marker + 6, y + h + 6, 0xFFFFD957);
        String title = net.minecraft.network.chat.Component.translatable(TITLES[rankIndex(score)]).getString();
        int textWidth = mc.font.width(title);
        int labelW = Math.max(100, textWidth + 20);
        int labelX = x + (w - labelW) / 2;
        gui.fill(labelX + 3, y + 17, labelX + labelW - 3, y + 34, 0xFFB98B35);
        gui.fill(labelX, y + 20, labelX + labelW, y + 31, 0xFFB98B35);
        gui.fill(labelX + 4, y + 19, labelX + labelW - 4, y + 32, 0xFF171015);
        gui.drawString(mc.font, title, labelX + (labelW - textWidth) / 2, y + 21, 0xFFFFD966, true);
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        drawHud(event.getGuiGraphics(), mc);
    }

    @SubscribeEvent
    public static void screenRender(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double mx = mc.mouseHandler.xpos() * sw / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * sh / mc.getWindow().getScreenHeight();
        if (mx < 8 || mx > 185 || my < 0 || my > 56) return;
        GuiGraphics gui = event.getGuiGraphics();
        int padding = 9;
        int cellW = 92;
        int count = THRESHOLDS.length;
        int columns = Math.max(1, Math.min(count, (sw - 16 - padding * 2) / cellW));
        int rows = (count + columns - 1) / columns;
        int panelW = Math.min(sw - 8, columns * cellW + padding * 2);
        int panelH = rows * 52 + 31;
        int px = Math.max(4, Math.min(10, sw - panelW - 4));
        int py = Math.min(Math.max(54, (int) my + 10), Math.max(4, sh - panelH - 4));
        gui.pose().pushPose();
        // Scale the legend to remain fully visible even at small GUI resolutions.
        float scale = Math.min(1.0f, (sh - 8.0f) / Math.max(1, panelH));
        if (scale < 1.0f) {
            gui.pose().scale(scale, scale, 1.0f);
            px = (int) (px / scale);
            py = 4;
        }
        gui.fill(px, py, px + panelW, py + panelH, 0xF419131B);
        gui.fill(px, py, px + panelW, py + 2, 0xFFB88A39);
        gui.drawString(mc.font, net.minecraft.network.chat.Component.translatable("reputation.hud.ranks").getString(), px + 8, py + 8, 0xFFFFD46A, true);
        int current = rankIndex(syncedScore);
        for (int i = 0; i < count; i++) {
            int cx = px + padding + (i % columns) * cellW;
            int cy = py + 26 + (i / columns) * 52;
            boolean active = i == current;
            gui.fill(cx, cy, cx + cellW - 3, cy + 47, active ? 0xFF54401D : 0xFF28222A);
            gui.fill(cx + 3, cy + 3, cx + cellW - 6, cy + 6, colorAt(THRESHOLDS[i]));
            String value = (THRESHOLDS[i] > 0 ? "+" : "") + THRESHOLDS[i];
            gui.drawCenteredString(mc.font, value, cx + (cellW - 3) / 2, cy + 9, active ? 0xFFFFD74C : 0xFFE8D5D6);
            gui.drawCenteredString(mc.font, SYMBOLS[i], cx + (cellW - 3) / 2, cy + 20, colorAt(THRESHOLDS[i]));
            String title = net.minecraft.network.chat.Component.translatable(TITLES[i]).getString();
            if (mc.font.width(title) > cellW - 7 && title.contains(" ")) {
                int split = title.lastIndexOf(' ');
                gui.drawCenteredString(mc.font, title.substring(0, split), cx + (cellW - 3) / 2, cy + 30, 0xFFFFFFFF);
                gui.drawCenteredString(mc.font, title.substring(split + 1), cx + (cellW - 3) / 2, cy + 39, 0xFFFFFFFF);
            } else {
                gui.drawCenteredString(mc.font, title, cx + (cellW - 3) / 2, cy + 34, 0xFFFFFFFF);
            }
        }
        gui.pose().popPose();
    }
}
