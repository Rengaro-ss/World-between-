package com.worldbetween.entity;

import com.worldbetween.faction.FactionType;

/** One of the peoples' folk in the world: whose they are, and whether they take up arms. */
public interface FactionMember {
	FactionType faction();

	/** Whether this one fights when it meets the enemy. */
	boolean fights();
}
