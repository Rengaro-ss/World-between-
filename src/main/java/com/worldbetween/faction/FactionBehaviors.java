package com.worldbetween.faction;

import com.worldbetween.faction.neris.NerisBehavior;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of implemented faction behaviours. Factions without one stay dormant.
 */
public final class FactionBehaviors {
	private static final Map<FactionType, FactionBehavior> BEHAVIORS = new EnumMap<>(FactionType.class);

	static {
		BEHAVIORS.put(FactionType.NERIS, new NerisBehavior());
	}

	private FactionBehaviors() {
	}

	public static FactionBehavior get(FactionType type) {
		return BEHAVIORS.get(type);
	}
}
