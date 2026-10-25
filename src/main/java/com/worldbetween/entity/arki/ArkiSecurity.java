package com.worldbetween.entity.arki;

import com.worldbetween.colony.ColonyLayout;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The law of the Arki. The plain one every people has (do not kill, do not steal, do not break), and their
 * own: nobody flies over their colonies, and nobody builds above them; the sky over a colony is theirs, it
 * is all of the sky they have left. A flyer is warned and given a few seconds to come down or go; whoever
 * keeps on, or keeps building, or breaks the colony, becomes the Arki's enemy.
 */
public final class ArkiSecurity {
	/** How far from a colony's centre its sky reaches. */
	public static final int TERRITORY = 42;
	private static final int GRACE_SECONDS = 10;
	private static final int STRIKES = 3;
	private static final Set<Block> COLONY = Set.of(Blocks.CALCITE, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.AMETHYST_CLUSTER,
			Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_PILLAR, Blocks.SEA_LANTERN, Blocks.TINTED_GLASS, Blocks.PURPLE_STAINED_GLASS,
			Blocks.BLUE_STAINED_GLASS, Blocks.END_ROD, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.END_STONE,
			Blocks.PURPUR_BLOCK, Blocks.LECTERN, Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE, Blocks.DRIED_KELP_BLOCK);

	private static final Map<UUID, Warning> WARNINGS = new HashMap<>();
	private static final Map<UUID, Integer> STRIKE_COUNT = new HashMap<>();
	private static final Map<UUID, Integer> AIRBORNE = new HashMap<>();

	private record Warning(Settlement colony, long deadline, ServerBossBar bar) {
	}

	private ArkiSecurity() {
	}

