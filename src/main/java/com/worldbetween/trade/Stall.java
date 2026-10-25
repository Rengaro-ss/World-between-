package com.worldbetween.trade;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What a trader of the Kaments or the Arki has in hand while dealing with a stranger: who it is, what is on
 * offer, and what each trade means to the people behind it. Their entities hand the Merchant calls to it.
 */
public final class Stall {
	private PlayerEntity customer;
	private TradeOfferList offers = new TradeOfferList();
	private Map<TradeOffer, Consumer<ServerPlayerEntity>> deals = new IdentityHashMap<>();

	public void open(PlayerEntity player, TradeOfferList offers, Map<TradeOffer, Consumer<ServerPlayerEntity>> deals) {
		this.offers = offers;
		this.deals = deals;
		this.customer = player;
	}

	public PlayerEntity customer() {
		return customer;
	}

	public void setCustomer(PlayerEntity customer) {
		this.customer = customer;
	}

	public TradeOfferList offers() {
		return offers;
	}

	public void setOffers(TradeOfferList offers) {
		this.offers = offers;
	}

	public void trade(TradeOffer offer) {
		offer.use();
		Consumer<ServerPlayerEntity> deal = deals.get(offer);
		if (deal != null && customer instanceof ServerPlayerEntity player) {
			deal.accept(player);
		}
	}

	/** The trader stands still and looks at its customer; the deal is off if the customer walks away. */
	public void tick(MobEntity trader) {
		if (customer == null) {
			return;
		}
		if (customer.isRemoved() || trader.squaredDistanceTo(customer) > 64 || !(customer.currentScreenHandler instanceof MerchantScreenHandler)) {
			customer = null;
		} else {
			trader.getNavigation().stop();
			// Hold still, flyers too, for as long as the deal lasts.
			trader.getMoveControl().moveTo(trader.getX(), trader.getY(), trader.getZ(), 0);
			trader.setVelocity(trader.getVelocity().multiply(0.2));
			trader.getLookControl().lookAt(customer);
		}
	}

	public void close() {
		if (customer instanceof ServerPlayerEntity player && player.currentScreenHandler instanceof MerchantScreenHandler) {
			player.closeHandledScreen();
		}
	}

	/** The Merchant calls a trader entity hands on to its stall. */
	public interface Keeper extends Merchant {
		Stall stall();

		@Override
		default void setCustomer(PlayerEntity customer) {
			stall().setCustomer(customer);
		}

		@Override
		default PlayerEntity getCustomer() {
			return stall().customer();
		}

		@Override
		default TradeOfferList getOffers() {
			return stall().offers();
		}

		@Override
		default void setOffersFromServer(TradeOfferList offers) {
			stall().setOffers(offers);
		}

		@Override
		default void trade(TradeOffer offer) {
			stall().trade(offer);
		}

		@Override
		default void onSellingItem(ItemStack stack) {
		}

		@Override
		default int getExperience() {
			return 0;
		}

		@Override
		default void setExperienceFromServer(int experience) {
		}

		@Override
		default boolean isLeveledMerchant() {
			return false;
		}

		@Override
		default SoundEvent getYesSound() {
			return SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
		}
	}
}
