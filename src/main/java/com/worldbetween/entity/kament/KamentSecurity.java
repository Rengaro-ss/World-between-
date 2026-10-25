package com.worldbetween.entity.kament;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.kament.Ancient;
import com.worldbetween.faction.kament.KamentTrait;
import com.worldbetween.faction.kament.Kaments;
import com.worldbetween.faction.kament.Tunnel;
import com.worldbetween.hold.HoldFeatures;
import com.worldbetween.hold.HoldLayout;
import com.worldbetween.mine.MineBlocks;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Ground;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The law of the stone. On Kament land a stranger is let be, so long as no metal is on them or in their
 * hands: metal is the blood of the mountain. A stranger seen with it is told to give up all the metal they
 * carry, every piece; who does so goes in peace, who leaves goes in peace, who does neither is an enemy.
 * Breaking ore on their land bleeds the mountain, and the Kaments do not forget.
 */
public final class KamentSecurity {
	private static final int LEAVE_TICKS = 100;
	private static final int DEMAND_SECONDS = 60;
	private static final double SIGHT = 24;
	private static final Set<Item> METAL = Set.of(Items.SHIELD, Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.MILK_BUCKET,
			Items.POWDER_SNOW_BUCKET, Items.SHEARS, Items.COMPASS, Items.RECOVERY_COMPASS, Items.CLOCK, Items.FLINT_AND_STEEL,
			Items.SPYGLASS, Items.BRUSH, Items.CHAIN, Items.LANTERN, Items.SOUL_LANTERN, Items.ANVIL, Items.CHIPPED_ANVIL,
			Items.DAMAGED_ANVIL, Items.HOPPER, Items.CAULDRON, Items.LIGHTNING_ROD, Items.MINECART, Items.RAIL, Items.POWERED_RAIL,
			Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL, Items.MACE, Items.HEAVY_CORE, Items.CROSSBOW, Items.TRIPWIRE_HOOK);

	private static final class Demand {
		final Settlement settlement;
		final long start;
		final long deadline;
		long lastInside;
		final ServerBossBar bar;

		Demand(Settlement settlement, long start, long deadline, ServerBossBar bar) {
			this.settlement = settlement;
			this.start = start;
			this.deadline = deadline;
			this.lastInside = start;
			this.bar = bar;
		}
	}

	private static final Map<UUID, Demand> DEMANDS = new HashMap<>();
	private static final Map<UUID, Long> BLEED_MESSAGES = new HashMap<>();

