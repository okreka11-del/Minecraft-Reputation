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
    private static int syncedScore = 100;
    private static final int[] THRESHOLDS = {-100, -80, -60, -20, 0, 100, 120, 140, 160, 180, 200};
    private static final String[] TITLES = {
        "Повелитель Тьмы", "Злодей", "Враг", "Недруг", "Нейтральный",
        "Знакомый", "Союзник", "Надёжный союзник", "Хороший союзник",
        "Лучший союзник", "Герой Света"
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

    private static void drawHud(GuiGraphics gui, Minecraft mc) {
        int x = 12, y = 13, w = 166, h = 10;
        int score = syncedScore;
        gui.drawString(mc.font, "Репутация: ", x + 18, y - 11, 0xFFFFFFFF, true);
        gui.drawString(mc.font, (score > 0 ? "+" : "") + score, x + 88, y - 11, 0xFFFFD44C, true);
        gui.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xEE130E14);
        gui.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF8B6D43);
        gui.fill(x, y, x + w, y + h, 0xFF231E24);
        for (int i = 0; i < w; i += 2) {
            int value = -100 + (int) Math.round((i + 1) * 300.0 / w);
            gui.fill(x + i, y + 1, x + Math.min(w, i + 2), y + h - 1, colorAt(value));
        }
        for (int i = 1; i < 12; i++) {
            int tick = x + i * w / 12;
            gui.fill(tick, y, tick + 1, y + h, 0xAA181218);
        }
        int marker = x + Math.round((score + 100) * w / 300.0f);
        gui.fill(marker - 2, y - 4, marker + 3, y + h + 4, 0xFF432B0A);
        gui.fill(marker - 1, y - 3, marker + 2, y + h + 3, 0xFFFFD750);
        String title = TITLES[rankIndex(score)];
        int textWidth = mc.font.width(title);
        int labelW = Math.max(94, textWidth + 20);
        int labelX = x + (w - labelW) / 2;
        gui.fill(labelX, y + 15, labelX + labelW, y + 29, 0xEE171015);
        gui.fill(labelX, y + 15, labelX + labelW, y + 16, 0xFFB98B35);
        gui.fill(labelX, y + 28, labelX + labelW, y + 29, 0xFFB98B35);
        gui.drawString(mc.font, title, labelX + (labelW - textWidth) / 2, y + 18, 0xFFFFD966, true);
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
        gui.drawString(mc.font, "Звания репутации  •  -100 ... +200", px + 8, py + 8, 0xFFFFD46A, true);
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
            String title = TITLES[i];
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
