package com.worldbetween.faction.seekers;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;

/**
 * One of the Seekers: the kingdom's elite scouts. A seeker is always on a journey in the simulation,
 * from one point to another at a steady pace; a player who comes near meets them in person.
 */
public class Seeker {
	/** Blocks a seeker covers in a day. */
	public static final int SPEED = 400;

	public enum Mission {
		/** Find a good place for a new town. */
		SITE,
		/** Find the richest ground for a town's mine. */
		MINE,
		/** Find out where a stranger lives. */
		SCOUT,
		/** On the way home. */
		RETURN
	}

	private final int id;
	private final String name;
	private final int home;
	private boolean alive = true;
	private Mission mission;
	private BlockPos origin;
	private BlockPos target;
	/** Time the current leg began, in days with the fraction of the day. */
	private double departed;
	/** For MINE: the settlement it is for. For SCOUT: the stranger it is after. */
	private int forSettlement = -1;
	private UUID forPlayer;

	public Seeker(int id, String name, int home, BlockPos at) {
		this.id = id;
		this.name = name;
		this.home = home;
		this.origin = at;
		this.target = at;
	}

	public int id() {
		return id;
	}

	public String name() {
		return name;
	}

	public int home() {
		return home;
	}

	public boolean isAlive() {
		return alive;
	}

	public void die() {
		alive = false;
	}

	public Mission mission() {
		return mission;
	}

	public boolean isIdle() {
		return mission == null;
	}

	public BlockPos target() {
		return target;
	}

	public int forSettlement() {
		return forSettlement;
	}

	public UUID forPlayer() {
		return forPlayer;
	}

	public void send(Mission mission, BlockPos from, BlockPos to, double now, int settlement, UUID player) {
		this.mission = mission;
		this.origin = from;
		this.target = to;
		this.departed = now;
		this.forSettlement = settlement;
		this.forPlayer = player;
	}

	public void finish() {
		this.mission = null;
	}

	/** Where the seeker is at time {@code now}. */
	public BlockPos position(double now) {
		double distance = Math.sqrt(origin.getSquaredDistance(target));
		double t = distance < 1 ? 1 : MathHelper.clamp((now - departed) * SPEED / distance, 0, 1);
		return new BlockPos((int) MathHelper.lerp(t, origin.getX(), target.getX()), 0, (int) MathHelper.lerp(t, origin.getZ(), target.getZ()));
	}

	public boolean hasArrived(double now) {
		return position(now).getSquaredDistance(target.withY(0)) < 4;
	}

	/** Picks the journey up from where the seeker actually is, after walking it in person. */
	public void resumeFrom(BlockPos at, double now) {
		this.origin = at;
		this.departed = now;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putString("Name", name);
		nbt.putInt("Home", home);
		nbt.putBoolean("Alive", alive);
		if (mission != null) {
			nbt.putString("Mission", mission.name());
		}
		nbt.putLong("Origin", origin.asLong());
		nbt.putLong("Target", target.asLong());
		nbt.putDouble("Departed", departed);
		nbt.putInt("For", forSettlement);
		if (forPlayer != null) {
			nbt.putUuid("Player", forPlayer);
		}
		return nbt;
	}

	public static Seeker fromNbt(NbtCompound nbt) {
		Seeker seeker = new Seeker(nbt.getInt("Id"), nbt.getString("Name"), nbt.getInt("Home"), BlockPos.fromLong(nbt.getLong("Origin")));
		seeker.alive = nbt.getBoolean("Alive");
		if (nbt.contains("Mission")) {
			try {
				seeker.mission = Mission.valueOf(nbt.getString("Mission"));
			} catch (IllegalArgumentException ignored) {
				seeker.mission = null;
			}
		}
		seeker.target = BlockPos.fromLong(nbt.getLong("Target"));
		seeker.departed = nbt.getDouble("Departed");
		seeker.forSettlement = nbt.getInt("For");
		if (nbt.containsUuid("Player")) {
			seeker.forPlayer = nbt.getUuid("Player");
		}
		return seeker;
	}
}
