package com.worldbetween.item;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.kament.KamentSecurity;
import com.worldbetween.faction.FactionType;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.EnumMap;
import java.util.Map;

/**
 * What the stone path does for its bearer: the charms of the amulets in the inventory, and gear of the
 * mountain's stone mending itself, slowly, while it stands on rock (twice as fast on the Kaments' land).
 */
public final class StonePath {
	/** No more than this many charms sound at once; the first amulets in the inventory are the ones heard. */
	public static final int MAX_CHARMS = 3;
	private static final Identifier MEMORY = Identifier.of(WorldBetween.MOD_ID, "charm_memory");
	private static final int CHARM_INTERVAL = 20;
	private static final int MEND_INTERVAL = 100;

	private StonePath() {
	}

	public static void onServerTick(MinecraftServer server) {
		int tick = server.getTicks();
		if (tick % CHARM_INTERVAL != 0) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			charms(player);
			if (tick % MEND_INTERVAL == 0) {
				mend(player);
			}
		}
	}

	/** The strongest cut of each charm the player carries, the first {@link #MAX_CHARMS} kinds found. */
	public static Map<StoneCharm.Kind, Integer> carried(ServerPlayerEntity player) {
		Map<StoneCharm.Kind, Integer> found = new EnumMap<>(StoneCharm.Kind.class);
		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			StoneCharm charm = inventory.getStack(i).get(WorldBetweenItems.CHARM);
			if (charm == null || !found.containsKey(charm.kind()) && found.size() >= MAX_CHARMS) {
				continue;
			}
			found.merge(charm.kind(), charm.level(), Math::max);
		}
		return found;
	}

	private static void charms(ServerPlayerEntity player) {
		Map<StoneCharm.Kind, Integer> charms = carried(player);
		charms.forEach((kind, level) -> {
			int amplifier = Math.min(2, level) - 1;
			switch (kind) {
				case FIRM -> effect(player, StatusEffects.RESISTANCE, 60, amplifier);
				case CHISEL -> effect(player, StatusEffects.HASTE, 60, amplifier);
				case DEEP -> {
					BlockPos at = player.getBlockPos();
					if (at.getY() < 50 || player.getWorld().getLightLevel(LightType.SKY, at) < 6) {
						effect(player, StatusEffects.NIGHT_VISION, 300, 0);
					}
					if (level >= 2) {
						effect(player, StatusEffects.FIRE_RESISTANCE, 60, 0);
					}
				}
				case WARD -> {
					// A shell of stone that, once broken, comes back only after a minute.
					if (!player.hasStatusEffect(StatusEffects.ABSORPTION)) {
						player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1200, amplifier, true, false, true));
					}
				}
				default -> {
				}
			}
		});
		// Memory is held in the body itself, not as an effect: the hearts stay as long as the amulet does.
		EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null) {
			Integer level = charms.get(StoneCharm.Kind.MEMORY);
			EntityAttributeModifier current = health.getModifier(MEMORY);
			double wanted = level == null ? 0 : 4.0 * Math.min(2, level);
			if (current != null && current.value() != wanted) {
				health.removeModifier(MEMORY);
				current = null;
			}
			if (current == null && wanted > 0) {
				health.addTemporaryModifier(new EntityAttributeModifier(MEMORY, wanted, EntityAttributeModifier.Operation.ADD_VALUE));
			}
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
	}

	private static void effect(ServerPlayerEntity player, RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int duration, int amplifier) {
		StatusEffectInstance current = player.getStatusEffect(effect);
		if (current == null || current.getAmplifier() < amplifier || current.getDuration() < duration - CHARM_INTERVAL * 2) {
			player.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, true, false, true));
		}
	}

	private static void mend(ServerPlayerEntity player) {
		if (!player.isOnGround()) {
			return;
		}
		BlockPos below = player.getBlockPos().down();
		if (!player.getWorld().getBlockState(below).isIn(BlockTags.BASE_STONE_OVERWORLD)) {
			return;
		}
		int amount = KamentSecurity.territoryAt(player.getServerWorld(),
				WorldBetweenState.get(player.getServer()).faction(FactionType.KAMENT), player.getBlockPos()) != null ? 2 : 1;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getEquippedStack(slot);
			if (WorldBetweenItems.STONE_GEAR.contains(stack.getItem()) && stack.isDamaged()) {
				stack.setDamage(Math.max(0, stack.getDamage() - amount));
			}
		}
	}
}
