package com.worldbetween.trade;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.NerisEntity;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.item.Ownership;
import com.worldbetween.town.TownSecurity;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trading with the merchants of Neris. What they sell comes out of the kingdom's real stores, and only
 * what it can spare; what they buy is what the kingdom is short of, paid in iron nuggets from the town's
 * treasury. Prices follow the stranger's standing, and selling the kingdom what it needs earns a little goodwill.
 */
public final class NerisTrade {
	private static final int MAX_LOTS = 8;
	private static final int MAX_HELP_PER_DAY = 6;

	/** What a trade means to the kingdom: so much of a resource out or in, so many coins the other way. */
	public record Deal(ResourceType resource, int units, int coins, boolean kingdomSells, boolean scarce) {
	}

	private record Sale(ResourceType resource, int units, Item item, int count, int price, int minEra) {
	}

	private record Purchase(ResourceType resource, int units, Item item, int count, int pay) {
	}

	private static final List<Sale> SALES = List.of(
			new Sale(ResourceType.FOOD, 6, Items.BREAD, 6, 3, 0),
			new Sale(ResourceType.MEAT, 4, Items.COOKED_MUTTON, 4, 3, 0),
			new Sale(ResourceType.WOOD, 8, Items.DARK_OAK_PLANKS, 24, 4, 0),
			new Sale(ResourceType.BRICKS, 4, Items.STONE_BRICKS, 16, 5, 0),
			new Sale(ResourceType.FUEL, 4, Items.CHARCOAL, 8, 4, 0),
			new Sale(ResourceType.LEATHER, 2, Items.LEATHER, 4, 5, 0),
			new Sale(ResourceType.CLOTH, 2, Items.ORANGE_WOOL, 8, 4, 0),
			new Sale(ResourceType.METAL, 1, Items.IRON_INGOT, 1, 11, 0),
			new Sale(ResourceType.EQUIPMENT, 1, Items.SHIELD, 1, 20, 0),
			new Sale(ResourceType.EQUIPMENT, 1, Items.IRON_SWORD, 1, 28, 0),
			new Sale(ResourceType.EQUIPMENT, 1, Items.IRON_CHESTPLATE, 1, 56, 1),
			new Sale(ResourceType.METAL, 3, Items.CHAIN, 4, 30, 1));

	private static final List<Purchase> PURCHASES = List.of(
			new Purchase(ResourceType.GRAIN, 20, Items.WHEAT, 20, 3),
			new Purchase(ResourceType.FOOD, 16, Items.POTATO, 16, 2),
			new Purchase(ResourceType.LOGS, 16, Items.OAK_LOG, 16, 3),
			new Purchase(ResourceType.LOGS, 16, Items.SPRUCE_LOG, 16, 3),
			new Purchase(ResourceType.STONE, 16, Items.COBBLESTONE, 32, 2),
			new Purchase(ResourceType.FUEL, 8, Items.COAL, 8, 4),
			new Purchase(ResourceType.ORE, 8, Items.RAW_COPPER, 16, 4),
			new Purchase(ResourceType.ORE, 8, Items.RAW_IRON, 8, 14),
			new Purchase(ResourceType.LEATHER, 6, Items.LEATHER, 6, 5),
			new Purchase(ResourceType.WOOL, 8, Items.WHITE_WOOL, 8, 3));

	/** Goodwill earned from trade today, per player: day and amount. */
	private static final Map<UUID, long[]> HELP = new HashMap<>();

	private NerisTrade() {
	}

