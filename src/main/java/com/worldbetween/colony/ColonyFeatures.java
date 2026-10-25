package com.worldbetween.colony;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.arki.ArkiHall;
import com.worldbetween.faction.arki.Arkis;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.ChronicleBooks;
import com.worldbetween.town.TownFeatures;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndRodBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Arki colonies as the town builder puts them into the world: discs of white calcite on cushions of
 * living amethyst, on the sea or high in the sky, with the capsule the founders came down in at the centre.
 */
public final class ColonyFeatures {
	private static final String REVISION = ".r1";
	private static final BlockState CALCITE = Blocks.CALCITE.getDefaultState();
	private static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.getDefaultState();
	private static final BlockState AMETHYST = Blocks.AMETHYST_BLOCK.getDefaultState();
	private static final BlockState BUDDING = Blocks.BUDDING_AMETHYST.getDefaultState();
	private static final BlockState PURPUR = Blocks.PURPUR_BLOCK.getDefaultState();
	private static final BlockState GLASS = Blocks.PURPLE_STAINED_GLASS.getDefaultState();
	private static final BlockState BLUE_GLASS = Blocks.BLUE_STAINED_GLASS.getDefaultState();
	private static final BlockState LANTERN = Blocks.SEA_LANTERN.getDefaultState();

	private ColonyFeatures() {
	}

	public static List<TownFeatures.Feature> of(ServerWorld world, Faction faction, Settlement colony) {
		BlockPos origin = ColonyLayout.origin(world, colony);
		boolean sky = Arkis.isSky(colony);
		String base = colony.key(faction.type()) + "/";
		int tier = colony.tier();
		long seed = colony.pos().asLong() * 17 + colony.id();
		List<TownFeatures.Feature> features = new ArrayList<>();
		features.add(TownFeatures.structure(base + "core" + REVISION, tier, v -> core(origin, v, sky, seed), TownFeatures.versionsUpTo(tier)));
		for (int slot = 0; slot < Arkis.capacity(colony); slot++) {
			ArkiHall hall = Arkis.hallAt(colony, slot);
			if (hall == null) {
				continue;
			}
			int[] c = ColonyLayout.slot(slot);
			BlockPos center = origin.add(c[0], ColonyLayout.slotHeight(colony, slot), c[1]);
			int version = hall.ordinal();
			long discSeed = seed + slot * 131;
			features.add(TownFeatures.structure(base + "disc_" + slot + REVISION, version,
					v -> hallDisc(center, ArkiHall.byOrdinal(v), sky, discSeed), new int[]{version}));
		}
		return features;
	}

	// ---------------------------------------------------------------- discs

