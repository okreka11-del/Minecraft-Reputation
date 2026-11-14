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
        // Fit the track to the actual GUI width without scaling the font or crest.
        // The bar renderer needs at least 15 pixels for its end caps and ticks.
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int x = Math.min(12, Math.max(0, screenWidth - 16));
        int y = 20, w = Math.max(16, Math.min(176, screenWidth - x - 4)), h = 12;
        int score = syncedScore;
        String label = net.minecraft.network.chat.Component.translatable("reputation.hud.label").getString() + ": ";
        String number = (score > 0 ? "+" : "") + score;
        // Render at native GUI pixel size. Fractional pose scaling (previously 1.30x)
        // resamples the vanilla bitmap font and blurs glyph edges at GUI scales.
        // Integer draw coordinates keep the HUD aligned to the GUI pixel grid.
        int textX = x + 13;
        int textY = y - 19;
        // Reserve the score width before truncating translated labels on narrow GUIs.
        int maxLabelWidth = Math.max(0, mc.getWindow().getGuiScaledWidth() - textX - mc.font.width(number) - 7);
        if (mc.font.width(label) > maxLabelWidth) {
            label = mc.font.plainSubstrByWidth(label, maxLabelWidth);
        }
        // A second offset shadow pass makes small pixel glyphs appear doubled.
        // Keep both HUD text runs on one crisp GUI-pixel pass.
        gui.drawString(mc.font, label, textX, textY, 0xFFFFFFFF, false);
        gui.drawString(mc.font, number, textX + mc.font.width(label) + 3, textY, 0xFFFFD44C, false);
        roundedBar(gui, x, y, w, h);
        drawCrest(gui, x - 15, y - 7);
        int marker = x + 7 + Math.round((score + 100) * (w - 14) / 300.0f);
        gui.fill(marker - 3, y - 5, marker + 4, y + h + 5, 0xFF402709);
        gui.fill(marker - 2, y - 4, marker + 3, y + h + 4, 0xFFFFC943);
        gui.fill(marker - 1, y - 3, marker + 2, y + h + 3, 0xFFFFFFA5);
        gui.fill(marker - 5, y - 6, marker + 6, y - 4, 0xFFFFD957);
        gui.fill(marker - 5, y + h + 4, marker + 6, y + h + 6, 0xFFFFD957);
        String title = net.minecraft.network.chat.Component.translatable(TITLES[rankIndex(score)]).getString();
        // Never scale the bitmap font to squeeze long localized rank names.
        // Keep the badge inside the GUI and truncate at integer pixel widths.
        int screenW = mc.getWindow().getGuiScaledWidth();
        int badgeLeft = Math.min(x, Math.max(0, screenW - 1));
        int maxBadgeW = Math.max(1, screenW - badgeLeft);
        int maxTitleW = Math.max(0, maxBadgeW - 20);
        if (mc.font.width(title) > maxTitleW) {
            String suffix = mc.font.width("...") <= maxTitleW ? "..." : "";
            title = mc.font.plainSubstrByWidth(title,
                Math.max(0, maxTitleW - mc.font.width(suffix))) + suffix;
        }
        int textWidth = mc.font.width(title);
        int labelW = Math.min(maxBadgeW, Math.max(100, textWidth + 20));
        int labelX = Math.max(0, Math.min(x + (w - labelW) / 2, screenW - labelW));
        gui.fill(labelX + 3, y + 17, labelX + labelW - 3, y + 34, 0xFFB98B35);
        gui.fill(labelX, y + 20, labelX + labelW, y + 31, 0xFFB98B35);
        gui.fill(labelX + 4, y + 19, labelX + labelW - 4, y + 32, 0xFF171015);
        // Render the bitmap glyphs once: the former 1px duplicate plus built-in
        // font shadow produced a soft-looking double outline on the rank badge.
        gui.drawString(mc.font, title, labelX + (labelW - textWidth) / 2, y + 21, 0xFFFFD966, false);
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
        // Keep every glyph at native GUI pixel size: never shrink the entire legend.
        // A compact two-column list fits ordinary GUI scales without resampling.
        int columns = sw >= 240 ? 2 : 1;
        int rows = (THRESHOLDS.length + columns - 1) / columns;
        int cellW = Math.max(1, Math.min(154, (sw - 20) / columns));
        int rowH = 19;
        int visibleRows = Math.max(1, Math.min(rows, (sh - 32) / rowH));
        int panelW = Math.max(1, Math.min(sw - 8, columns * cellW + 12));
        int panelH = visibleRows * rowH + 23;
        int px = Math.max(4, Math.min(10, sw - panelW - 4));
        int py = Math.max(4, Math.min(54, sh - panelH - 4));
        // For very short windows, show the ranks surrounding the current rank.
        int current = rankIndex(syncedScore);
        int firstRow = Math.max(0, Math.min(rows - visibleRows, current / columns - visibleRows / 2));
        gui.fill(px, py, px + panelW, py + panelH, 0xF419131B);
        gui.fill(px, py, px + panelW, py + 2, 0xFFB88A39);
        // Clip translated headings by native bitmap glyph width, never by scaling.
        String heading = net.minecraft.network.chat.Component.translatable("reputation.hud.ranks").getString();
        int headingWidth = Math.max(0, panelW - 12);
        if (mc.font.width(heading) > headingWidth) {
            String suffix = mc.font.width("...") <= headingWidth ? "..." : "";
            heading = mc.font.plainSubstrByWidth(heading,
                Math.max(0, headingWidth - mc.font.width(suffix))) + suffix;
        }
        gui.drawString(mc.font, heading, px + 6, py + 7, 0xFFFFD46A, false);
        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < columns; col++) {
                int i = (firstRow + row) * columns + col;
                if (i >= THRESHOLDS.length) continue;
                int cx = px + 6 + col * cellW;
                int cy = py + 21 + row * rowH;
                gui.fill(cx, cy, cx + cellW - 3, cy + rowH - 2,
                    i == current ? 0xFF54401D : 0xFF28222A);
                gui.fill(cx + 2, cy + 3, cx + 4, cy + rowH - 5, colorAt(THRESHOLDS[i]));
                String value = (THRESHOLDS[i] > 0 ? "+" : "") + THRESHOLDS[i];
                String title = net.minecraft.network.chat.Component.translatable(TITLES[i]).getString();
                // Reserve the score and spacing first; never let an ellipsis
                // overflow a narrow rank cell or rescale the bitmap font.
                int available = Math.max(0, cellW - 19 - mc.font.width(value));
                if (mc.font.width(title) > available) {
                    String suffix = mc.font.width("...") <= available ? "..." : "";
                    title = mc.font.plainSubstrByWidth(title,
                        Math.max(0, available - mc.font.width(suffix))) + suffix;
                }
                gui.drawString(mc.font, title, cx + 8, cy + 5, 0xFFFFFFFF, false);
                gui.drawString(mc.font, value, cx + cellW - 6 - mc.font.width(value), cy + 5,
                    i == current ? 0xFFFFD74C : 0xFFE8D5D6, false);
            }
        }
    }
}