	/** The colony whose sky a point is in, if any. */
	public static Settlement colonyAt(ServerWorld world, Faction faction, BlockPos pos) {
		if (!faction.isActive() || world.getRegistryKey() != World.OVERWORLD) {
			return null;
		}
		for (Settlement colony : faction.settlements()) {
			BlockPos origin = ColonyLayout.origin(world, colony);
			double dx = pos.getX() - origin.getX();
			double dz = pos.getZ() - origin.getZ();
			if (dx * dx + dz * dz < TERRITORY * TERRITORY && pos.getY() > origin.getY() - 24) {
				return colony;
			}
		}
		return null;
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % 10 != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		Faction faction = state.faction(FactionType.ARKI);
		long now = server.getTicks();
		for (ServerPlayerEntity player : world.getPlayers()) {
			UUID id = player.getUuid();
			Settlement colony = player.isCreative() || player.isSpectator() ? null : colonyAt(world, faction, player.getBlockPos());
			boolean flying = colony != null && flying(world, player, colony);
			Warning warning = WARNINGS.get(id);
			if (!flying || faction.isCriminal(id)) {
				if (warning != null) {
					clear(id);
					if (colony != null && !faction.isCriminal(id)) {
						player.sendMessage(Text.translatable("message.worldbetween.arki.landed").formatted(Formatting.LIGHT_PURPLE), true);
					}
				}
				continue;
			}
			if (warning == null) {
				ServerBossBar bar = new ServerBossBar(Text.translatable("bossbar.worldbetween.arki.flight"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
				bar.addPlayer(player);
				WARNINGS.put(id, new Warning(colony, now + GRACE_SECONDS * 20L, bar));
				player.sendMessage(Text.translatable("message.worldbetween.arki.flight", colony.name()).formatted(Formatting.LIGHT_PURPLE), false);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, GRACE_SECONDS * 20, 0, false, false));
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.5f, 0.6f);
				continue;
			}
			warning.bar().setPercent(Math.max(0, (warning.deadline() - now) / (GRACE_SECONDS * 20f)));
			if (now >= warning.deadline()) {
				clear(id);
				outlaw(server, faction, warning.colony(), player, "flight");
			}
		}
	}

	/** On elytra, flying, or hanging in the air high over the discs for a while. */
	private static boolean flying(ServerWorld world, ServerPlayerEntity player, Settlement colony) {
		if (player.isFallFlying() || player.getAbilities().flying) {
			return true;
		}
		BlockPos origin = ColonyLayout.origin(world, colony);
		UUID id = player.getUuid();
		if (!player.isOnGround() && !player.isTouchingWater() && player.getY() > origin.getY() + 10 && !player.isClimbing()) {
			int ticks = AIRBORNE.merge(id, 10, Integer::sum);
			return ticks >= 60;
		}
		AIRBORNE.remove(id);
		return false;
	}

	/** Building above a colony: the block is not placed, and the third time the Arki have had enough. */
	public static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
		if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer) || player.isCreative() || player.isSpectator()) {
			return ActionResult.PASS;
		}
		ItemStack stack = player.getStackInHand(hand);
		if (!(stack.getItem() instanceof BlockItem)) {
			return ActionResult.PASS;
		}
		ServerWorld serverWorld = (ServerWorld) world;
		Faction faction = WorldBetweenState.get(serverWorld.getServer()).faction(FactionType.ARKI);
		BlockPos at = new ItemPlacementContext(new ItemUsageContext(player, hand, hit)).getBlockPos();
		Settlement colony = colonyAt(serverWorld, faction, at);
		if (colony == null || at.getY() <= ColonyLayout.origin(serverWorld, colony).getY() + 2) {
			return ActionResult.PASS;
		}
		serverPlayer.sendMessage(Text.translatable("message.worldbetween.arki.no_building", colony.name()).formatted(Formatting.LIGHT_PURPLE), true);
		serverWorld.playSound(null, at, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 1f, 1.6f);
		strike(serverWorld.getServer(), faction, colony, serverPlayer, "building");
		return ActionResult.FAIL;
	}

	/** Breaking the colony: the crystal, the discs, the lenses. */
	public static void onBlockBroken(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
		if (player.isCreative() || !COLONY.contains(state.getBlock())) {
			return;
		}
		Faction faction = WorldBetweenState.get(world.getServer()).faction(FactionType.ARKI);
		Settlement colony = colonyAt(world, faction, pos);
		if (colony == null) {
			return;
		}
		faction.changeReputation(player.getUuid(), -10);
		player.sendMessage(Text.translatable("message.worldbetween.arki.broken", colony.name()).formatted(Formatting.LIGHT_PURPLE), true);
		strike(world.getServer(), faction, colony, player, "breaking");
	}

	private static void strike(MinecraftServer server, Faction faction, Settlement colony, ServerPlayerEntity player, String crime) {
		if (faction.isCriminal(player.getUuid())) {
			return;
		}
		int strikes = STRIKE_COUNT.merge(player.getUuid(), 1, Integer::sum);
		if (strikes >= STRIKES) {
			STRIKE_COUNT.remove(player.getUuid());
			outlaw(server, faction, colony, player, crime);
		}
	}

	/** The stranger is the Arki's enemy now: every Arki that sees them turns its magic on them. */
	public static void outlaw(MinecraftServer server, Faction faction, Settlement colony, ServerPlayerEntity player, String crime) {
		if (faction.isCriminal(player.getUuid())) {
			return;
		}
		WorldBetweenState state = WorldBetweenState.get(server);
		faction.declareCriminal(player.getUuid(), state.day());
		faction.changeReputation(player.getUuid(), -20);
		player.sendMessage(Text.translatable("message.worldbetween.arki.outlaw." + crime, colony.name()).formatted(Formatting.RED), false);
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.5f, 1.4f);
		new SimulationContext(server, state).chronicle(FactionType.ARKI, "chronicle.worldbetween.arki.outlaw", player.getName().getString(), colony.name());
		state.markDirty();
	}

	private static void clear(UUID id) {
		Warning warning = WARNINGS.remove(id);
		if (warning != null) {
			warning.bar().clearPlayers();
		}
		AIRBORNE.remove(id);
	}

	public static void onDisconnect(ServerPlayerEntity player) {
		clear(player.getUuid());
		STRIKE_COUNT.remove(player.getUuid());
	}
}
