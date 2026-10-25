package com.worldbetween.war;

import com.worldbetween.faction.FactionType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * The war between two peoples. It never quite ends: it only runs hotter or colder. Its heat (tension) rises
 * with near borders, with every clash and with one side growing much stronger than the other, and cools
 * slowly when nothing happens. Four degrees:
 * <ol>
 *     <li>Wariness: nobody seeks a fight, but parties that meet on the road fight.</li>
 *     <li>Raids: ambushes and sabotage near the enemy's towns.</li>
 *     <li>Campaigns: armies march on the enemy's small towns; the defenders march out to meet them.</li>
 *     <li>Total war: armies march on any town, to wipe the enemy out.</li>
 * </ol>
 */
public class War {
	public static final int MAX_LEVEL = 4;
	/** Tension at which each degree begins (index = level - 1), and how far below it it must fall to cool again. */
	private static final double[] RISE = {0, 25, 50, 75};
	private static final double COOL = 8;
	/** At least so many days between two changes of degree. */
	private static final int SETTLE_DAYS = 5;

	private final FactionType a;
	private final FactionType b;
	private double tension;
	private int level;
	private long changed = -1000;
	private int battles;
	private int fallenA;
	private int fallenB;
	private int townsSacked;

	public War(FactionType a, FactionType b) {
		this.a = a.ordinal() <= b.ordinal() ? a : b;
		this.b = a.ordinal() <= b.ordinal() ? b : a;
	}

	public static String key(FactionType x, FactionType y) {
		return x.ordinal() <= y.ordinal() ? x.id() + "/" + y.id() : y.id() + "/" + x.id();
	}

	public String key() {
		return key(a, b);
	}

	public FactionType a() {
		return a;
	}

	public FactionType b() {
		return b;
	}

	public FactionType other(FactionType side) {
		return side == a ? b : a;
	}

	public boolean involves(FactionType side) {
		return side == a || side == b;
	}

	public double tension() {
		return tension;
	}

	public void addTension(double delta) {
		tension = Math.max(0, Math.min(100, tension + delta));
	}

	public void setTension(double tension) {
		this.tension = Math.max(0, Math.min(100, tension));
	}

	/** 0 while the two have not met; then 1 to 4. */
	public int level() {
		return level;
	}

	public void setLevel(int level, long day) {
		this.level = level;
		this.changed = day;
	}

	/** The degree the tension calls for now, or the current one while it settles. */
	public int wantedLevel(long day) {
		if (level == 0 || day - changed < SETTLE_DAYS) {
			return level;
		}
		if (level < MAX_LEVEL && tension >= RISE[level]) {
			return level + 1;
		}
		if (level > 1 && tension < RISE[level - 1] - COOL) {
			return level - 1;
		}
		return level;
	}

	public int battles() {
		return battles;
	}

	public void recordBattle(FactionType side, int fallen, FactionType otherSide, int otherFallen) {
		battles++;
		addFallen(side, fallen);
		addFallen(otherSide, otherFallen);
	}

	private void addFallen(FactionType side, int fallen) {
		if (side == a) {
			fallenA += fallen;
		} else if (side == b) {
			fallenB += fallen;
		}
	}

	public int fallen(FactionType side) {
		return side == a ? fallenA : side == b ? fallenB : 0;
	}

	public int townsSacked() {
		return townsSacked;
	}

	public void addTownSacked() {
		townsSacked++;
	}

	public static Text levelName(int level) {
		return Text.translatable("war.worldbetween.level." + level);
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("A", a.id());
		nbt.putString("B", b.id());
		nbt.putDouble("Tension", tension);
		nbt.putInt("Level", level);
		nbt.putLong("Changed", changed);
		nbt.putInt("Battles", battles);
		nbt.putInt("FallenA", fallenA);
		nbt.putInt("FallenB", fallenB);
		nbt.putInt("Sacked", townsSacked);
		return nbt;
	}

	public static War fromNbt(NbtCompound nbt) {
		FactionType a = FactionType.byId(nbt.getString("A"));
		FactionType b = FactionType.byId(nbt.getString("B"));
		if (a == null || b == null) {
			return null;
		}
		War war = new War(a, b);
		war.tension = nbt.getDouble("Tension");
		war.level = nbt.getInt("Level");
		war.changed = nbt.getLong("Changed");
		war.battles = nbt.getInt("Battles");
		war.fallenA = nbt.getInt("FallenA");
		war.fallenB = nbt.getInt("FallenB");
		war.townsSacked = nbt.getInt("Sacked");
		return war;
	}
}
