package com.worldbetween.trade;

import com.worldbetween.entity.kament.KamentEntity;
import com.worldbetween.entity.kament.KamentSecurity;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.kament.Ancient;
import com.worldbetween.faction.kament.Focus;
import com.worldbetween.faction.kament.Kaments;
import com.worldbetween.item.StoneAmuletItem;
import com.worldbetween.item.StoneCharm;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Trading with the Kaments, in their own coin: shards of flint. The carvers deal in stone and open the stone
 * path to a friend (the chisel, hewn granite, heartstone); the Ancients cut charms into amulets. Heartstone
 * holds a spark of a sculptor's soul, so every one sold sets the city's growth back a little.
 * Nobody deals with them showing metal.
 */
public final class KamentTrade {
	public static final int CHISEL_REPUTATION = 20;
	public static final int HEARTSTONE_REPUTATION = 40;
	public static final int CHARM_REPUTATION = 30;
	public static final int SECOND_CUT_REPUTATION = 60;
	private static final int HEARTSTONE_SOUL = 10;
	private static final int MAX_LOTS = 8;

	private record Sale(Item item, int count, int price, int minReputation, ResourceType resource, int units) {
	}

	private record Purchase(Item item, int count, int pay, ResourceType resource, int units) {
	}

	private static final List<Sale> SALES = List.of(
			new Sale(Items.STONE_BRICKS, 16, 3, 0, ResourceType.BRICKS, 4),
			new Sale(Items.CHISELED_STONE_BRICKS, 8, 4, 0, ResourceType.BRICKS, 3),
			new Sale(Items.POLISHED_DIORITE, 16, 3, 0, ResourceType.DIORITE, 6),
			new Sale(Items.TUFF_BRICKS, 16, 4, 0, ResourceType.STONE, 8),
			new Sale(Items.GRAVEL, 16, 2, 0, ResourceType.GRAVEL, 8),
			new Sale(WorldBetweenItems.CHISEL, 1, 16, CHISEL_REPUTATION, null, 0),
			new Sale(WorldBetweenItems.HEWN_GRANITE, 2, 6, CHISEL_REPUTATION, ResourceType.BRICKS, 2));

	private static final List<Purchase> PURCHASES = List.of(
			new Purchase(Items.COBBLESTONE, 32, 2, ResourceType.STONE, 16),
			new Purchase(Items.COBBLED_DEEPSLATE, 32, 3, ResourceType.STONE, 16),
			new Purchase(Items.GRAVEL, 16, 2, ResourceType.GRAVEL, 16),
			new Purchase(Items.DIORITE, 16, 3, ResourceType.DIORITE, 16));

	private KamentTrade() {
	}

	/** A stranger talks to a carver or an Ancient. Returns false if there is to be no trade. */
	public static boolean open(KamentEntity trader, ServerPlayerEntity player, Faction faction, Settlement settlement, boolean ancient) {
		int reputation = faction.reputation(player.getUuid());
		if (faction.isCriminal(player.getUuid())) {
			return false;
		}
		if (KamentSecurity.showsMetal(player)) {
			trader.say(player, "speech.worldbetween.kament.trade.metal");
			return true;
		}
		if (reputation < 0) {
			trader.say(player, "speech.worldbetween.kament.trade.refuse");
			return true;
		}
		float factor = NerisTrade.priceFactor(reputation);
		TradeOfferList offers = new TradeOfferList();
		Map<TradeOffer, Consumer<ServerPlayerEntity>> deals = new IdentityHashMap<>();
		if (ancient) {
			charms(faction, settlement, reputation, factor, offers, deals);
		} else {
			stone(faction, settlement, reputation, factor, offers, deals);
		}
		if (offers.isEmpty()) {
			trader.say(player, ancient ? "speech.worldbetween.kament.trade.charm_refuse" : "speech.worldbetween.kament.trade.empty");
			return true;
		}
		trader.say(player, ancient ? "speech.worldbetween.kament.trade.charm_greet" : "speech.worldbetween.kament.trade.greet");
		trader.stall().open(player, offers, deals);
		trader.sendOffers(player, trader.getCustomName() != null ? trader.getCustomName() : trader.getName(), 0);
		return true;
	}

