package com.worldbetween.faction.projects;

import com.worldbetween.faction.Faction;
import com.worldbetween.sim.SimulationContext;

/** What happens in the world the day a great work is finished. */
public final class Deeds {
	private Deeds() {
	}

	public static void completed(SimulationContext context, Faction faction, Project project) {
		switch (project) {
			case HELL_CAMPAIGN -> {
				// The fifth age: the kingdom holds ground in the Nether at last.
				faction.setEra(Math.max(faction.era(), 4));
				context.chronicle(faction.type(), "chronicle.worldbetween.neris.era_4");
			}
			case MOROK -> Morok.wake(context, faction);
			default -> {
			}
		}
		context.state().markDirty();
	}
}
