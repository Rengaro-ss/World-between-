package com.worldbetween.faction;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;

import com.worldbetween.faction.court.Noble;
import com.worldbetween.faction.journeys.Journey;
import com.worldbetween.faction.seekers.Marker;
import com.worldbetween.faction.seekers.Seeker;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The persistent state of one faction. Behaviour lives in {@link FactionBehavior};
 * this class only holds data.
 */
public class Faction {
	public static final int MIN_REPUTATION = -100;
	public static final int MAX_REPUTATION = 100;
	/** "Long ago" for day counters; far enough back for any cooldown, safe to subtract from. */
	public static final long NEVER = -1_000_000L;

	private final FactionType type;
	private boolean arrived;
	private boolean destroyed;
	private int era;
	private int soldiers;
	private long metalProduced;
	private long lastExpansionDay = NEVER;
	private int nextSettlementId;
	private final EnumMap<ResourceType, Long> resources = new EnumMap<>(ResourceType.class);
	private final List<Settlement> settlements = new ArrayList<>();
	private final EnumMap<FactionType, Integer> relations = new EnumMap<>(FactionType.class);
	private final Map<UUID, Integer> reputation = new HashMap<>();
	/** Day each named event last happened, used to keep the chronicle from repeating itself. */
	private final Map<String, Long> lastEventDays = new HashMap<>();
	/** Players declared criminals, mapped to the day it happened. */
	private final Map<UUID, Long> criminals = new HashMap<>();
	private final List<Noble> nobles = new ArrayList<>();
	private int kingId = -1;
	private int nextNobleId;
	/** Day the council of barons meets to elect a king, or NEVER if no election is pending. */
	private long councilDay = NEVER;
	private final List<Seeker> seekers = new ArrayList<>();
	private int nextSeekerId;
	/** Places seekers found good for a town, best first. */
	private final List<BlockPos> knownSites = new ArrayList<>();
	private final List<Marker> markers = new ArrayList<>();
	private int nextMarkerId;
	/** Where strangers (players) live, as found out by seekers. */
	private final Map<UUID, BlockPos> strangers = new HashMap<>();
	private final List<Journey> journeys = new ArrayList<>();
	private int nextJourneyId;
	/** The dead portal the founders came through. */
	private BlockPos portal;
	/** Named numbers for whatever a faction keeps track of beyond the common fields. */
	private final Map<String, Long> counters = new HashMap<>();
	private final List<com.worldbetween.faction.kament.Ancient> ancients = new ArrayList<>();
	private final List<com.worldbetween.faction.kament.Tunnel> tunnels = new ArrayList<>();
	private final List<com.worldbetween.faction.arki.Comet> comets = new ArrayList<>();

	public Faction(FactionType type) {
		this.type = type;
	}

	public FactionType type() {
		return type;
	}

	public boolean hasArrived() {
		return arrived;
	}

	public void setArrived(boolean arrived) {
		this.arrived = arrived;
	}

	/** A faction can only be destroyed by a legendary deed; until then it always survives. */
	public boolean isDestroyed() {
		return destroyed;
	}

	public void setDestroyed(boolean destroyed) {
		this.destroyed = destroyed;
	}

	public boolean isActive() {
		return arrived && !destroyed;
	}

	public int era() {
		return era;
	}

	public void setEra(int era) {
		this.era = era;
	}

	public int soldiers() {
		return soldiers;
	}

	public void setSoldiers(int soldiers) {
		this.soldiers = Math.max(0, soldiers);
	}

	public long metalProduced() {
		return metalProduced;
	}

	public void addMetalProduced(long amount) {
		metalProduced += amount;
	}

	public long lastExpansionDay() {
		return lastExpansionDay;
	}

	public void setLastExpansionDay(long day) {
		this.lastExpansionDay = day;
	}

	public int nextSettlementId() {
		return nextSettlementId++;
	}

	public long resource(ResourceType type) {
		return resources.getOrDefault(type, 0L);
	}

	public void addResource(ResourceType type, long amount) {
		resources.put(type, Math.max(0, resource(type) + amount));
	}

	/** Takes up to {@code amount} of a resource and returns how much was actually taken. */
	public long takeResource(ResourceType type, long amount) {
		long taken = Math.min(resource(type), amount);
		resources.put(type, resource(type) - taken);
		return taken;
	}

