package com.vova.reputation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

/** Applies reputation prices before the vanilla merchant menu sends its offers. */
public final class ReputationTrading {
    private ReputationTrading() {}

    public static int adjustment(ServerPlayer player) {
        int score = player.getData(ReputationMod.REP);
        int standing = ReputationMod.factionStandings(player)[0];
        int amount = score >= 200 ? -12 : score >= 151 ? -8 : score >= 101 ? -4
            : score <= -80 ? 12 : score < 0 ? 6 : 0;
        amount += standing >= 100 ? -5 : standing >= 40 ? -2
            : standing <= -60 ? 9 : standing <= -20 ? 4 : 0;
        return Math.max(-16, Math.min(16, amount));
    }

    public static void open(ServerPlayer player, Villager villager) {
        int amount = adjustment(player);
        for (MerchantOffer offer : villager.getOffers()) {
            offer.resetSpecialPriceDiff();
            if (amount != 0) offer.addToSpecialPriceDiff(amount);
        }
        villager.setTradingPlayer(player);
        villager.openTradingScreen(player, villager.getDisplayName(), villager.getVillagerData().getLevel());
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
            "Репутация: " + (amount < 0 ? "скидка до " : amount > 0 ? "наценка до " : "обычные цены ")
                + Math.abs(amount) + " изумрудов за предложение (минимум 1)"), true);
    }
}
