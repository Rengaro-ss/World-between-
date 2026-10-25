package com.worldbetween.item;

import com.worldbetween.WorldBetween;
import com.worldbetween.faction.FactionType;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Ownership marks. Everything a faction mines, forges or builds carries the name of its owner,
 * so stolen goods can always be recognised.
 */
public final class Ownership {
	public static final ComponentType<FactionType> OWNER = ComponentType.<FactionType>builder()
			.codec(FactionType.CODEC)
			.packetCodec(PacketCodecs.STRING.xmap(FactionType::byId, FactionType::id))
			.build();

	private Ownership() {
	}

	public static void register() {
		Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(WorldBetween.MOD_ID, "owner"), OWNER);
	}

	public static ItemStack mark(ItemStack stack, FactionType owner) {
		if (!stack.isEmpty()) {
			stack.set(OWNER, owner);
		}
		return stack;
	}

	public static FactionType ownerOf(ItemStack stack) {
		return stack.get(OWNER);
	}
}
