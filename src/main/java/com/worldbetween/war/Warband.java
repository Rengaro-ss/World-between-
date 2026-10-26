package com.worldbetween.war;

import com.worldbetween.faction.FactionType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * A war party in the field: a border patrol, a raiding band, an army on campaign, or the defenders marching
 * out to meet one. It moves in a straight line at its own pace; armies are slow. A player who comes near
 * meets it wherever it has got to, and whatever happens there in person is what happened.
 */
public class Warband {
	public enum Kind {
		/** A few fighters walking out towards the border and back. */
		PATROL("patrol", 300, 3),
		/** A raiding band that lies in wait near an enemy town, ambushes and sabotages. */
		RAID("raid", 260, 5),
		/** An army marching on an enemy town. */
		ARMY("army", 110, 10),
		/** The defenders marching out to meet an army before it reaches the walls. */
		DEFENDERS("defenders", 140, 10);

		private final String id;
		private final int speed;
		private final int shown;

		Kind(String id, int speed, int shown) {
			this.id = id;
			this.speed = speed;
			this.shown = shown;
		}

		public String id() {
			return id;
		}

		/** Blocks covered in a day. */
		public int speed() {
			return speed;
		}

		/** How many of them a player sees at once; the rest come up from behind as these fall. */
		public int shown() {
			return shown;
		}

		public boolean isArmy() {
			return this == ARMY || this == DEFENDERS;
		}
	}

	public enum State {
		MARCHING, WAITING, RETURNING
	}

	/** Home of a band set down by hand for a test: its survivors go back to nobody. */
	public static final int TEST = -2;

	private final int id;
	private final FactionType faction;
	private final Kind kind;
	private final int home;
	private final FactionType enemy;
	/** The enemy settlement marched on, or -1. */
	private final int target;
	private BlockPos from;
	private BlockPos to;
	private BlockPos homePos;
	private double departed;
	private State state = State.MARCHING;
	private int fighters;
	private long waitUntil;
	/** The enemy band this one marches to meet, or is being met by; -1 if none. */
	private int opponent = -1;
	/** Day this band last fought in person, so the simulation does not fight the same fight again. */
	private long foughtLive = -1;
	private boolean sabotaged;
	/** Manticores with their riders, the kingdom's beasts of war. */
	private int beasts;

	public Warband(int id, FactionType faction, Kind kind, int home, BlockPos homePos, FactionType enemy, int target,
			BlockPos to, int fighters, double now) {
		this.id = id;
		this.faction = faction;
		this.kind = kind;
		this.home = home;
		this.homePos = homePos.withY(0);
		this.enemy = enemy;
		this.target = target;
		this.from = homePos.withY(0);
		this.to = to.withY(0);
		this.fighters = fighters;
		this.departed = now;
	}

	public int id() {
		return id;
	}

	public FactionType faction() {
		return faction;
	}

	public Kind kind() {
		return kind;
	}

	public int home() {
		return home;
	}

	public BlockPos homePos() {
		return homePos;
	}

	public FactionType enemy() {
		return enemy;
	}

	public int target() {
		return target;
	}

	public BlockPos destination() {
		return to;
	}

	public State state() {
		return state;
	}

	public int fighters() {
		return fighters;
	}

	public void setFighters(int fighters) {
		this.fighters = Math.max(0, fighters);
	}

	public long waitUntil() {
		return waitUntil;
	}

	public int opponent() {
		return opponent;
	}

	public void setOpponent(int opponent) {
		this.opponent = opponent;
	}

	public long foughtLive() {
		return foughtLive;
	}

	public void setFoughtLive(long day) {
		this.foughtLive = day;
	}

	public int beasts() {
		return beasts;
	}

	public void setBeasts(int beasts) {
		this.beasts = Math.max(0, beasts);
	}

	public boolean hasSabotaged() {
		return sabotaged;
	}

	public void setSabotaged() {
		this.sabotaged = true;
	}