	/** A disc of calcite on a cushion of amethyst; in the sky, an island with crystals hanging under it. */
	private static void disc(Canvas.Local l, int radius, boolean sky, long seed) {
		Random random = Random.create(seed);
		int depth = sky ? radius + 4 : Math.max(3, radius - 1);
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				double d2 = x * x + z * z;
				if (d2 > radius * radius + radius) {
					continue;
				}
				double edge = Math.sqrt(d2) / radius;
				boolean rim = edge > 0.86;
				l.set(x, 0, z, rim ? QUARTZ : (x + z) % 5 == 0 && random.nextInt(3) == 0 ? AMETHYST : CALCITE);
				for (int y = 1; y <= 4; y++) {
					l.clear(x, y, z);
				}
				int column = Math.max(1, (int) Math.round(depth * (1 - edge * edge)) + random.nextInt(2));
				for (int y = -1; y >= -column; y--) {
					int roll = random.nextInt(10);
					BlockState state = roll < 6 ? AMETHYST : roll < 8 ? BUDDING : sky && roll < 9 ? Blocks.END_STONE.getDefaultState() : CALCITE;
					l.set(x, y, z, state);
				}
				// Crystal points under the cushion: living amethyst, growing into the sea or the air.
				if (random.nextInt(3) == 0) {
					l.set(x, -column - 1, z, Blocks.AMETHYST_CLUSTER.getDefaultState().with(AmethystClusterBlock.FACING, Direction.DOWN)
							.with(AmethystClusterBlock.WATERLOGGED, !sky));
				}
			}
		}
		for (int i = 0; i < 6; i++) {
			double angle = Math.toRadians(i * 60 + 30);
			int x = (int) Math.round(Math.sin(angle) * (radius - 1));
			int z = (int) Math.round(-Math.cos(angle) * (radius - 1));
			l.set(x, 0, z, LANTERN);
		}
	}

	private static Canvas core(BlockPos origin, int tier, boolean sky, long seed) {
		Canvas canvas = new Canvas();
		Canvas.Local l = ColonyLayout.local(canvas, origin);
		int radius = ColonyLayout.centralRadius(tier);
		disc(l, radius, sky, seed);
		capsule(l, 0, 1, 0);
		if (tier >= 1) {
			// The sanctum: a dome of violet and blue glass over the capsule they came down in.
			int r = radius - 1;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					for (int y = 1; y <= r; y++) {
						double d = Math.sqrt(x * x + y * y * 1.4 + z * z);
						if (d > r - 0.6 && d <= r + 0.4) {
							boolean door = Math.abs(x) <= 1 && z < 0 && y <= 3;
							if (!door) {
								l.set(x, y, z, (x + y + z) % 3 == 0 ? BLUE_GLASS : GLASS);
							}
						}
					}
				}
			}
			for (int i = 0; i < 8; i++) {
				double angle = Math.toRadians(i * 45);
				l.set((int) Math.round(Math.sin(angle) * (r - 2)), 1, (int) Math.round(-Math.cos(angle) * (r - 2)), rod(Direction.UP));
			}
		} else {
			// Fresh from the sky: the cushion is still scorched where the comet struck.
			Random random = Random.create(seed);
			for (int i = 0; i < 8; i++) {
				int x = random.nextInt(2 * radius - 1) - radius + 1;
				int z = random.nextInt(2 * radius - 1) - radius + 1;
				if (Math.abs(x) > 2 || Math.abs(z) > 2) {
					l.set(x, 0, z, (random.nextBoolean() ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState());
				}
			}
		}
		l.work(0, 1, -3, Canvas.Work.STORE);
		return canvas;
	}

	/** The comet capsule: a shell of purpur with violet windows, a crystal at its tip. */
	private static void capsule(Canvas.Local l, int cx, int y0, int cz) {
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				l.set(cx + x, y0 - 1, cz + z, Blocks.CRYING_OBSIDIAN.getDefaultState());
				for (int y = 0; y <= 3; y++) {
					boolean corner = Math.abs(x) == 1 && Math.abs(z) == 1;
					boolean inside = x == 0 && z == 0;
					if (inside || (corner && (y == 0 || y == 3))) {
						continue;
					}
					boolean window = !corner && (y == 1 || y == 2);
					boolean hatch = x == 0 && z == -1 && y <= 2;
					if (hatch) {
						continue;
					}
					l.set(cx + x, y0 + y, cz + z, window ? GLASS : PURPUR);
				}
			}
		}
		l.set(cx, y0 + 4, cz, AMETHYST);
		l.set(cx, y0 + 5, cz, rod(Direction.UP));
		l.set(cx, y0 + 3, cz, Blocks.END_ROD.getDefaultState().with(EndRodBlock.FACING, Direction.DOWN));
	}

	private static BlockState rod(Direction facing) {
		return Blocks.END_ROD.getDefaultState().with(EndRodBlock.FACING, facing);
	}

	private static BlockState crystal() {
		return Blocks.AMETHYST_CLUSTER.getDefaultState().with(AmethystClusterBlock.FACING, Direction.UP);
	}

	private static Canvas hallDisc(BlockPos center, ArkiHall hall, boolean sky, long seed) {
		Canvas canvas = new Canvas();
		Canvas.Local l = ColonyLayout.local(canvas, center);
		disc(l, 5, sky, seed);
		switch (hall) {
			case GARDEN -> {
				for (int x = -3; x <= 3; x += 2) {
					for (int z = -3; z <= 3; z += 2) {
						if (x * x + z * z <= 18 && (x != -1 || z != -1)) {
							l.set(x, 0, z, BUDDING);
							l.set(x, 1, z, crystal());
						}
					}
				}
				l.work(-1, 1, -1, Canvas.Work.FIELD);
			}
			case LENS -> {
				for (int y = 1; y <= 7; y++) {
					l.set(0, y, 0, Blocks.QUARTZ_PILLAR.getDefaultState());
				}
				l.set(0, 8, 0, AMETHYST);
				for (Direction d : Direction.Type.HORIZONTAL) {
					l.set(d.getOffsetX(), 8, d.getOffsetZ(), Blocks.TINTED_GLASS.getDefaultState());
					l.set(d.getOffsetX() * 2, 8, d.getOffsetZ() * 2, rod(d));
				}
				l.set(0, 9, 0, rod(Direction.UP));
				for (int[] p : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
					l.set(p[0], 1, p[1], QUARTZ);
					l.set(p[0], 2, p[1], rod(Direction.UP));
				}
				l.work(0, 1, -2, Canvas.Work.KILN);
			}
			case SEAWORKS -> {
				if (!sky) {
					// An open pool down to the sea, rimmed in prismarine.
					for (int x = -1; x <= 1; x++) {
						for (int z = -1; z <= 1; z++) {
							for (int y = -8; y <= 0; y++) {
								l.set(x, y, z, Blocks.WATER.getDefaultState());
							}
						}
					}
				} else {
					l.set(0, 0, 0, Blocks.WATER_CAULDRON.getDefaultState());
				}
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						if (Math.abs(x) == 2 || Math.abs(z) == 2) {
							l.set(x, 0, z, Blocks.PRISMARINE_BRICKS.getDefaultState());
						}
					}
				}
				l.set(3, 1, 0, Blocks.DRIED_KELP_BLOCK.getDefaultState());
				l.set(3, 2, 0, Blocks.DRIED_KELP_BLOCK.getDefaultState());
				l.set(-3, 1, 1, Blocks.DARK_PRISMARINE.getDefaultState());
				l.set(-3, 1, -1, Blocks.PRISMARINE.getDefaultState());
				l.work(0, 1, -3, Canvas.Work.FISHING);
			}
			case OBSERVATORY -> {
				for (int x = -3; x <= 3; x++) {
					for (int z = -3; z <= 3; z++) {
						double d = Math.sqrt(x * x + z * z);
						if (d > 2.4 && d <= 3.4) {
							for (int y = 1; y <= 2; y++) {
								boolean door = x == 0 && z < 0;
								if (!door) {
									l.set(x, y, z, CALCITE);
								}
							}
						}
						for (int y = 3; y <= 5; y++) {
							double dome = Math.sqrt(x * x + (y - 2) * (y - 2) * 2.0 + z * z);
							if (dome > 2.5 && dome <= 3.5) {
								l.set(x, y, z, BLUE_GLASS);
							}
						}
					}
				}
				l.set(0, 1, 1, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.NORTH).with(LecternBlock.HAS_BOOK, true));
				l.hook(0, 1, 1, ChronicleBooks::place);
				l.set(1, 1, 0, rod(Direction.UP));
				l.set(1, 2, 0, Blocks.TINTED_GLASS.getDefaultState());
				l.work(0, 1, 0, Canvas.Work.BOOKS);
			}
			case DWELLING -> {
				capsule(l, -2, 1, 1);
				capsule(l, 2, 1, -1);
				l.set(0, 1, 2, crystal());
			}
			case PAD -> {
				for (int i = 0; i < 12; i++) {
					double angle = Math.toRadians(i * 30);
					l.set((int) Math.round(Math.sin(angle) * 4), 1, (int) Math.round(-Math.cos(angle) * 4), rod(Direction.UP));
				}
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						l.set(x, 0, z, (x + z) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE.getDefaultState() : PURPUR);
					}
				}
				capsule(l, 0, 1, 0);
				l.work(0, 1, -3, Canvas.Work.STORE);
			}
		}
		return canvas;
	}
}
