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
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(KamentModel.LAYER, KamentModel::getTexturedModelData);
		EntityRendererRegistry.register(WorldBetweenEntities.KAMENT, KamentRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(ArkiModel.LAYER, ArkiModel::getTexturedModelData);
		EntityRendererRegistry.register(WorldBetweenEntities.ARKI, ArkiRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(ManticoreModel.LAYER, ManticoreModel::getTexturedModelData);
		EntityRendererRegistry.register(WorldBetweenEntities.MANTICORE, ManticoreRenderer::new);
		EntityRendererRegistry.register(WorldBetweenEntities.MOROK, MorokRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(StoneHoundModel.LAYER, StoneHoundModel::getTexturedModelData);
		EntityRendererRegistry.register(WorldBetweenEntities.STONE_HOUND, StoneHoundRenderer::new);
		EntityRendererRegistry.register(WorldBetweenEntities.ARKI_ORB, net.minecraft.client.render.entity.EmptyEntityRenderer::new);
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			FactionType owner = Ownership.ownerOf(stack);
			if (owner != null) {
				lines.add(Text.translatable("tooltip.worldbetween.owner", owner.displayName()).formatted(Formatting.GRAY));
			}
			FactionType seller = Ownership.sellerOf(stack);
			if (seller != null) {
				lines.add(Text.translatable("tooltip.worldbetween.bought", seller.displayName()).formatted(Formatting.DARK_GREEN));
			}
			if (com.worldbetween.item.WorldBetweenItems.STONE_GEAR.contains(stack.getItem())) {
				lines.add(Text.translatable("item.worldbetween.stone_gear.tooltip").formatted(Formatting.DARK_PURPLE));
			}
		});
	}
}
