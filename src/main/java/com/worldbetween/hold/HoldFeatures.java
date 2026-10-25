package com.worldbetween.hold;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.kament.KamentHall;
import com.worldbetween.faction.kament.Kaments;
import com.worldbetween.faction.kament.Tunnel;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.ChronicleBooks;
import com.worldbetween.town.Ground;
import com.worldbetween.town.TownFeatures;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * The Kaments' holds as the town builder puts them into the world: hewn out of the rock rather than built
 * on it. Everything here is drawn at a version, like the towns of Neris, so a hold that grows is hewn
 * further, and the halls change as their work changes them.
 */
public final class HoldFeatures {
	private static final BlockState BRICKS = Blocks.STONE_BRICKS.getDefaultState();
	private static final BlockState CHISELED = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
	private static final BlockState ANDESITE = Blocks.POLISHED_ANDESITE.getDefaultState();
	private static final BlockState DIORITE = Blocks.POLISHED_DIORITE.getDefaultState();
	private static final BlockState LIGHT = Blocks.GLOWSTONE.getDefaultState();
	private static final BlockState DEEP_BRICKS = Blocks.DEEPSLATE_BRICKS.getDefaultState();
	private static final BlockState DEEP_TILES = Blocks.DEEPSLATE_TILES.getDefaultState();
	private static final BlockState DEEP_POLISHED = Blocks.POLISHED_DEEPSLATE.getDefaultState();
	private static final int[][] RING = {{-2, 0}, {-2, -1}, {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2}, {2, -1},
			{2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {-1, 2}, {-2, 2}, {-2, 1}};

	/** Bumped when the hold's blueprint changes, so holds already standing are hewn again. */
	private static final String REVISION = ".r6";

	private HoldFeatures() {
	}

	public static List<TownFeatures.Feature> of(ServerWorld world, Faction faction, Settlement settlement) {
		HoldLayout layout = HoldLayout.of(world, settlement);
		String base = settlement.key(faction.type()) + "/";
		int tier = settlement.tier();
		long seed = settlement.pos().asLong() * 31 + settlement.id();
		List<TownFeatures.Feature> features = new ArrayList<>();
		if (!layout.isDeep()) {
			features.add(TownFeatures.structure(base + "hold" + REVISION, tier, v -> hold(world, layout, v, seed), TownFeatures.versionsUpTo(tier)));
			Settlement child = child(faction, settlement);
			if (child != null) {
				int depth = child.pos().getY() - layout.origin().getY();
				features.add(TownFeatures.structure(base + "shaft", 0, v -> shaft(layout, depth), new int[]{0}));
			}
		} else {
			features.add(TownFeatures.structure(base + "cavern" + REVISION, tier, v -> cavern(layout, v, seed), TownFeatures.versionsUpTo(tier)));
		}
		for (int slot = 0; slot < Kaments.capacity(settlement); slot++) {
			KamentHall hall = Kaments.hallAt(settlement, slot);
			if (hall == null) {
				continue;
			}
			int extra = hall == KamentHall.HEALER ? (int) Math.min(9, settlement.counter("healed") / 16) : 0;
			int version = hall.ordinal() * 100 + (layout.isDeep() ? tier * 10 : 0) + extra;
			int index = slot;
			features.add(TownFeatures.structure(base + "room_" + slot + REVISION, version,
					v -> room(world, layout, index, KamentHall.byOrdinal(v / 100), (v / 10) % 10, v % 10, seed + index), new int[]{version}));
		}
		return features;
	}

	/** The deep city under a mountain hold, if one has been founded. */
	public static Settlement child(Faction faction, Settlement settlement) {
		long child = settlement.counter("child");
		return child == 0 ? null : faction.settlements().stream().filter(s -> s.id() == child - 1).findFirst().orElse(null);
	}

	public static List<TownFeatures.Feature> tunnels(Faction faction) {
		List<TownFeatures.Feature> features = new ArrayList<>();
		for (Tunnel tunnel : faction.tunnels()) {
			int version = (int) (tunnel.dug() / 16) + (tunnel.isThrough() ? 1 : 0);
			features.add(TownFeatures.structure(faction.type().id() + "/tunnel_" + tunnel.id(), version,
					v -> tunnel(tunnel, Math.min(tunnel.length(), v * 16.0)), new int[]{version}));
		}
		return features;
	}

	// ---------------------------------------------------------------- mountain hold

	private static Canvas hold(ServerWorld world, HoldLayout layout, int tier, long seed) {
		Canvas canvas = new Canvas();
		Canvas.Local l = layout.local(canvas);
		Random random = Random.create(seed);
		int hw = HoldLayout.hallHalfWidth(tier);
		int hd = HoldLayout.hallHalfDepth(tier);
		int h = HoldLayout.hallHeight(tier);

		// The great hall, vaulted at the top.
		for (int x = -hw; x <= hw; x++) {
			for (int z = -hd; z <= hd; z++) {
				for (int y = 1; y <= h; y++) {
					int inset = Math.max(0, y - (h - 2));
					if (Math.abs(x) > hw - inset) {
						continue;
					}
					l.carve(x, y, z);
				}
				roof(world, layout, l, x, z, h);
				BlockState floor = tier == 0 ? (random.nextInt(4) == 0 ? Blocks.COBBLESTONE : Blocks.CRACKED_STONE_BRICKS).getDefaultState()
						: x == 0 || z == 0 ? DIORITE : ANDESITE;
				l.set(x, 0, z, floor);
			}
		}
		for (int z = -hd + 2; z <= hd - 2; z += 4) {
			for (int side : new int[]{-1, 1}) {
				int px = side * (hw - 2);
				int height = tier == 0 && random.nextInt(2) == 0 ? 1 + random.nextInt(3) : h - 1;
				for (int y = 1; y <= height; y++) {
					l.set(px, y, z, y % 4 == 2 ? CHISELED : BRICKS);
				}
				if (height == h - 1) {
					l.set(px, h, z, LIGHT);
				} else {
					l.set(px + side, 1, z + 1, Blocks.COBBLESTONE.getDefaultState());
					l.set(px - side, 1, z - 1, Blocks.GRAVEL.getDefaultState());
				}
				// The niches they slept the mourning sleep in, ages long.
				l.carveBox(side * (hw + 1), 1, z, side * (hw + 1), 3, z);
				l.set(side * (hw + 1), 4, z, CHISELED);
				l.set(side * (hw + 1), 0, z, DIORITE);
			}
		}
		for (int z = -hd + 3; z <= hd - 3; z += 6) {
			l.set(0, h + 1, z, LIGHT);
		}
		// The Ancient's seat at the back of the hall.
		for (int x = -2; x <= 2; x++) {
			l.set(x, 1, hd - 1, DIORITE);
			l.set(x, 1, hd - 2, x == -2 || x == 2 ? DIORITE : Blocks.POLISHED_DIORITE_SLAB.getDefaultState());
		}
		l.set(0, 2, hd, Blocks.CHISELED_TUFF.getDefaultState());
		l.set(0, 3, hd, Blocks.CHISELED_TUFF_BRICKS.getDefaultState());
		l.set(-1, 2, hd, Blocks.TUFF_BRICKS.getDefaultState());
		l.set(1, 2, hd, Blocks.TUFF_BRICKS.getDefaultState());
		l.set(-2, 2, hd - 1, crystal());
		l.set(2, 2, hd - 1, crystal());
		if (tier == 0) {
			for (int i = 0; i < 8; i++) {
				int x = random.nextInt(hw * 2 - 1) - hw + 1;
				int z = random.nextInt(hd * 2 - 3) - hd + 1;
				if (Math.abs(x) > 1) {
					l.set(x, 1, z, (random.nextBoolean() ? Blocks.GRAVEL : Blocks.COBBLESTONE).getDefaultState());
				}
			}
		}

		// The gallery out to the gate.
		int exit = layout.exit();
		for (int z = -exit; z <= -hd - 1; z++) {
			for (int x = -2; x <= 2; x++) {
				for (int y = 1; y <= 5; y++) {
					if (Math.abs(x) == 2 && y == 5) {
						continue;
					}
					l.carve(x, y, z);
				}
				l.set(x, 0, z, BRICKS);
				roof(world, layout, l, x, z, 5);
			}
			if ((z + exit) % 6 == 3) {
				for (int x = -2; x <= 2; x++) {
					l.set(x, 6, z, CHISELED);
				}
				l.set(0, 6, z, LIGHT);
			}
		}
		gate(l, layout, -exit);

		// The inner gallery, where the working halls open off.
		for (int z = hd + 1; z <= HoldLayout.innerEnd(tier); z++) {
			for (int x = -1; x <= 1; x++) {
				for (int y = 1; y <= 4; y++) {
					l.carve(x, y, z);
				}
				l.set(x, 0, z, ANDESITE);
				roof(world, layout, l, x, z, 4);
			}
			if (z % 8 == 0) {
				l.set(0, 5, z, LIGHT);
			}
		}
		return canvas;
	}

	/** The gate in the mountainside: a frame of dressed stone, two stone watchers, steps down to the land. */
	private static void gate(Canvas.Local l, HoldLayout layout, int z0) {
		for (int x = -3; x <= 3; x++) {
			for (int y = 0; y <= 7; y++) {
				if (Math.abs(x) == 3 || y >= 6) {
					l.set(x, y, z0, y == 3 && Math.abs(x) == 3 || y == 7 && x == 0 ? CHISELED : BRICKS);
				}
			}
		}
		statue(l, -5, 1, z0 - 1);
		statue(l, 5, 1, z0 - 1);
		int floor = layout.origin().getY();
		// Where the land rises again in front of the gate, a cutting goes through it.
		for (int k = 0; k < 16; k++) {
			int ground = layout.apron(k) - floor;
			if (ground < 1) {
				break;
			}
			for (int x = -2; x <= 2; x++) {
				for (int y = 1; y <= ground + 3; y++) {
					l.carve(x, y, z0 - 1 - k);
				}
				l.set(x, 0, z0 - 1 - k, BRICKS);
			}
		}
		for (int k = 0; k < 16; k++) {
			int ground = layout.apron(k) - floor;
			int step = -k;
			if (ground >= step) {
				break;
			}
			for (int x = -2; x <= 2; x++) {
				l.set(x, step, z0 - 1 - k, Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
				for (int y = step - 1; y >= Math.max(ground, step - 12); y--) {
					l.fill(x, y, z0 - 1 - k, BRICKS);
				}
			}
		}
	}

	/** A stone watcher in the likeness of the Kaments themselves. */
	private static void statue(Canvas.Local l, int x, int y, int z) {
		l.set(x, y - 1, z, ANDESITE);
		l.set(x, y, z, Blocks.STONE.getDefaultState());
		l.set(x, y + 1, z, BRICKS);
		l.set(x, y + 2, z, BRICKS);
		l.set(x - 1, y + 2, z, Blocks.STONE_BRICK_WALL.getDefaultState());
		l.set(x + 1, y + 2, z, Blocks.STONE_BRICK_WALL.getDefaultState());
		l.set(x, y + 3, z, CHISELED);
	}

	/** The spiral stair down to the deep city: round a pillar, ever down, a light at every turn. */
	private static Canvas shaft(HoldLayout layout, int depth) {
		Canvas canvas = new Canvas();
		Canvas.Local l = layout.local(canvas);
		int cx = HoldLayout.SHAFT_X;
		for (int x = 6; x <= cx - 3; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int y = 1; y <= 4; y++) {
					l.carve(x, y, z);
				}
				l.set(x, 0, z, ANDESITE);
			}
		}
		int bottom = depth + 1;
		for (int k = 0; -k >= bottom; k++) {
			int[] cell = RING[k % RING.length];
			int[] previous = RING[(k - 1 + RING.length) % RING.length];
			int y = -k;
			for (int dy = 1; dy <= 3; dy++) {
				l.carve(cx + cell[0], y + dy, cell[1]);
			}
			Direction up = direction(previous[0] - cell[0], previous[1] - cell[1]);
			l.set(cx + cell[0], y, cell[1], (y < -20 ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS).getDefaultState()
					.with(StairsBlock.FACING, up));
		}
		for (int y = 3; y >= bottom; y--) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean light = (y % 8 == 0) && (dx != 0 || dz != 0) && (dx == 0 || dz == 0);
					l.set(cx + dx, y, dz, light ? LIGHT : y < -20 ? DEEP_BRICKS : BRICKS);
				}
			}
		}
		return canvas;
	}

	private static Direction direction(int dx, int dz) {
		if (dx > 0) {
			return Direction.EAST;
		}
		if (dx < 0) {
			return Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	// ---------------------------------------------------------------- deep city

	private static Canvas cavern(HoldLayout layout, int tier, long seed) {
		Canvas canvas = new Canvas();
		Canvas.Local l = layout.local(canvas);
		int r = HoldLayout.cavernRadius(tier);
		int ry = HoldLayout.cavernHeight(tier);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d2 = dx * dx + dz * dz;
				if (d2 > r * r) {
					continue;
				}
				int top = Math.max(3, (int) Math.round(ry * Math.sqrt(1 - d2 / (r * r))) + 2);
				for (int y = 1; y <= top; y++) {
					l.carve(dx, y, dz);
				}
				double d = Math.sqrt(d2);
				BlockState floor = Math.abs(d - r / 2.0) < 0.7 ? DIORITE : d > r - 2 ? DEEP_POLISHED : DEEP_TILES;
				l.set(dx, 0, dz, floor);
			}
		}
		for (int i = 0; i < 8; i++) {
			double angle = Math.toRadians(i * 45 + 22.5);
			int px = (int) Math.round(Math.sin(angle) * (r - 3));
			int pz = (int) Math.round(-Math.cos(angle) * (r - 3));
			double d2 = px * px + pz * pz;
			int top = Math.max(3, (int) Math.round(ry * Math.sqrt(1 - d2 / (r * r))) + 2);
			for (int y = 1; y < top; y++) {
				l.set(px, y, pz, y % 4 == 2 ? Blocks.CHISELED_DEEPSLATE.getDefaultState() : DEEP_BRICKS);
			}
			l.set(px, top, pz, Blocks.SHROOMLIGHT.getDefaultState());
		}
		// The Ancient's dais, to one side of the stair that comes down from above.
		int dz = r / 2;
		for (int x = -2; x <= 2; x++) {
			for (int z = dz - 1; z <= dz + 1; z++) {
				l.set(x, 1, z, DIORITE);
			}
		}
		l.set(0, 2, dz + 1, Blocks.CHISELED_TUFF.getDefaultState());
		l.set(-2, 2, dz, crystal());
		l.set(2, 2, dz, crystal());
		// Lights set into the dome itself, where the hollow meets the rock.
		for (int i = 0; i < 8; i++) {
			double angle = Math.toRadians(i * 45);
			int lx = (int) Math.round(Math.sin(angle) * r / 2.0);
			int lz = (int) Math.round(-Math.cos(angle) * r / 2.0);
			double d2 = lx * lx + lz * lz;
			int top = Math.max(3, (int) Math.round(ry * Math.sqrt(1 - d2 / (r * r))) + 2);
			l.set(lx, top, lz, LIGHT);
		}
		l.set(0, ry + 2, 0, Blocks.SHROOMLIGHT.getDefaultState());
		return canvas;
	}

	// ---------------------------------------------------------------- halls

	private static Canvas room(ServerWorld world, HoldLayout layout, int slot, KamentHall hall, int tier, int extra, long seed) {
		Canvas canvas = new Canvas();
		Canvas.Local l = layout.local(canvas);
		Random random = Random.create(seed);
		int[] c = layout.slot(slot, tier);
		int sx = c[0];
		int sz = c[1];
		boolean deep = layout.isDeep();
		// The way in.
		if (!deep) {
			int side = Integer.signum(sx);
			for (int x = side * 2; x != sx - side * 4; x += side) {
				for (int z = sz - 1; z <= sz + 1; z++) {
					for (int y = 1; y <= 3; y++) {
						l.carve(x, y, z);
					}
					l.set(x, 0, z, ANDESITE);
				}
			}
		} else {
			double angle = layout.slotAngle(slot);
			double fx = Math.sin(angle);
			double fz = -Math.cos(angle);
			for (double t = HoldLayout.cavernRadius(tier) - 2; t <= HoldLayout.cavernRadius(3) + 6; t += 0.5) {
				for (int w = -1; w <= 1; w++) {
					int x = (int) Math.round(fx * t - fz * w);
					int z = (int) Math.round(fz * t + fx * w);
					for (int y = 1; y <= 3; y++) {
						l.carve(x, y, z);
					}
					l.set(x, 0, z, DEEP_TILES);
				}
			}
		}
		int half = hall == KamentHall.QUARRY ? 5 : 4;
		BlockState floor = switch (hall) {
			case GRAVEL_PIT, KNAPPERY -> Blocks.COARSE_DIRT.getDefaultState();
			case QUARRY -> Blocks.STONE.getDefaultState();
			case MEMORY, SCULPTORY -> DIORITE;
			default -> deep ? DEEP_TILES : ANDESITE;
		};
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				boolean edge = Math.abs(x) == half || Math.abs(z) == half;
				int height = hall == KamentHall.QUARRY ? 6 - (edge ? random.nextInt(3) : 0) : 5;
				if (hall == KamentHall.QUARRY && edge && random.nextInt(3) == 0) {
					continue;
				}
				for (int y = 1; y <= height; y++) {
					l.carve(sx + x, y, sz + z);
				}
				l.set(sx + x, 0, sz + z, floor);
				if (!deep) {
					roof(world, layout, l, sx + x, sz + z, height);
				}
			}
		}
		if (!deep) {
			// Where the mountainside falls away beside a hall, its wall is built up in dressed stone.
			for (int i = -half - 1; i <= half + 1; i++) {
				for (int[] w : new int[][]{{i, -half - 1}, {i, half + 1}, {-half - 1, i}, {half + 1, i}}) {
					BlockPos column = l.world(sx + w[0], 0, sz + w[1]);
					if (Ground.floor(world, column.getX(), column.getZ()) < layout.origin().getY() + 9) {
						for (int y = 1; y <= 7; y++) {
							l.set(sx + w[0], y, sz + w[1], BRICKS);
						}
					}
				}
			}
		}
		l.set(sx, 6, sz, LIGHT);
		for (int[] corner : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			l.set(sx + corner[0], 6, sz + corner[1], LIGHT);
		}
		switch (hall) {
			case QUARRY -> {
				pile(l, sx - 3, sz - 3, Blocks.STONE.getDefaultState(), 2);
				pile(l, sx + 3, sz - 3, Blocks.DIORITE.getDefaultState(), 2);
				pile(l, sx + 3, sz + 3, Blocks.COBBLESTONE.getDefaultState(), 1);
				l.set(sx - 3, 1, sz + 3, Blocks.STONECUTTER.getDefaultState());
				l.work(sx, 1, sz + 3, Canvas.Work.CHISEL);
			}
			case GRAVEL_PIT -> {
				pile(l, sx - 3, sz - 3, Blocks.GRAVEL.getDefaultState(), 2);
				pile(l, sx + 3, sz - 3, Blocks.GRAVEL.getDefaultState(), 1);
				pile(l, sx - 3, sz + 3, Blocks.GRAVEL.getDefaultState(), 2);
				l.work(sx, 1, sz, Canvas.Work.DRILL);
			}
			case KNAPPERY -> {
				pile(l, sx - 3, sz - 3, Blocks.GRAVEL.getDefaultState(), 1);
				l.set(sx + 2, 1, sz, Blocks.GRINDSTONE.getDefaultState());
				l.set(sx + 3, 1, sz + 3, Blocks.SUSPICIOUS_GRAVEL.getDefaultState());
				l.work(sx + 1, 1, sz, Canvas.Work.GRIND);
			}
			case CARVERY -> {
				l.set(sx - 2, 1, sz + 3, Blocks.STONECUTTER.getDefaultState());
				l.set(sx + 2, 1, sz + 3, Blocks.STONECUTTER.getDefaultState());
				l.set(sx - 3, 1, sz - 3, CHISELED);
				l.set(sx + 3, 1, sz - 3, Blocks.STONE_BRICK_STAIRS.getDefaultState());
				l.set(sx + 3, 1, sz - 2, BRICKS);
				l.work(sx, 1, sz + 2, Canvas.Work.CHISEL);
			}
			case SCULPTORY -> {
				statue(l, sx - 2, 1, sz + 2);
				l.set(sx + 2, 1, sz + 2, Blocks.STONE.getDefaultState());
				l.set(sx + 2, 2, sz + 2, Blocks.ANDESITE.getDefaultState());
				l.set(sx, 0, sz, Blocks.AMETHYST_BLOCK.getDefaultState());
				l.set(sx - 3, 1, sz - 3, crystal());
				l.set(sx + 3, 1, sz - 3, crystal());
				l.work(sx + 1, 1, sz + 1, Canvas.Work.CHISEL);
			}
			case MEMORY -> {
				for (int x = -3; x <= 3; x += 2) {
					l.set(sx + x, 2, sz + 4, CHISELED);
					l.set(sx + x, 3, sz + 4, CHISELED);
				}
				l.set(sx - 1, 1, sz + 1, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.NORTH).with(LecternBlock.HAS_BOOK, true));
				l.hook(sx - 1, 1, sz + 1, ChronicleBooks::place);
				l.set(sx + 1, 1, sz + 1, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.NORTH));
				l.work(sx, 1, sz, Canvas.Work.BOOKS);
			}
			case HEALER -> {
				BlockState[] ores = deep
						? new BlockState[]{Blocks.DEEPSLATE_IRON_ORE.getDefaultState(), Blocks.DEEPSLATE_COPPER_ORE.getDefaultState(), Blocks.DEEPSLATE_GOLD_ORE.getDefaultState()}
						: new BlockState[]{Blocks.IRON_ORE.getDefaultState(), Blocks.COPPER_ORE.getDefaultState(), Blocks.GOLD_ORE.getDefaultState()};
				// The metal given back to the mountain shows in the walls as veins, more as more is healed.
				for (int i = 0; i < 3 + extra * 3; i++) {
					int wall = random.nextInt(4);
					int along = random.nextInt(7) - 3;
					int y = 1 + random.nextInt(4);
					int x = wall < 2 ? (wall == 0 ? -5 : 5) : along;
					int z = wall < 2 ? along : (wall == 2 ? -5 : 5);
					l.set(sx + x, y, sz + z, ores[random.nextInt(ores.length)]);
				}
				l.set(sx, 1, sz, crystal());
				l.work(sx + 1, 1, sz + 1, Canvas.Work.GRIND);
			}
			case WARD -> {
				for (int x : new int[]{-3, 3}) {
					for (int y = 1; y <= 5; y++) {
						l.set(sx + x, y, sz, y % 2 == 0 ? Blocks.CHISELED_TUFF.getDefaultState() : Blocks.TUFF_BRICKS.getDefaultState());
					}
				}
				l.work(sx, 1, sz + 2, Canvas.Work.STORE);
			}
		}
		return canvas;
	}

	/** Where the mountain is thin over a hall, a vault of dressed stone keeps the sky out. */
	private static void roof(ServerWorld world, HoldLayout layout, Canvas.Local l, int x, int z, int height) {
		if (layout.isDeep()) {
			return;
		}
		BlockPos column = l.world(x, 0, z);
		if (Ground.floor(world, column.getX(), column.getZ()) < layout.origin().getY() + height + 3) {
			l.set(x, height + 1, z, BRICKS);
			l.set(x, height + 2, z, BRICKS);
		}
	}

	private static void pile(Canvas.Local l, int x, int z, BlockState state, int height) {
		for (int y = 1; y <= height; y++) {
			l.set(x, y, z, state);
		}
		l.set(x + 1, 1, z, state);
		l.set(x, 1, z + 1, state);
	}

	private static BlockState crystal() {
		return Blocks.AMETHYST_CLUSTER.getDefaultState().with(AmethystClusterBlock.FACING, Direction.UP);
	}

	// ---------------------------------------------------------------- tunnels

	/** The stretch of a tunnel dug so far: three wide, four high, a dressed-stone arch and a light every sixteen blocks. */
	private static Canvas tunnel(Tunnel tunnel, double dug) {
		Canvas canvas = new Canvas();
		List<BlockPos> path = tunnel.path();
		double walked = 0;
		double nextArch = 8;
		for (int i = 1; i < path.size() && walked < dug; i++) {
			BlockPos a = path.get(i - 1);
			BlockPos b = path.get(i);
			double length = Math.sqrt(a.getSquaredDistance(b));
			double fx = (b.getX() - a.getX()) / Math.max(1e-6, length);
			double fz = (b.getZ() - a.getZ()) / Math.max(1e-6, length);
			for (double s = 0; s < length && walked + s < dug; s += 0.5) {
				double t = s / length;
				double px = a.getX() + (b.getX() - a.getX()) * t;
				double py = a.getY() + (b.getY() - a.getY()) * t;
				double pz = a.getZ() + (b.getZ() - a.getZ()) * t;
				boolean arch = walked + s >= nextArch;
				if (arch) {
					nextArch += 16;
				}
				for (int w = -2; w <= 2; w++) {
					int x = (int) Math.round(px - fz * w);
					int z = (int) Math.round(pz + fx * w);
					int y0 = (int) Math.round(py);
					for (int y = 0; y <= 4; y++) {
						BlockPos pos = new BlockPos(x, y0 + y, z);
						if (Math.abs(w) <= 1 && y <= 3) {
							canvas.put(pos, Blocks.AIR.getDefaultState(), Canvas.Mode.CARVE);
						} else if (arch && (Math.abs(w) == 2 || y == 4)) {
							canvas.put(pos, w == 0 && y == 4 ? LIGHT : y0 < -8 ? DEEP_BRICKS : BRICKS, Canvas.Mode.SET);
						}
					}
				}
			}
			walked += length;
		}
		return canvas;
	}
}
