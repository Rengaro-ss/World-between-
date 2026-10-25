package com.worldbetween.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.worldbetween.WorldBetween;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side settings, stored in config/worldbetween.json.
 */
public class WorldBetweenConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Day on which the Kingdom of Neris comes through its portal. */
	public int nerisArrivalDay = 3;
	/** Arrival points are picked in a ring around world spawn, in blocks. */
	public int minArrivalDistance = 400;
	public int maxArrivalDistance = 900;
	/** New settlements are founded this far from their parent, in blocks. */
	public int minExpansionDistance = 220;
	public int maxExpansionDistance = 380;
	/** Mine tunnel each staffed mine digs per day, in blocks. */
	public int mineStepsPerDay = 8;
	/** Time the guards give a noticed stranger to leave a mine, in seconds. */
	public int trespassGraceSeconds = 180;
	/** Chance per second that miners notice a stranger in their tunnels. */
	public float noticeChancePerSecond = 0.15f;
	/** Reputation at which a faction lets a player walk its mines freely. */
	public int friendlyReputation = 30;
	/** Below this reputation, guards turn a stranger out of the kingdom's towns. */
	public int townEntryReputation = -20;
	/** Below this reputation, a stranger may not stay inside a walled town at night. */
	public int curfewReputation = 10;
	/** Reputation a baron wants before receiving a stranger in the town hall. */
	public int hallReputation = 20;
	/** Reputation the king wants before granting a stranger an audience. */
	public int throneReputation = 50;
	/** Time a stranger turned out of a town gets to leave, in seconds. */
	public int townGraceSeconds = 60;
	/** Time a stranger caught out after curfew gets to leave, in seconds. */
	public int curfewGraceSeconds = 90;
	/** Time an intruder in a hall or a closed building gets to get out, in seconds. */
	public int intrusionGraceSeconds = 10;
	/** Below this reputation, merchants will not trade with a stranger. */
	public int tradeReputation = -20;
	/** Announce chronicle events to all players in chat. */
	public boolean broadcastChronicle = true;

	public static WorldBetweenConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(WorldBetween.MOD_ID + ".json");
		WorldBetweenConfig config = new WorldBetweenConfig();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				WorldBetweenConfig loaded = GSON.fromJson(reader, WorldBetweenConfig.class);
				if (loaded != null) {
					config = loaded;
				}
			} catch (IOException | RuntimeException e) {
				WorldBetween.LOGGER.error("Failed to read {}, using defaults", path, e);
			}
		}
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(config, writer);
		} catch (IOException e) {
			WorldBetween.LOGGER.error("Failed to write {}", path, e);
		}
		return config;
	}
}