	private double length() {
		return Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
	}

	/** Where they are at time {@code now} (in days), at ground level 0. */
	public BlockPos position(double now) {
		if (state == State.WAITING) {
			return to;
		}
		double length = length();
		if (length < 1) {
			return to;
		}
		double t = Math.min(1, Math.max(0, (now - departed) * kind.speed() / length));
		return new BlockPos((int) Math.round(from.getX() + (to.getX() - from.getX()) * t), 0,
				(int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * t));
	}

	public boolean hasArrived(double now) {
		return state != State.WAITING && (now - departed) * kind.speed() >= length() - 1;
	}

	/** Days until they reach their destination. */
	public double daysLeft(double now) {
		return Math.max(0, length() / kind.speed() - (now - departed));
	}

	/** Sets off afresh from where they are now towards somewhere else. */
	public void redirect(BlockPos destination, double now) {
		from = position(now);
		to = destination.withY(0);
		departed = now;
		if (state == State.WAITING) {
			state = State.MARCHING;
		}
	}

	/** Holds this spot until the given day: an ambush, a camp. */
	public void waitHere(double now, long until) {
		from = position(now);
		to = from;
		state = State.WAITING;
		waitUntil = until;
	}

	/** Sets the band down at a spot to hold it until a given day (for testing, and for camps). */
	public void stationAt(BlockPos at, long until) {
		from = at.withY(0);
		to = from;
		state = State.WAITING;
		waitUntil = until;
	}

	public void turnBack(double now) {
		BlockPos here = position(now);
		state = State.RETURNING;
		from = here;
		to = homePos;
		departed = now;
		opponent = -1;
	}

	/** Pins the band where a fight in person left it. */
	public void stopAt(BlockPos at, double now) {
		BlockPos goal = to;
		State was = state;
		from = at.withY(0);
		departed = now;
		if (was == State.WAITING) {
			to = from;
		} else {
			to = goal;
		}
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putString("Faction", faction.id());
		nbt.putString("Kind", kind.name());
		nbt.putInt("Home", home);
		nbt.putLong("HomePos", homePos.asLong());
		nbt.putString("Enemy", enemy.id());
		nbt.putInt("Target", target);
		nbt.putLong("From", from.asLong());
		nbt.putLong("To", to.asLong());
		nbt.putDouble("Departed", departed);
		nbt.putString("State", state.name());
		nbt.putInt("Fighters", fighters);
		nbt.putLong("WaitUntil", waitUntil);
		nbt.putInt("Opponent", opponent);
		nbt.putLong("FoughtLive", foughtLive);
		nbt.putBoolean("Sabotaged", sabotaged);
		nbt.putInt("Beasts", beasts);
		return nbt;
	}

	public static Warband fromNbt(NbtCompound nbt) {
		FactionType faction = FactionType.byId(nbt.getString("Faction"));
		FactionType enemy = FactionType.byId(nbt.getString("Enemy"));
		Kind kind;
		State state;
		try {
			kind = Kind.valueOf(nbt.getString("Kind"));
			state = State.valueOf(nbt.getString("State"));
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (faction == null || enemy == null) {
			return null;
		}
		Warband band = new Warband(nbt.getInt("Id"), faction, kind, nbt.getInt("Home"), BlockPos.fromLong(nbt.getLong("HomePos")),
				enemy, nbt.getInt("Target"), BlockPos.fromLong(nbt.getLong("To")), nbt.getInt("Fighters"), nbt.getDouble("Departed"));
		band.from = BlockPos.fromLong(nbt.getLong("From"));
		band.state = state;
		band.waitUntil = nbt.getLong("WaitUntil");
		band.opponent = nbt.getInt("Opponent");
		band.foughtLive = nbt.getLong("FoughtLive");
		band.sabotaged = nbt.getBoolean("Sabotaged");
		band.beasts = nbt.getInt("Beasts");
		return band;
	}
}
