package com.worldbetween;

import com.worldbetween.command.WorldBetweenCommand;
import com.worldbetween.config.WorldBetweenConfig;
import com.worldbetween.item.Ownership;
import com.worldbetween.mine.MineManager;
import com.worldbetween.mine.MineSecurity;
import com.worldbetween.sim.WorldSimulation;
import com.worldbetween.town.TownActivity;
import com.worldbetween.town.TownManager;
import com.worldbetween.world.WorldBetweenAttachments;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldBetween implements ModInitializer {
	public static final String MOD_ID = "worldbetween";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static WorldBetweenConfig config;

	public static WorldBetweenConfig config() {
		return config;
	}

	@Override
	public void onInitialize() {
		config = WorldBetweenConfig.load();
		Ownership.register();
		com.worldbetween.item.WorldBetweenItems.register();
		com.worldbetween.entity.WorldBetweenEntities.register();
		com.worldbetween.entity.kament.KamentEffects.register();
		WorldBetweenAttachments.register();
		ServerTickEvents.END_SERVER_TICK.register(WorldSimulation::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(MineManager::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(MineSecurity::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(TownManager::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(TownActivity::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.entity.NerisPresence::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.town.TownSecurity::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.entity.Arrival::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.entity.kament.KamentPresence::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.entity.kament.KamentSecurity::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.entity.kament.KamentAwakening::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.entity.arki.ArkiPresence::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.entity.arki.ArkiSky::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.item.StonePath::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.war.WarPresence::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.war.Repairs::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.war.Weapons::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.faction.projects.NetherFront::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.faction.projects.NightSky::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.faction.projects.MorokPresence::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.faction.projects.WorksPresence::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(com.worldbetween.entity.arki.ArkiSecurity::onServerTick);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(com.worldbetween.town.TownSecurity::onKill);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(com.worldbetween.war.WarPresence::onDeath);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) ->
				{
					com.worldbetween.entity.NerisPresence.onEntityLoad(entity);
					com.worldbetween.faction.projects.NetherFront.onEntityLoad(entity, world);
				});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
				MineSecurity.onBlockBroken(serverPlayer, serverWorld, pos, state);
				com.worldbetween.entity.kament.KamentSecurity.onBlockBroken(serverPlayer, serverWorld, pos, state);
				com.worldbetween.entity.arki.ArkiSecurity.onBlockBroken(serverPlayer, serverWorld, pos, state);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MineSecurity.onDisconnect(handler.getPlayer());
			com.worldbetween.town.TownSecurity.onDisconnect(handler.getPlayer());
			com.worldbetween.entity.kament.KamentSecurity.onDisconnect(handler.getPlayer());
			com.worldbetween.entity.arki.ArkiSecurity.onDisconnect(handler.getPlayer());
		});
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(com.worldbetween.entity.arki.ArkiSecurity::onUseBlock);
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
				WorldBetweenCommand.register(dispatcher));
		LOGGER.info("World Between initialized");
	}
}
