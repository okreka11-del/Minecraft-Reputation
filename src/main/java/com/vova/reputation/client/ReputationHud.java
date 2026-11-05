package com.vova.reputation.client;

import com.vova.reputation.ReputationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Always-visible reputation HUD, anchored at the upper-left.
 * Client attachment synchronization must be validated in multiplayer.
 */
@EventBusSubscriber(modid = ReputationMod.ID, value = Dist.CLIENT)
public final class ReputationHud {
    private ReputationHud() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        GuiGraphics gui = event.getGuiGraphics();
        int score = minecraft.player.getData(ReputationMod.REP);
        int x = 12, y = 12, width = 150, height = 10;
        int fill = Math.max(0, Math.min(width, Math.round((score + 100) * width / 300.0f)));
        int color = score >= 151 ? 0xFF49D77A : score >= 101 ? 0xFF85CC5B
            : score >= 51 ? 0xFFE8CA62 : score >= 1 ? 0xFFF09245 : 0xFFE34D55;
        gui.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xCC080B12);
        gui.fill(x, y, x + width, y + height, 0xFF30333D);
        gui.fill(x, y, x + fill, y + height, color);
        gui.drawString(minecraft.font, "REPUTATION  " + score, x, y + 15, 0xFFFFFFFF, true);
    }
}