	public boolean canAfford(BuildingType building) {
		return resource(ResourceType.WOOD) >= building.woodCost() && resource(ResourceType.STONE) >= building.stoneCost()
				&& resource(ResourceType.BRICKS) >= building.brickCost();
	}

	public void pay(BuildingType building) {
		takeResource(ResourceType.WOOD, building.woodCost());
		takeResource(ResourceType.STONE, building.stoneCost());
		takeResource(ResourceType.BRICKS, building.brickCost());
	}

	public int totalBuildings(BuildingType type) {
		return settlements.stream().mapToInt(s -> s.buildingCount(type)).sum();
	}

	public List<Settlement> settlements() {
		return settlements;
	}

	public int totalPopulation() {
		return settlements.stream().mapToInt(Settlement::population).sum() + soldiers;
	}

	public int relation(FactionType other) {
		return relations.getOrDefault(other, 0);
	}

	public void setRelation(FactionType other, int value) {
		relations.put(other, MathHelper.clamp(value, MIN_REPUTATION, MAX_REPUTATION));
	}

	public int reputation(UUID player) {
		return reputation.getOrDefault(player, 0);
	}

	public void changeReputation(UUID player, int delta) {
		reputation.put(player, MathHelper.clamp(reputation(player) + delta, MIN_REPUTATION, MAX_REPUTATION));
	}

	public List<Noble> nobles() {
		return nobles;
	}

	public List<Noble> livingNobles() {
		return nobles.stream().filter(Noble::isAlive).toList();
	}

	public Noble king() {
		return nobles.stream().filter(n -> n.id() == kingId && n.isAlive()).findFirst().orElse(null);
	}

	public void setKing(Noble noble) {
		this.kingId = noble == null ? -1 : noble.id();
	}

	public int nextNobleId() {
		return nextNobleId++;
	}

	public long councilDay() {
		return councilDay;
	}

	public void setCouncilDay(long day) {
		this.councilDay = day;
	}

	public List<Seeker> seekers() {
		return seekers;
	}

	public int nextSeekerId() {
		return nextSeekerId++;
	}

	public List<BlockPos> knownSites() {
		return knownSites;
	}

	public List<Marker> markers() {
		return markers;
	}

	public Marker addMarker(Marker.Type type, BlockPos pos) {
		Marker marker = new Marker(nextMarkerId++, type, pos);
		markers.add(marker);
		return marker;
	}

	public Map<UUID, BlockPos> strangers() {
		return strangers;
	}

	public List<Journey> journeys() {
		return journeys;
	}

	public int nextJourneyId() {
		return nextJourneyId++;
	}

	public BlockPos portal() {
		return portal;
	}

	public void setPortal(BlockPos portal) {
		this.portal = portal;
	}

	public long counter(String key) {
		return counters.getOrDefault(key, 0L);
	}

	public void setCounter(String key, long value) {
		counters.put(key, value);
	}

	public void addCounter(String key, long delta) {
		setCounter(key, counter(key) + delta);
	}

	public List<com.worldbetween.faction.kament.Ancient> ancients() {
		return ancients;
	}

	public List<com.worldbetween.faction.kament.Tunnel> tunnels() {
		return tunnels;
	}

	/** Comets and capsules decided on but not yet come down. */
	public List<com.worldbetween.faction.arki.Comet> comets() {
		return comets;
	}

	public boolean isCriminal(UUID player) {
		return criminals.containsKey(player);
	}

	public void declareCriminal(UUID player, long day) {
		criminals.putIfAbsent(player, day);
	}

	public void pardon(UUID player) {
		criminals.remove(player);
	}

	/** Returns true and records the day if the event has not happened within {@code cooldown} days. */
	public boolean tryEvent(String event, long day, long cooldown) {
		Long last = lastEventDays.get(event);
		if (last != null && day - last < cooldown) {
			return false;
		}
		lastEventDays.put(event, day);
		return true;
	}

	/** The day an event last happened, or null. */
	public Long lastEvent(String event) {
		return lastEventDays.get(event);
	}

