package com.worldbetween.trade;

import com.worldbetween.faction.Faction;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A little standing earned by bringing a people what it wants; only so much of it in a day. */
public final class Goodwill {
	private static final int MAX_PER_DAY = 6;
	private static final Map<String, long[]> EARNED = new HashMap<>();

	private Goodwill() {
	}

	public static void earn(ServerPlayerEntity player, Faction faction, int gain) {
		UUID id = player.getUuid();
		long day = WorldBetweenState.get(player.getServer()).day();
		long[] earned = EARNED.computeIfAbsent(faction.type().id() + "/" + id, k -> new long[]{day, 0});
		if (earned[0] != day) {
			earned[0] = day;
			earned[1] = 0;
		}
		if (earned[1] >= MAX_PER_DAY) {
			return;
		}
		int before = faction.reputation(id);
		faction.changeReputation(id, (int) Math.min(gain, MAX_PER_DAY - earned[1]));
		earned[1] += gain;
		if (earned[1] >= MAX_PER_DAY || before < 0 && faction.reputation(id) >= 0) {
			player.sendMessage(Text.translatable("message.worldbetween.trade.goodwill", faction.type().displayName()).formatted(Formatting.GREEN), true);
		}
	}
}
