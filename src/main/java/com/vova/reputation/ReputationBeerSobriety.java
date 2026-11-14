package com.vova.reputation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
/** One intoxication point fades every five minutes of server play. */
public final class ReputationBeerSobriety {
    private ReputationBeerSobriety() {}
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.serverLevel().getGameTime() % 6000 != 0) return;
        int count = player.getData(ReputationMod.BEER_DRINKS);
        if (count > 0) player.setData(ReputationMod.BEER_DRINKS, count - 1);
    }
}
