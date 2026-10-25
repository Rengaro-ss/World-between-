package com.worldbetween;

import com.worldbetween.command.WorldBetweenCommand;
import com.worldbetween.config.WorldBetweenConfig;
import com.worldbetween.item.Ownership;
import com.worldbetween.mine.MineManager;
import com.worldbetween.mine.MineSecurity;
import com.worldbetween.sim.WorldSimulation;
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
		WorldBetweenAttachments.register();
		ServerTickEvents.END_SERVER_TICK.register(WorldSimulation::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(MineManager::onServerTick);
		ServerTickEvents.END_SERVER_TICK.register(MineSecurity::onServerTick);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
				MineSecurity.onBlockBroken(serverPlayer, serverWorld, pos, state);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MineSecurity.onDisconnect(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
				WorldBetweenCommand.register(dispatcher));
		LOGGER.info("World Between initialized");
	}
}
