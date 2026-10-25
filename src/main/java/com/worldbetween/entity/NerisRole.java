package com.worldbetween.entity;

/**
 * Who a Neris person is; decides their look, gear and behaviour.
 */
public enum NerisRole {
	CITIZEN("citizen"),
	WORKER("worker"),
	GUARD("guard"),
	SEEKER("seeker"),
	BARON("baron"),
	KING("king"),
	MERCHANT("merchant");

	private final String id;

	NerisRole(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public boolean fights() {
		return this == GUARD || this == SEEKER;
	}

	public static NerisRole byIndex(int index) {
		NerisRole[] roles = values();
		return index >= 0 && index < roles.length ? roles[index] : CITIZEN;
	}
}
