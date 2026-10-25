package com.worldbetween.faction.court;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A baron of the realm, possibly its king.
 */
public class Noble {
	/** Days in a year of a noble's life. */
	public static final int DAYS_PER_YEAR = 30;

	private final int id;
	private final String name;
	private final Trait first;
	private final Trait second;
	private final Ambition ambition;
	private final long birthDay;
	private int seat;
	private Sphere sphere;
	private boolean alive = true;
	private long purse;
	/** Grudges and favours on top of what traits alone would make of another noble. */
	private final Map<Integer, Integer> regard = new HashMap<>();

	public Noble(int id, String name, Trait first, Trait second, Ambition ambition, long birthDay, int seat, Sphere sphere) {
		this.id = id;
		this.name = name;
		this.first = first;
		this.second = second;
		this.ambition = ambition;
		this.birthDay = birthDay;
		this.seat = seat;
		this.sphere = sphere;
	}

	public int id() {
		return id;
	}

	public String name() {
		return name;
	}

	public List<Trait> traits() {
		return List.of(first, second);
	}

	public boolean has(Trait trait) {
		return first == trait || second == trait;
	}

	public Ambition ambition() {
		return ambition;
	}

	public int age(long day) {
		return (int) ((day - birthDay) / DAYS_PER_YEAR);
	}

	/** The settlement the noble holds. */
	public int seat() {
		return seat;
	}

	public void setSeat(int seat) {
		this.seat = seat;
	}

	public Sphere sphere() {
		return sphere;
	}

	public void setSphere(Sphere sphere) {
		this.sphere = sphere;
	}

	public boolean isAlive() {
		return alive;
	}

	public void die() {
		alive = false;
	}

	/** The noble's own coins, apart from the treasury. */
	public long purse() {
		return purse;
	}

	public void addToPurse(long coins) {
		purse = Math.max(0, purse + coins);
	}

	public int regard(int other) {
		return regard.getOrDefault(other, 0);
	}

	public void changeRegard(int other, int delta) {
		regard.put(other, Math.max(-50, Math.min(50, regard(other) + delta)));
	}

	/** How strongly the noble's oversight works: hard workers do twice what idlers do. */
	public float diligence() {
		float d = 1f;
		if (has(Trait.INDUSTRIOUS)) {
			d *= 1.5f;
		}
		if (has(Trait.LAZY)) {
			d *= 0.5f;
		}
		return d;
	}

	/** The epithet comes from the noble's most striking trait: the vice if there is one. */
	public Trait signature() {
		if (!second.isVirtue() && first.isVirtue()) {
			return second;
		}
		return first;
	}

	/** "Kar-Esh the Greedy". */
	public MutableText displayName() {
		return Text.translatable("noble.worldbetween.name", name, Text.translatable(signature().epithetKey()));
	}

	/** Chronicle argument form of the name: the epithet is stored as a key so it reads in any language. */
	public String chronicleName() {
		return name + " @" + signature().epithetKey();
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putString("Name", name);
		nbt.putString("First", first.id());
		nbt.putString("Second", second.id());
		nbt.putString("Ambition", ambition.id());
		nbt.putLong("Born", birthDay);
		nbt.putInt("Seat", seat);
		nbt.putString("Sphere", sphere.id());
		nbt.putBoolean("Alive", alive);
		nbt.putLong("Purse", purse);
		NbtCompound regardNbt = new NbtCompound();
		regard.forEach((other, value) -> regardNbt.putInt(Integer.toString(other), value));
		nbt.put("Regard", regardNbt);
		return nbt;
	}

	public static Noble fromNbt(NbtCompound nbt) {
		Trait first = Trait.byId(nbt.getString("First"));
		Trait second = Trait.byId(nbt.getString("Second"));
		Ambition ambition = Ambition.byId(nbt.getString("Ambition"));
		Sphere sphere = Sphere.byId(nbt.getString("Sphere"));
		if (first == null || second == null || ambition == null || sphere == null) {
			return null;
		}
		Noble noble = new Noble(nbt.getInt("Id"), nbt.getString("Name"), first, second, ambition,
				nbt.getLong("Born"), nbt.getInt("Seat"), sphere);
		noble.alive = nbt.getBoolean("Alive");
		noble.purse = nbt.getLong("Purse");
		NbtCompound regardNbt = nbt.getCompound("Regard");
		for (String key : regardNbt.getKeys()) {
			try {
				noble.regard.put(Integer.parseInt(key), regardNbt.getInt(key));
			} catch (NumberFormatException ignored) {
				// Skip malformed entries rather than failing the whole load.
			}
		}
		return noble;
	}
}
