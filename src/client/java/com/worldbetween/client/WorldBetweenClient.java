package com.worldbetween.client;

import com.worldbetween.faction.FactionType;
import com.worldbetween.item.Ownership;
import com.worldbetween.entity.WorldBetweenEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class WorldBetweenClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(WorldBetweenEntities.NERIS, NerisRenderer::new);
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			FactionType owner = Ownership.ownerOf(stack);
			if (owner != null) {
				lines.add(Text.translatable("tooltip.worldbetween.owner", owner.displayName()).formatted(Formatting.GRAY));
			}
			FactionType seller = Ownership.sellerOf(stack);
			if (seller != null) {
				lines.add(Text.translatable("tooltip.worldbetween.bought", seller.displayName()).formatted(Formatting.DARK_GREEN));
			}
		});
	}
}