	public boolean hasEventHappened(String event) {
		return lastEventDays.containsKey(event);
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean("Arrived", arrived);
		nbt.putBoolean("Destroyed", destroyed);
		nbt.putInt("Era", era);
		nbt.putInt("Soldiers", soldiers);
		nbt.putLong("MetalProduced", metalProduced);
		nbt.putLong("LastExpansion", lastExpansionDay);
		nbt.putInt("NextSettlementId", nextSettlementId);

		NbtCompound resourcesNbt = new NbtCompound();
		resources.forEach((type, amount) -> resourcesNbt.putLong(type.id(), amount));
		nbt.put("Resources", resourcesNbt);

		NbtList settlementsNbt = new NbtList();
		settlements.forEach(settlement -> settlementsNbt.add(settlement.toNbt()));
		nbt.put("Settlements", settlementsNbt);

		NbtCompound relationsNbt = new NbtCompound();
		relations.forEach((other, value) -> relationsNbt.putInt(other.id(), value));
		nbt.put("Relations", relationsNbt);

		NbtCompound reputationNbt = new NbtCompound();
		reputation.forEach((player, value) -> reputationNbt.putInt(player.toString(), value));
		nbt.put("Reputation", reputationNbt);

		NbtCompound eventsNbt = new NbtCompound();
		lastEventDays.forEach(eventsNbt::putLong);
		nbt.put("Events", eventsNbt);

		NbtCompound criminalsNbt = new NbtCompound();
		criminals.forEach((player, day) -> criminalsNbt.putLong(player.toString(), day));
		nbt.put("Criminals", criminalsNbt);

		NbtList noblesNbt = new NbtList();
		nobles.forEach(noble -> noblesNbt.add(noble.toNbt()));
		nbt.put("Nobles", noblesNbt);
		nbt.putInt("King", kingId);
		nbt.putInt("NextNobleId", nextNobleId);
		nbt.putLong("Council", councilDay);

		NbtList seekersNbt = new NbtList();
		seekers.forEach(seeker -> seekersNbt.add(seeker.toNbt()));
		nbt.put("Seekers", seekersNbt);
		nbt.putInt("NextSeekerId", nextSeekerId);
		nbt.putLongArray("KnownSites", knownSites.stream().mapToLong(BlockPos::asLong).toArray());
		NbtList markersNbt = new NbtList();
		markers.forEach(marker -> markersNbt.add(marker.toNbt()));
		nbt.put("Markers", markersNbt);
		nbt.putInt("NextMarkerId", nextMarkerId);
		NbtCompound strangersNbt = new NbtCompound();
		strangers.forEach((player, pos) -> strangersNbt.putLong(player.toString(), pos.asLong()));
		nbt.put("Strangers", strangersNbt);
		NbtList journeysNbt = new NbtList();
		journeys.forEach(journey -> journeysNbt.add(journey.toNbt()));
		nbt.put("Journeys", journeysNbt);
		nbt.putInt("NextJourneyId", nextJourneyId);
		if (portal != null) {
			nbt.putLong("Portal", portal.asLong());
		}
		NbtCompound countersNbt = new NbtCompound();
		counters.forEach(countersNbt::putLong);
		nbt.put("Counters", countersNbt);
		NbtList ancientsNbt = new NbtList();
		ancients.forEach(ancient -> ancientsNbt.add(ancient.toNbt()));
		nbt.put("Ancients", ancientsNbt);
		NbtList tunnelsNbt = new NbtList();
		tunnels.forEach(tunnel -> tunnelsNbt.add(tunnel.toNbt()));
		nbt.put("Tunnels", tunnelsNbt);
		NbtList cometsNbt = new NbtList();
		comets.forEach(comet -> cometsNbt.add(comet.toNbt()));
		nbt.put("Comets", cometsNbt);
		return nbt;
	}