	private static void stone(Faction faction, Settlement settlement, int reputation, float factor, TradeOfferList offers,
			Map<TradeOffer, Consumer<ServerPlayerEntity>> deals) {
		for (Sale sale : SALES) {
			if (reputation < sale.minReputation()) {
				continue;
			}
			int lots = sale.resource() == null ? 1 : (int) Math.min(MAX_LOTS, (faction.resource(sale.resource()) - 40) / sale.units());
			if (lots < 1) {
				continue;
			}
			int price = Math.max(1, Math.round(sale.price() * factor));
			TradeOffer offer = new TradeOffer(shards(price), new ItemStack(sale.item(), sale.count()), lots, 0, 0f);
			offers.add(offer);
			deals.put(offer, player -> {
				if (sale.resource() != null) {
					faction.takeResource(sale.resource(), sale.units());
				}
				faction.addCounter("shards", price);
				dirty(player);
			});
		}
		// Heartstone: a spark of the sculptor's soul, given only to a friend, and only while there is soul to spare.
		if (reputation >= HEARTSTONE_REPUTATION && settlement.counter("sculptors") > 0 && settlement.counter("soul") >= HEARTSTONE_SOUL * 2) {
			int price = Math.max(1, Math.round(32 * factor));
			int lots = (int) Math.min(2, settlement.counter("soul") / HEARTSTONE_SOUL - 1);
			TradeOffer offer = new TradeOffer(shards(price), new ItemStack(WorldBetweenItems.HEARTSTONE), lots, 0, 0f);
			offers.add(offer);
			deals.put(offer, player -> {
				settlement.addCounter("soul", -HEARTSTONE_SOUL);
				faction.addCounter("shards", price);
				dirty(player);
			});
		}
		long treasury = treasury(faction);
		for (Purchase purchase : PURCHASES) {
			int pay = Math.max(1, (int) Math.floor(purchase.pay() / factor));
			int lots = (int) Math.min(MAX_LOTS, treasury / pay);
			if (lots < 1 || faction.resource(purchase.resource()) > 600) {
				continue;
			}
			TradeOffer offer = new TradeOffer(new TradedItem(purchase.item(), purchase.count()), Optional.empty(),
					new ItemStack(WorldBetweenItems.FLINT_SHARD, pay), lots, 0, 0f);
			offers.add(offer);
			deals.put(offer, player -> {
				faction.addResource(purchase.resource(), purchase.units());
				spend(faction, pay);
				Goodwill.earn(player, faction, 1);
				dirty(player);
			});
		}
	}

	/** The Ancient cuts the charm of what it holds most dear, and of what the council has set the people to. */
	private static void charms(Faction faction, Settlement settlement, int reputation, float factor, TradeOfferList offers,
			Map<TradeOffer, Consumer<ServerPlayerEntity>> deals) {
		if (reputation < CHARM_REPUTATION) {
			return;
		}
		Set<Focus> foci = new LinkedHashSet<>();
		Ancient ancient = Kaments.ancientOf(faction, settlement);
		if (ancient != null) {
			foci.add(ancient.first().leaning());
			foci.add(ancient.second().leaning());
		}
		foci.add(Kaments.focus(faction));
		for (Focus focus : foci) {
			StoneCharm.Kind kind = StoneCharm.Kind.of(focus);
			int price = Math.max(1, Math.round(24 * factor));
			TradeOffer first = new TradeOffer(new TradedItem(WorldBetweenItems.BLANK_AMULET), Optional.of(shards(price)),
					StoneAmuletItem.of(kind, 1), 2, 0, 0f);
			offers.add(first);
			deals.put(first, player -> {
				faction.addCounter("shards", price);
				dirty(player);
			});
			if (reputation >= SECOND_CUT_REPUTATION) {
				TradedItem amulet = new TradedItem(WorldBetweenItems.STONE_AMULET, 1)
						.withComponents(components -> components.add(WorldBetweenItems.CHARM, new StoneCharm(kind, 1)));
				TradeOffer second = new TradeOffer(amulet, Optional.of(new TradedItem(WorldBetweenItems.HEARTSTONE, 2)),
						StoneAmuletItem.of(kind, 2), 1, 0, 0f);
				offers.add(second);
				deals.put(second, KamentTrade::dirty);
			}
		}
	}

	/** What the Kaments can pay with: the shards they hold, and flint they could break into more. */
	private static long treasury(Faction faction) {
		return faction.counter("shards") + faction.resource(ResourceType.FLINT) * 9 / 2;
	}

	private static void spend(Faction faction, int shards) {
		long held = faction.counter("shards");
		if (held < shards) {
			long flint = (shards - held + 8) / 9;
			faction.takeResource(ResourceType.FLINT, flint);
			faction.addCounter("shards", flint * 9);
		}
		faction.addCounter("shards", -shards);
	}

	private static TradedItem shards(int count) {
		return new TradedItem(WorldBetweenItems.FLINT_SHARD, Math.min(64, count));
	}

	private static void dirty(ServerPlayerEntity player) {
		WorldBetweenState.get(player.getServer()).markDirty();
	}
}
