package com.worldbetween.trade;

import com.worldbetween.entity.arki.ArkiEntity;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.arki.Arkis;
import com.worldbetween.item.WorldBetweenItems;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Trading with the Arki, who count in shards of amethyst. They sell what the sea and the starlight give them,
 * buy what holds secrets (books, maps, instruments: the hunger that cost them the sky), and alone among the
 * peoples they change the coin of the others: iron nuggets of Neris, flint shards of the Kaments.
 */
public final class ArkiTrade {
	private static final int MAX_LOTS = 8;
	/** What each colony keeps back of a store before it sells any. */
	private static final int RESERVE = 40;

	private record Sale(Item item, int count, int price, int minReputation, String store, int units) {
	}

	private record Purchase(Item item, int count, int pay, int lore) {
	}

	private static final List<Sale> SALES = List.of(
			new Sale(Items.PRISMARINE_SHARD, 8, 4, 0, Arkis.SEA_GOODS, 4),
			new Sale(Items.PRISMARINE_CRYSTALS, 4, 5, 0, Arkis.SEA_GOODS, 4),
			new Sale(Items.SEA_LANTERN, 2, 8, 0, Arkis.SEA_GOODS, 6),
			new Sale(Items.GLOWSTONE_DUST, 4, 4, 0, Arkis.STARLIGHT, 6),
			new Sale(Items.END_ROD, 4, 6, 0, Arkis.STARLIGHT, 8),
			new Sale(Items.NAUTILUS_SHELL, 1, 10, 10, Arkis.SEA_GOODS, 8),
			new Sale(Items.SPYGLASS, 1, 12, 10, null, 0),
			new Sale(Items.ENDER_PEARL, 2, 14, 20, Arkis.STARLIGHT, 20),
			new Sale(Items.EXPERIENCE_BOTTLE, 3, 15, 30, Arkis.LORE, 6),
			new Sale(Items.SPONGE, 1, 20, 30, Arkis.SEA_GOODS, 16),
			new Sale(Items.BUDDING_AMETHYST, 1, 64, 50, Arkis.AMETHYST, 64),
			new Sale(Items.HEART_OF_THE_SEA, 1, 56, 60, Arkis.SEA_GOODS, 60));

	private static final List<Purchase> PURCHASES = List.of(
			new Purchase(Items.BOOK, 3, 4, 3),
			new Purchase(Items.WRITTEN_BOOK, 1, 6, 5),
			new Purchase(Items.ENCHANTED_BOOK, 1, 12, 10),
			new Purchase(Items.FILLED_MAP, 1, 8, 4),
			new Purchase(Items.COMPASS, 1, 3, 1),
			new Purchase(Items.CLOCK, 1, 5, 2));

	private ArkiTrade() {
	}

	public static boolean open(ArkiEntity keeper, ServerPlayerEntity player, Faction faction, Settlement colony) {
		int reputation = faction.reputation(player.getUuid());
		if (faction.isCriminal(player.getUuid())) {
			return false;
		}
		if (reputation < 0) {
			keeper.say(player, "speech.worldbetween.arki.trade.refuse");
			return true;
		}
		float factor = NerisTrade.priceFactor(reputation);
		TradeOfferList offers = new TradeOfferList();
		Map<TradeOffer, Consumer<ServerPlayerEntity>> deals = new IdentityHashMap<>();
		exchange(faction, reputation, offers, deals);
		for (Sale sale : SALES) {
			if (reputation < sale.minReputation()) {
				continue;
			}
			int lots = sale.store() == null ? 2 : (int) Math.min(MAX_LOTS, (colony.counter(sale.store()) - RESERVE) / sale.units());
			if (lots < 1) {
				continue;
			}
			int price = Math.max(1, Math.round(sale.price() * factor));
			TradeOffer offer = new TradeOffer(amethyst(price), new ItemStack(sale.item(), sale.count()), lots, 0, 0f);
			offers.add(offer);
			deals.put(offer, p -> {
				if (sale.store() != null) {
					colony.addCounter(sale.store(), -sale.units());
				}
				colony.addCounter(Arkis.AMETHYST, price);
				dirty(p);
			});
		}
		for (Purchase purchase : PURCHASES) {
			int pay = Math.max(1, (int) Math.floor(purchase.pay() / factor));
			int lots = (int) Math.min(MAX_LOTS, (colony.counter(Arkis.AMETHYST) - RESERVE) / pay);
			if (lots < 1) {
				continue;
			}
			TradeOffer offer = new TradeOffer(new TradedItem(purchase.item(), purchase.count()), Optional.empty(),
					new ItemStack(Items.AMETHYST_SHARD, pay), lots, 0, 0f);
			offers.add(offer);
			deals.put(offer, p -> {
				colony.addCounter(Arkis.AMETHYST, -pay);
				colony.addCounter(Arkis.LORE, purchase.lore());
				Goodwill.earn(p, faction, 1);
				dirty(p);
			});
		}
		if (offers.isEmpty()) {
			keeper.say(player, "speech.worldbetween.arki.trade.empty");
			return true;
		}
		keeper.say(player, "speech.worldbetween.arki.trade.greet");
		keeper.stall().open(player, offers, deals);
		keeper.sendOffers(player, keeper.getCustomName() != null ? keeper.getCustomName() : keeper.getName(), 0);
		return true;
	}

	/**
	 * The money-changers of the world: ten of anyone's coin for eight of another's (nine for a friend). What
	 * they pay out in foreign coin they must first have taken in; they always keep a little of each.
	 */
	private static void exchange(Faction faction, int reputation, TradeOfferList offers, Map<TradeOffer, Consumer<ServerPlayerEntity>> deals) {
		int out = reputation >= 30 ? 9 : 8;
		Item[] coins = {Items.IRON_NUGGET, WorldBetweenItems.FLINT_SHARD, Items.AMETHYST_SHARD};
		String[] stocks = {"coin_neris", "coin_kament", null};
		for (int from = 0; from < coins.length; from++) {
			for (int to = 0; to < coins.length; to++) {
				if (from == to) {
					continue;
				}
				String give = stocks[to];
				long held = give == null ? Long.MAX_VALUE : faction.counter(give) + 64;
				int lots = (int) Math.min(MAX_LOTS, held / out);
				if (lots < 1) {
					continue;
				}
				String take = stocks[from];
				TradeOffer offer = new TradeOffer(new TradedItem(coins[from], 10), new ItemStack(coins[to], out), lots, 0, 0f);
				offers.add(offer);
				deals.put(offer, p -> {
					if (take != null) {
						faction.addCounter(take, 10);
					}
					if (give != null) {
						faction.addCounter(give, -out);
					}
					dirty(p);
				});
			}
		}
	}

	private static TradedItem amethyst(int count) {
		return new TradedItem(Items.AMETHYST_SHARD, Math.min(64, count));
	}

	private static void dirty(ServerPlayerEntity player) {
		WorldBetweenState.get(player.getServer()).markDirty();
	}
}