	public static Faction fromNbt(FactionType type, NbtCompound nbt) {
		Faction faction = new Faction(type);
		faction.arrived = nbt.getBoolean("Arrived");
		faction.destroyed = nbt.getBoolean("Destroyed");
		faction.era = nbt.getInt("Era");
		faction.soldiers = nbt.getInt("Soldiers");
		faction.metalProduced = nbt.getLong("MetalProduced");
		faction.lastExpansionDay = nbt.contains("LastExpansion") ? nbt.getLong("LastExpansion") : NEVER;
		faction.nextSettlementId = nbt.getInt("NextSettlementId");

		NbtCompound resourcesNbt = nbt.getCompound("Resources");
		for (String key : resourcesNbt.getKeys()) {
			ResourceType resource = ResourceType.byId(key);
			if (resource != null) {
				faction.resources.put(resource, resourcesNbt.getLong(key));
			}
		}

		NbtList settlementsNbt = nbt.getList("Settlements", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < settlementsNbt.size(); i++) {
			faction.settlements.add(Settlement.fromNbt(settlementsNbt.getCompound(i)));
		}

		NbtCompound relationsNbt = nbt.getCompound("Relations");
		for (String key : relationsNbt.getKeys()) {
			FactionType other = FactionType.byId(key);
			if (other != null) {
				faction.relations.put(other, relationsNbt.getInt(key));
			}
		}

		NbtCompound reputationNbt = nbt.getCompound("Reputation");
		for (String key : reputationNbt.getKeys()) {
			try {
				faction.reputation.put(UUID.fromString(key), reputationNbt.getInt(key));
			} catch (IllegalArgumentException ignored) {
				// Skip malformed entries rather than failing the whole load.
			}
		}

		NbtCompound eventsNbt = nbt.getCompound("Events");
		for (String key : eventsNbt.getKeys()) {
			faction.lastEventDays.put(key, eventsNbt.getLong(key));
		}

		NbtCompound criminalsNbt = nbt.getCompound("Criminals");
		for (String key : criminalsNbt.getKeys()) {
			try {
				faction.criminals.put(UUID.fromString(key), criminalsNbt.getLong(key));
			} catch (IllegalArgumentException ignored) {
				// Skip malformed entries rather than failing the whole load.
			}
		}

		NbtList noblesNbt = nbt.getList("Nobles", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < noblesNbt.size(); i++) {
			Noble noble = Noble.fromNbt(noblesNbt.getCompound(i));
			if (noble != null) {
				faction.nobles.add(noble);
			}
		}
		faction.kingId = nbt.contains("King") ? nbt.getInt("King") : -1;
		faction.nextNobleId = nbt.getInt("NextNobleId");
		faction.councilDay = nbt.contains("Council") ? nbt.getLong("Council") : NEVER;

		NbtList seekersNbt = nbt.getList("Seekers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < seekersNbt.size(); i++) {
			faction.seekers.add(Seeker.fromNbt(seekersNbt.getCompound(i)));
		}
		faction.nextSeekerId = nbt.getInt("NextSeekerId");
		for (long site : nbt.getLongArray("KnownSites")) {
			faction.knownSites.add(BlockPos.fromLong(site));
		}
		NbtList markersNbt = nbt.getList("Markers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < markersNbt.size(); i++) {
			Marker marker = Marker.fromNbt(markersNbt.getCompound(i));
			if (marker != null) {
				faction.markers.add(marker);
			}
		}
		faction.nextMarkerId = nbt.getInt("NextMarkerId");
		NbtCompound strangersNbt = nbt.getCompound("Strangers");
		for (String key : strangersNbt.getKeys()) {
			try {
				faction.strangers.put(UUID.fromString(key), BlockPos.fromLong(strangersNbt.getLong(key)));
			} catch (IllegalArgumentException ignored) {
				// Skip malformed entries rather than failing the whole load.
			}
		}
		NbtList journeysNbt = nbt.getList("Journeys", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < journeysNbt.size(); i++) {
			Journey journey = Journey.fromNbt(journeysNbt.getCompound(i));
			if (journey != null) {
				faction.journeys.add(journey);
			}
		}
		faction.nextJourneyId = nbt.getInt("NextJourneyId");
		if (nbt.contains("Portal")) {
			faction.portal = BlockPos.fromLong(nbt.getLong("Portal"));
		}
		NbtCompound countersNbt = nbt.getCompound("Counters");
		for (String key : countersNbt.getKeys()) {
			faction.counters.put(key, countersNbt.getLong(key));
		}
		NbtList ancientsNbt = nbt.getList("Ancients", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < ancientsNbt.size(); i++) {
			faction.ancients.add(com.worldbetween.faction.kament.Ancient.fromNbt(ancientsNbt.getCompound(i)));
		}
		NbtList tunnelsNbt = nbt.getList("Tunnels", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < tunnelsNbt.size(); i++) {
			com.worldbetween.faction.kament.Tunnel tunnel = com.worldbetween.faction.kament.Tunnel.fromNbt(tunnelsNbt.getCompound(i));
			if (tunnel != null) {
				faction.tunnels.add(tunnel);
			}
		}
		NbtList cometsNbt = nbt.getList("Comets", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < cometsNbt.size(); i++) {
			com.worldbetween.faction.arki.Comet comet = com.worldbetween.faction.arki.Comet.fromNbt(cometsNbt.getCompound(i));
			if (comet != null) {
				faction.comets.add(comet);
			}
		}
		return faction;
	}
}
