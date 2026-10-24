package com.worldbetween;

import com.worldbetween.command.WorldBetweenCommand;
import com.worldbetween.config.WorldBetweenConfig;
import com.worldbetween.sim.WorldSimulation;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
		ServerTickEvents.END_SERVER_TICK.register(WorldSimulation::onServerTick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
				WorldBetweenCommand.register(dispatcher));
		LOGGER.info("World Between initialized");
	}
}
