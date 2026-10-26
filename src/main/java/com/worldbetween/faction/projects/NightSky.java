package com.worldbetween.faction.projects;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The Eternal Night of the Arki. While their tower stands whole the sun does not come up: when the night
 * ends the next one begins at once. Days still pass (the world still counts them), but every one of them
 * is a night, and the Arki are at their strongest in it. Break the tower and the day comes back until the
 * builders have mended it.
 */
public final class NightSky {
	/** The first moment of night, and the last, in ticks of the day. */
	private static final long DUSK = 13000;
	private static final long DAWN = 23200;
	private static boolean told;

	private NightSky() {
	}

	public static boolean eternal(MinecraftServer server) {
		Faction arki = WorldBetweenState.get(server).faction(FactionType.ARKI);
		return WorksFeatures.working(arki, Project.NIGHT_TOWER);
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % 10 != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		if (!eternal(server)) {
			told = false;
			return;
		}
		long time = world.getTimeOfDay();
		long tod = time % 24000L;
		if (tod >= DAWN) {
			world.setTimeOfDay(time - tod + 24000L + DUSK); // straight on into the next night
		} else if (tod < DUSK) {
			world.setTimeOfDay(time - tod + DUSK);
		} else {
			return;
		}
		if (!told) {
			told = true;
			for (ServerPlayerEntity player : world.getPlayers()) {
				player.sendMessage(Text.translatable("message.worldbetween.arki.eternal_night").formatted(Formatting.DARK_PURPLE), false);
			}
		}
	}
}
