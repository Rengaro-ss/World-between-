package com.worldbetween.sim;

import com.worldbetween.WorldBetween;
import com.worldbetween.config.WorldBetweenConfig;
import com.worldbetween.faction.FactionType;
import com.worldbetween.world.ChronicleEntry;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.random.Random;

import java.util.Arrays;

/**
 * What a faction behaviour can see and touch during one simulation day.
 */
public class SimulationContext {
	private final MinecraftServer server;
	private final WorldBetweenState state;
	private final Random random;

	public SimulationContext(MinecraftServer server, WorldBetweenState state) {
		this.server = server;
		this.state = state;
		// Deterministic per world and day, so the same seed tells the same history.
		this.random = Random.create(server.getOverworld().getSeed() ^ (state.day() * 0x9E3779B97F4A7C15L));
	}

	public MinecraftServer server() {
		return server;
	}

	public WorldBetweenState state() {
		return state;
	}

	public long day() {
		return state.day();
	}

	public Random random() {
		return random;
	}

	public WorldBetweenConfig config() {
		return WorldBetween.config();
	}

	public void chronicle(FactionType faction, String key, Object... args) {
		ChronicleEntry entry = new ChronicleEntry(state.day(), faction, key,
				Arrays.stream(args).map(String::valueOf).toList());
		state.addChronicle(entry);
		WorldBetween.LOGGER.info("[Chronicle] day {} {}: {} {}", entry.day(), faction, key, entry.args());
		if (config().broadcastChronicle) {
			server.getPlayerManager().broadcast(entry.toText(), false);
		}
	}
}
