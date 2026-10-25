package com.worldbetween.entity.kament;

/** Who a Kament is: decides their look, strength and work. */
public enum KamentRole {
	/** Quarries, sifts, carves: the hands of the people. */
	WORKER("worker"),
	/** Keeps the gates and the halls. Slow, and terrible when roused. */
	GUARD("guard"),
	/** A soul-sculptor: hews new Kaments and pours a part of its soul into each. */
	SCULPTOR("sculptor"),
	/** The Ancient of a city, one of the council. */
	ANCIENT("ancient");

	private final String id;

	KamentRole(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public boolean fights() {
		return this == GUARD || this == ANCIENT;
	}

	public static KamentRole byIndex(int index) {
		KamentRole[] roles = values();
		return index >= 0 && index < roles.length ? roles[index] : WORKER;
	}
}