	/** A stranger talks to a merchant: either the goods come out, or the merchant will not deal with them. */
	public static void open(NerisEntity merchant, ServerPlayerEntity player, Faction faction, Settlement market) {
		Text name = merchant.getCustomName() != null ? merchant.getCustomName() : merchant.getName();
		UUID id = player.getUuid();
		int reputation = faction.reputation(id);
		if (faction.isCriminal(id) || reputation < WorldBetween.config().tradeReputation || market == null) {
			say(player, name, faction.isCriminal(id) ? "speech.worldbetween.merchant.criminal" : "speech.worldbetween.merchant.refuse");
			return;
		}
		TradeOfferList offers = new TradeOfferList();
		Map<TradeOffer, Deal> deals = new IdentityHashMap<>();
		float factor = priceFactor(reputation);
		int population = faction.totalPopulation();
		long treasury = market.coins() + faction.resource(ResourceType.COINS);

		for (Sale sale : SALES) {
			long reserve = reserve(sale.resource(), population, faction);
			long spare = faction.resource(sale.resource()) - reserve;
			int lots = (int) Math.min(MAX_LOTS, spare / sale.units());
			if (lots < 1 || faction.era() < sale.minEra()) {
				continue;
			}
			int price = Math.max(1, (int) Math.ceil(sale.price() * factor));
			ItemStack goods = Ownership.markBought(new ItemStack(sale.item(), sale.count()), faction.type());
			TradeOffer offer = new TradeOffer(nuggets(Math.min(64, price)), price > 64 ? Optional.of(nuggets(price - 64)) : Optional.empty(),
					goods, lots, 0, 0f);
			offers.add(offer);
			deals.put(offer, new Deal(sale.resource(), sale.units(), price, true, false));
		}
		for (Purchase purchase : PURCHASES) {
			long target = target(purchase.resource(), population);
			long have = faction.resource(purchase.resource());
			if (have >= target) {
				continue;
			}
			boolean scarce = have < target / 3;
			int pay = Math.max(1, (int) Math.floor(purchase.pay() / factor * (scarce ? 1.5f : 1f)));
			int lots = (int) Math.min(Math.min(MAX_LOTS, (target - have) / purchase.units() + 1), treasury / pay);
			if (lots < 1) {
				continue;
			}
			TradeOffer offer = new TradeOffer(new TradedItem(purchase.item(), purchase.count()), Optional.empty(),
					new ItemStack(Items.IRON_NUGGET, pay), lots, 0, 0f);
			offers.add(offer);
			deals.put(offer, new Deal(purchase.resource(), purchase.units(), pay, false, scarce));
		}
		if (offers.isEmpty()) {
			say(player, name, "speech.worldbetween.merchant.empty");
			return;
		}
		say(player, name, "speech.worldbetween.merchant.greet." + TownSecurity.standing(faction, id));
		merchant.beginTrade(player, offers, deals, market.id());
		merchant.sendOffers(player, name, 0);
	}

	/** A trade went through: the kingdom's stores and treasury change, and helping it is remembered. */
	public static void onTrade(ServerPlayerEntity player, Faction faction, Settlement market, Deal deal) {
		if (deal.kingdomSells()) {
			faction.takeResource(deal.resource(), deal.units());
			market.addCoins(deal.coins());
		} else {
			faction.addResource(deal.resource(), deal.units());
			long fromTown = Math.min(market.coins(), deal.coins());
			market.addCoins(-fromTown);
			faction.takeResource(ResourceType.COINS, deal.coins() - fromTown);
			long day = WorldBetweenState.get(player.getServer()).day();
			long[] help = HELP.computeIfAbsent(player.getUuid(), k -> new long[]{day, 0});
			if (help[0] != day) {
				help[0] = day;
				help[1] = 0;
			}
			int gain = deal.scarce() ? 2 : 1;
			if (help[1] < MAX_HELP_PER_DAY) {
				int before = faction.reputation(player.getUuid());
				faction.changeReputation(player.getUuid(), (int) Math.min(gain, MAX_HELP_PER_DAY - help[1]));
				help[1] += gain;
				if (help[1] >= MAX_HELP_PER_DAY || before < 0 && faction.reputation(player.getUuid()) >= 0) {
					player.sendMessage(Text.translatable("message.worldbetween.trade.goodwill", faction.type().displayName())
							.formatted(Formatting.GREEN), true);
				}
			}
		}
		WorldBetweenState.get(player.getServer()).markDirty();
	}

	/** Friends get a better price; strangers under a cloud pay more. */
	public static float priceFactor(int reputation) {
		if (reputation >= 60) {
			return 0.75f;
		}
		if (reputation >= 30) {
			return 0.85f;
		}
		return reputation >= 0 ? 1f : 1.3f;
	}

	/** What the kingdom keeps back for itself before it sells any. */
	private static long reserve(ResourceType resource, int population, Faction faction) {
		return switch (resource) {
			case FOOD -> 3L * population + 30;
			case MEAT -> 20;
			case WOOD -> 80;
			case BRICKS -> 60;
			case FUEL -> 40;
			case METAL -> 30;
			case EQUIPMENT -> 6 + faction.soldiers() / 20;
			default -> 20;
		};
	}

	/** How much of a resource the kingdom would like to have before it stops buying. */
	private static long target(ResourceType resource, int population) {
		return switch (resource) {
			case GRAIN, FOOD -> 4L * population + 40;
			case LOGS, STONE -> 200;
			case FUEL -> 80;
			case ORE -> 60;
			case LEATHER -> 30;
			case WOOL -> 40;
			default -> 0;
		};
	}

	private static TradedItem nuggets(int count) {
		return new TradedItem(Items.IRON_NUGGET, count);
	}

	private static void say(ServerPlayerEntity player, Text name, String key) {
		player.sendMessage(Text.empty().append(name.copy().formatted(Formatting.GOLD)).append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(Text.translatable(key).formatted(Formatting.WHITE)), false);
	}
}