	private KamentSecurity() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % 20 != 0) {
			return;
		}
		WorldBetweenState state = WorldBetweenState.get(server);
		Faction faction = state.faction(FactionType.KAMENT);
		if (!faction.isActive()) {
			return;
		}
		ServerWorld world = server.getOverworld();
		long now = server.getTicks();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			Settlement land = territoryAt(world, faction, player.getBlockPos());
			if (land != null) {
				player.addStatusEffect(new StatusEffectInstance(KamentEffects.KAMENT_LAND, 60, 0, true, false, true));
			}
			if (player.isCreative()) {
				continue;
			}
			Demand demand = DEMANDS.get(player.getUuid());
			if (demand != null) {
				update(player, faction, demand, land, state, now);
			} else if (land != null && !faction.isCriminal(player.getUuid()) && showsMetal(player)) {
				KamentEntity witness = witness(world, player);
				if (witness != null) {
					demand(player, faction, land, witness, now);
				}
			}
		}
	}

	/** The Kament settlement whose land a position is on: a hold and its mountain, a deep city, a tunnel. */
	public static Settlement territoryAt(ServerWorld world, Faction faction, BlockPos pos) {
		for (Settlement settlement : faction.settlements()) {
			HoldLayout layout = HoldLayout.of(world, settlement);
			BlockPos origin = layout.origin();
			double dx = pos.getX() - origin.getX();
			double dz = pos.getZ() - origin.getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			if (layout.isDeep()) {
				if (horizontal <= HoldLayout.cavernRadius(3) + 12 && Math.abs(pos.getY() - origin.getY()) <= 12) {
					return settlement;
				}
				continue;
			}
			// The mountain itself, from the hall's floor up to the peak, and the ground before the gate.
			if (horizontal <= 40 + 6 * settlement.tier() && pos.getY() >= origin.getY() - 6
					&& pos.getY() <= Ground.floor(world, pos.getX(), pos.getZ()) + 3) {
				return settlement;
			}
			if (layout.local().world(0, 1, -layout.exit()).getSquaredDistance(pos) < 14 * 14) {
				return settlement;
			}
			if (HoldFeatures.child(faction, settlement) != null) {
				BlockPos shaft = layout.shaftTop();
				if (Math.abs(pos.getX() - shaft.getX()) <= 4 && Math.abs(pos.getZ() - shaft.getZ()) <= 4 && pos.getY() <= origin.getY() + 4) {
					return settlement;
				}
			}
		}
		for (Tunnel tunnel : faction.tunnels()) {
			double walked = 0;
			List<BlockPos> path = tunnel.path();
			for (int i = 1; i < path.size() && walked <= tunnel.dug(); i++) {
				if (path.get(i).getSquaredDistance(pos) < 7 * 7) {
					return faction.settlements().stream().filter(s -> s.id() == tunnel.from()).findFirst().orElse(null);
				}
				walked += Math.sqrt(path.get(i - 1).getSquaredDistance(path.get(i)));
			}
		}
		return null;
	}

	/** Whether a thing is of metal: the blood of the mountain, taken out of it. */
	public static boolean isMetal(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		if (METAL.contains(item)) {
			return true;
		}
		String path = Registries.ITEM.getId(item).getPath();
		return path.contains("iron") || path.contains("gold") || path.contains("copper") || path.contains("netherite")
				|| path.contains("chainmail") || path.endsWith("_minecart");
	}

	/** Metal worn, or metal (or a shield) in the hands: what the Kaments will not abide on their land. */
	public static boolean showsMetal(ServerPlayerEntity player) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot != EquipmentSlot.BODY && isMetal(player.getEquippedStack(slot))) {
				return true;
			}
		}
		return false;
	}

	/** A Kament close by that can see the player, if there is one. */
	private static KamentEntity witness(ServerWorld world, ServerPlayerEntity player) {
		for (KamentEntity kament : KamentPresence.near(world, player, SIGHT)) {
			if (kament.canSee(player)) {
				return kament;
			}
		}
		return null;
	}

	private static int grace(Faction faction, Settlement settlement) {
		int seconds = DEMAND_SECONDS;
		Ancient ancient = Kaments.ancientOf(faction, settlement);
		if (ancient != null && ancient.has(KamentTrait.PATIENT)) {
			seconds += 30;
		}
		if (ancient != null && ancient.has(KamentTrait.WRATHFUL)) {
			seconds -= 20;
		}
		if (Kaments.focus(faction) == com.worldbetween.faction.kament.Focus.WARD) {
			seconds -= 10;
		}
		return Math.max(20, seconds);
	}

	private static void demand(ServerPlayerEntity player, Faction faction, Settlement land, KamentEntity witness, long now) {
		int seconds = grace(faction, land);
		ServerBossBar bar = new ServerBossBar(Text.translatable("bossbar.worldbetween.kament.metal"), BossBar.Color.WHITE, BossBar.Style.NOTCHED_10);
		bar.addPlayer(player);
		DEMANDS.put(player.getUuid(), new Demand(land, now, now + seconds * 20L, bar));
		witness.getLookControl().lookAt(player);
		witness.setQuarry(player.getUuid());
		witness.say(player, "speech.worldbetween.kament.demand");
		player.sendMessage(Text.translatable("message.worldbetween.kament.demand", seconds).formatted(Formatting.GRAY), false);
		player.getServerWorld().playSound(null, witness.getBlockPos(), SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 1.5f, 0.4f);
	}

	private static void update(ServerPlayerEntity player, Faction faction, Demand demand, Settlement land, WorldBetweenState state, long now) {
		if (land != null) {
			demand.lastInside = now;
		}
		if (now - demand.lastInside > LEAVE_TICKS) {
			player.sendMessage(Text.translatable("message.worldbetween.kament.left").formatted(Formatting.GRAY), false);
			clear(player);
			return;
		}
		long remaining = demand.deadline - now;
		if (remaining > 0) {
			demand.bar.setPercent(Math.max(0f, remaining / (float) (demand.deadline - demand.start)));
			return;
		}
		clear(player);
		outlaw(player, faction, demand.settlement, "metal", 20, state);
	}

	/** The stranger would not give it up: from now on the Kaments' guards treat them as an enemy. */
	private static void outlaw(ServerPlayerEntity player, Faction faction, Settlement settlement, String reason, int penalty, WorldBetweenState state) {
		faction.declareCriminal(player.getUuid(), state.day());
		changeReputation(faction, settlement, player.getUuid(), -penalty);
		state.markDirty();
		player.sendMessage(Text.translatable("message.worldbetween.kament.enemy").formatted(Formatting.RED), false);
		new SimulationContext(player.getServer(), state).chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.outlaw_" + reason,
				player.getName().getString(), settlement.name());
		for (KamentEntity kament : KamentPresence.near(player.getServerWorld(), player, 32)) {
			kament.setQuarry(null);
			if (kament.role().fights()) {
				kament.setTarget(player);
			}
		}
	}

	/** A loss of goodwill, weighed by the Ancient: a mindful one makes it half as heavy again. */
	private static void changeReputation(Faction faction, Settlement settlement, UUID player, int delta) {
		Ancient ancient = settlement == null ? null : Kaments.ancientOf(faction, settlement);
		if (delta < 0 && ancient != null && ancient.has(KamentTrait.MINDFUL)) {
			delta = Math.round(delta * 1.5f);
		}
		if (delta > 0 && ancient != null && ancient.has(KamentTrait.COLD)) {
			delta = Math.max(0, delta - 1);
		}
		if (delta > 0 && ancient != null && ancient.has(KamentTrait.CURIOUS)) {
			delta += 1;
		}
		faction.changeReputation(player, delta);
	}

	/**
	 * A player hands a Kament all the metal they carry. Done when asked, the demand is met; done unasked,
	 * it earns a little goodwill. Returns false if there was nothing to give.
	 */
	public static boolean surrender(KamentEntity kament, ServerPlayerEntity player) {
		WorldBetweenState state = WorldBetweenState.get(player.getServer());
		Faction faction = state.faction(FactionType.KAMENT);
		// Metal goes to them when they have asked for it, or when a stranger, crouching, offers it of their own will.
		if (faction.isCriminal(player.getUuid()) || !DEMANDS.containsKey(player.getUuid()) && !player.isSneaking()) {
			return false;
		}
		PlayerInventory inventory = player.getInventory();
		int pieces = 0;
		long weight = 0;
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (isMetal(stack)) {
				pieces += stack.getCount();
				weight += (long) stack.getCount() * (stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ToolItem ? 4 : 1);
				inventory.setStack(i, ItemStack.EMPTY);
			}
		}
		if (pieces == 0) {
			return false;
		}
		inventory.markDirty();
		// The healers will give it back to the mountain.
		faction.addResource(ResourceType.ORE, weight);
		Settlement land = territoryAt(player.getServerWorld(), faction, player.getBlockPos());
		boolean asked = DEMANDS.containsKey(player.getUuid());
		clear(player);
		if (faction.tryEvent("gift_" + player.getUuid(), state.day(), 1)) {
			changeReputation(faction, land, player.getUuid(), asked ? 1 : 3);
		}
		state.markDirty();
		kament.setQuarry(null);
		kament.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		kament.say(player, asked ? "speech.worldbetween.kament.surrendered" : "speech.worldbetween.kament.gift");
		player.sendMessage(Text.translatable("message.worldbetween.kament.taken", pieces).formatted(Formatting.GRAY), true);
		player.getServerWorld().playSound(null, kament.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.NEUTRAL, 1.5f, 0.6f);
		return true;
	}

	/** Breaking ore on their land bleeds the mountain. */
	public static void onBlockBroken(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
		if (!MineBlocks.isOre(state) || player.isCreative()) {
			return;
		}
		WorldBetweenState worldState = WorldBetweenState.get(world.getServer());
		Faction faction = worldState.faction(FactionType.KAMENT);
		Settlement land = faction.isActive() ? territoryAt(world, faction, pos) : null;
		if (land == null) {
			return;
		}
		boolean seen = witness(world, player) != null;
		changeReputation(faction, land, player.getUuid(), seen ? -10 : -4);
		worldState.markDirty();
		long now = world.getServer().getTicks();
		Long last = BLEED_MESSAGES.get(player.getUuid());
		if (last == null || now - last > 600 || seen) {
			BLEED_MESSAGES.put(player.getUuid(), now);
			player.sendMessage(Text.translatable(seen ? "message.worldbetween.kament.bleed_seen" : "message.worldbetween.kament.bleed")
					.formatted(seen ? Formatting.RED : Formatting.GRAY), false);
		}
		if (seen && faction.reputation(player.getUuid()) <= -40 && !faction.isCriminal(player.getUuid())) {
			clear(player);
			outlaw(player, faction, land, "ore", 0, worldState);
		}
	}

	public static void kamentSlain(MinecraftServer server, Faction faction, Settlement settlement, ServerPlayerEntity killer) {
		WorldBetweenState state = WorldBetweenState.get(server);
		settlement.setPopulation(settlement.population() - 1);
		if (killer != null) {
			changeReputation(faction, settlement, killer.getUuid(), -15);
			faction.declareCriminal(killer.getUuid(), state.day());
			new SimulationContext(server, state).chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.slain",
					settlement.name(), killer.getName().getString());
		}
		state.markDirty();
	}

	public static void ancientSlain(MinecraftServer server, Faction faction, Ancient ancient, Settlement settlement, ServerPlayerEntity killer) {
		WorldBetweenState state = WorldBetweenState.get(server);
		ancient.die();
		String name = killer != null ? killer.getName().getString() : "?";
		new SimulationContext(server, state).chronicle(FactionType.KAMENT,
				killer != null ? "chronicle.worldbetween.kament.ancient_slain_by" : "chronicle.worldbetween.kament.ancient_slain",
				ancient.name(), settlement.name(), name);
		if (killer != null) {
			faction.changeReputation(killer.getUuid(), -60);
			faction.declareCriminal(killer.getUuid(), state.day());
		}
		state.markDirty();
	}

	public static void onDisconnect(ServerPlayerEntity player) {
		clear(player);
	}

	private static void clear(ServerPlayerEntity player) {
		Demand demand = DEMANDS.remove(player.getUuid());
		if (demand != null) {
			demand.bar.clearPlayers();
			for (KamentEntity kament : KamentPresence.all(player.getServerWorld())) {
				kament.setQuarry(null);
			}
		}
	}
}
