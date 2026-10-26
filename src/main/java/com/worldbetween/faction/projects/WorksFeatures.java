package com.worldbetween.faction.projects;

import com.worldbetween.colony.ColonyLayout;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.hold.HoldLayout;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.Damage;
import com.worldbetween.town.Ground;
import com.worldbetween.town.TownFeatures;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * The great works standing in the world: the kingdom's menagerie of manticores and its Great Portal of
 * Invasion beside the capital, the Kaments' quake crystal and the seat of Morok before a hold's gate, the
 * Arki's Tower of Eternal Night on its own cushion of amethyst. While a work is being made it stands half
 * built; when it is done it is whole. They belong to their town, and war breaks and builders mend them too.
 */
public final class WorksFeatures {
	private WorksFeatures() {
	}

	public static List<TownFeatures.Feature> of(ServerWorld world, Faction faction, Settlement settlement) {
		List<TownFeatures.Feature> features = new ArrayList<>();
		for (Project project : Project.values()) {
			if (project.owner() != faction.type() || Projects.seat(faction, project) != settlement) {
				continue;
			}
			int stage = Projects.stage(faction, project);
			if (stage < Project.MAKING || project == Project.COMET_STORM) {
				continue;
			}
			int version = stage == Project.DONE ? 1 : 0;
			String key = settlement.key(faction.type()) + "/work_" + project.id();
			BlockPos origin = origin(world, faction, settlement, project);
			if (origin == null) {
				continue;
			}
			features.add(TownFeatures.structure(key, version, v -> draw(project, origin, v), new int[]{0, 1}));
		}
		return features;
	}

	/** Where a work stands: outside the capital's streets, before a hold's gate, off a colony's discs. */
	public static BlockPos origin(ServerWorld world, Faction faction, Settlement settlement, Project project) {
		// Once chosen, a work's place does not move, however the kingdom grows round it.
		if (faction.counter(project.key("placed")) == settlement.id() + 1) {
			BlockPos placed = new BlockPos((int) faction.counter(project.key("x")), (int) faction.counter(project.key("y")), (int) faction.counter(project.key("z")));
			return placed;
		}
		BlockPos chosen = choose(world, faction, settlement, project);
		if (chosen != null) {
			faction.setCounter(project.key("placed"), settlement.id() + 1);
			faction.setCounter(project.key("x"), chosen.getX());
			faction.setCounter(project.key("y"), chosen.getY());
			faction.setCounter(project.key("z"), chosen.getZ());
		}
		return chosen;
	}

	private static BlockPos choose(ServerWorld world, Faction faction, Settlement settlement, Project project) {
		BlockPos center = settlement.pos();
		int reach = 84 + 16 * settlement.tier();
		// Away from the neighbouring towns, where the roads go: the menagerie on the far side, the portal across from it.
		double ax = 0;
		double az = 0;
		for (Settlement other : faction.settlements()) {
			double d = Math.max(1, Math.hypot(other.pos().getX() - center.getX(), other.pos().getZ() - center.getZ()));
			if (other != settlement) {
				ax += (other.pos().getX() - center.getX()) / d;
				az += (other.pos().getZ() - center.getZ()) / d;
			}
		}
		double angle = ax == 0 && az == 0 ? Math.PI / 2 : Math.atan2(-az, -ax);
		BlockPos at = switch (project) {
			case MANTICORES -> center.add((int) (Math.cos(angle) * reach), 0, (int) (Math.sin(angle) * reach));
			case HELL_CAMPAIGN -> center.add((int) (Math.cos(angle + 1.2) * reach), 0, (int) (Math.sin(angle + 1.2) * reach));
			case QUAKE_CRYSTAL, MOROK -> {
				HoldLayout layout = HoldLayout.of(world, settlement);
				if (layout.isDeep()) {
					yield null;
				}
				BlockPos gate = layout.local().world(project == Project.MOROK ? 9 : -9, 0, -layout.exit() - 10);
				yield gate;
			}
			case NIGHT_TOWER -> ColonyLayout.origin(world, settlement).add(0, 0, -46);
			case COMET_STORM -> null;
		};
		if (at == null) {
			return null;
		}
		if (project == Project.NIGHT_TOWER) {
			return at;
		}
		return new BlockPos(at.getX(), Ground.floor(world, at.getX(), at.getZ()) + 1, at.getZ());
	}

	private static Canvas draw(Project project, BlockPos origin, int version) {
		Canvas canvas = new Canvas();
		Canvas.Local b = canvas.at(origin, Direction.NORTH);
		switch (project) {
			case MANTICORES -> menagerie(b, version);
			case HELL_CAMPAIGN -> portal(b, version);
			case QUAKE_CRYSTAL -> crystal(b, version);
			case MOROK -> seatOfMorok(b, version);
			case NIGHT_TOWER -> tower(b, version);
			default -> {
			}
		}
		return canvas;
	}

	/** A long stone hall with barred cages and a roof open to the sky for the beasts to fly out. */
	private static void menagerie(Canvas.Local b, int v) {
		BlockState wall = Blocks.STONE_BRICKS.getDefaultState();
		b.site(-8, -6, 8, 6, 7, Blocks.COBBLESTONE.getDefaultState(), Blocks.POLISHED_ANDESITE.getDefaultState());
		int height = v == 0 ? 2 : 6;
		for (int x = -8; x <= 8; x++) {
			for (int z = -6; z <= 6; z++) {
				boolean edge = x == -8 || x == 8 || z == -6 || z == 6;
				if (!edge) {
					continue;
				}
				for (int y = 1; y <= height; y++) {
					boolean window = y >= 3 && y <= 4 && (x + z) % 3 == 0 && !(x == -8 || x == 8) ;
					boolean door = z == -6 && Math.abs(x) <= 1 && y <= 3;
					if (door) {
						continue;
					}
					b.set(x, y, z, window ? Blocks.IRON_BARS.getDefaultState() : (y == 1 || x % 4 == 0 ? Blocks.CHISELED_STONE_BRICKS.getDefaultState() : wall));
				}
			}
		}
		if (v == 0) {
			for (int x = -8; x <= 8; x += 4) {
				for (int y = 1; y <= 6; y++) {
					b.set(x, y, -7, Blocks.SCAFFOLDING.getDefaultState());
				}
			}
			return;
		}
		// The roof: dark oak round the rim, open in the middle.
		for (int x = -8; x <= 8; x++) {
			for (int z = -6; z <= 6; z++) {
				if (Math.abs(x) >= 6 || Math.abs(z) >= 4) {
					b.set(x, 7, z, Blocks.DARK_OAK_SLAB.getDefaultState());
				}
			}
		}
		// Two cages of bars, with straw and gnawed bones.
		for (int side = -1; side <= 1; side += 2) {
			for (int x = 2; x <= 6; x++) {
				for (int y = 1; y <= 3; y++) {
					b.set(side * x, y, 1, Blocks.IRON_BARS.getDefaultState());
				}
			}
			for (int z = 2; z <= 5; z++) {
				for (int y = 1; y <= 3; y++) {
					b.set(side * 2, y, z, Blocks.IRON_BARS.getDefaultState());
				}
			}
			b.set(side * 4, 1, 3, Blocks.HAY_BLOCK.getDefaultState());
			b.set(side * 5, 1, 4, Blocks.HAY_BLOCK.getDefaultState());
			b.set(side * 6, 1, 2, Blocks.BONE_BLOCK.getDefaultState());
			b.set(side * 3, 6, 3, Blocks.CHAIN.getDefaultState());
			b.set(side * 3, 5, 3, Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
		}
		b.set(0, 1, 4, Blocks.LECTERN.getDefaultState());
		b.set(0, 1, -2, Blocks.BLACK_CARPET.getDefaultState());
		b.set(-7, 1, -5, Blocks.BARREL.getDefaultState());
		b.set(7, 1, -5, Blocks.SMITHING_TABLE.getDefaultState());
	}

	/** The Great Portal of Invasion: a blackstone bastion round a portal six blocks wide. */
	private static void portal(Canvas.Local b, int v) {
		b.site(-8, -5, 8, 5, 12, Blocks.BLACKSTONE.getDefaultState(), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
		BlockState frame = Blocks.OBSIDIAN.getDefaultState();
		int top = v == 0 ? 3 : 8;
		for (int x = -4; x <= 4; x++) {
			for (int y = 1; y <= top; y++) {
				if (x == -4 || x == 4 || y == 1 || y == 8) {
					b.set(x, y, 0, frame);
				}
			}
		}
		// Two towers either side, and walls to the back.
		for (int side = -1; side <= 1; side += 2) {
			for (int x = 6; x <= 8; x++) {
				for (int z = -2; z <= 2; z++) {
					for (int y = 1; y <= (v == 0 ? 3 : 11); y++) {
						boolean hollow = x == 7 && z == 0 && y < 10;
						if (!hollow) {
							b.set(side * x, y, z, y % 4 == 0 ? Blocks.GILDED_BLACKSTONE.getDefaultState() : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
						}
					}
				}
			}
			if (v == 1) {
				b.set(side * 7, 12, 0, Blocks.SOUL_LANTERN.getDefaultState());
				b.set(side * 5, 1, -2, Blocks.BLACK_BANNER.getDefaultState());
				for (int y = 2; y <= 7; y++) {
					b.set(side * 5, y, 0, Blocks.CHAIN.getDefaultState());
				}
			}
		}
		if (v == 0) {
			for (int x = -4; x <= 4; x += 2) {
				for (int y = 4; y <= 8; y++) {
					b.set(x, y, -1, Blocks.SCAFFOLDING.getDefaultState());
				}
			}
			return;
		}
		// Lit once it stands, and lit again whenever the builders have made the frame whole.
		BlockPos low = b.world(-3, 2, 0);
		b.hook(0, 2, 0, (world, pos) -> light(world, low));
	}

	/** Fills the portal frame with fire, if the frame is whole. */
	public static void light(ServerWorld world, BlockPos low) {
		BlockState portal = Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, Direction.Axis.X);
		boolean whole = true;
		for (int x = -1; x <= 7; x++) {
			whole &= world.getBlockState(low.add(x, -1, 0)).isOf(Blocks.OBSIDIAN) && world.getBlockState(low.add(x, 6, 0)).isOf(Blocks.OBSIDIAN);
		}
		for (int y = 0; y < 6; y++) {
			whole &= world.getBlockState(low.add(-1, y, 0)).isOf(Blocks.OBSIDIAN) && world.getBlockState(low.add(7, y, 0)).isOf(Blocks.OBSIDIAN);
		}
		for (int x = 0; x < 7; x++) {
			for (int y = 0; y < 6; y++) {
				BlockPos pos = low.add(x, y, 0);
				if (!whole && world.getBlockState(pos).isOf(Blocks.NETHER_PORTAL)) {
					world.setBlockState(pos, Blocks.AIR.getDefaultState()); // the frame is broken: the fire goes out
				} else if (!world.getBlockState(pos).isAir() && !world.getBlockState(pos).isOf(Blocks.NETHER_PORTAL)) {
					return; // something in the way
				}
			}
		}
		if (!whole) {
			return;
		}
		for (int x = 0; x < 7; x++) {
			for (int y = 0; y < 6; y++) {
				world.setBlockState(low.add(x, y, 0), portal, net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE);
			}
		}
	}

	/** The quake crystal: a great amethyst grown out of a ring of dressed stone, souls caught inside it. */
	private static void crystal(Canvas.Local b, int v) {
		b.site(-3, -3, 3, 3, 10, Blocks.STONE.getDefaultState(), Blocks.POLISHED_DEEPSLATE.getDefaultState());
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if (Math.abs(x) == 3 || Math.abs(z) == 3) {
					b.set(x, 1, z, (x + z) % 2 == 0 ? Blocks.CHISELED_STONE_BRICKS.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState());
				}
			}
		}
		if (v == 0) {
			b.set(0, 1, 0, Blocks.BUDDING_AMETHYST.getDefaultState());
			return;
		}
		int[] widths = {2, 2, 1, 1, 1, 1, 0, 0, 0};
		for (int y = 1; y <= widths.length; y++) {
			int w = widths[y - 1];
			for (int x = -w; x <= w; x++) {
				for (int z = -w; z <= w; z++) {
					boolean face = Math.abs(x) == w || Math.abs(z) == w;
					b.set(x, y, z, face && (x + y + z) % 3 == 0 ? Blocks.TINTED_GLASS.getDefaultState() : face ? Blocks.AMETHYST_BLOCK.getDefaultState()
							: Blocks.CRYING_OBSIDIAN.getDefaultState());
				}
			}
		}
		b.set(0, widths.length + 1, 0, Blocks.AMETHYST_CLUSTER.getDefaultState());
		for (int[] c : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
			b.fill(c[0], 1, c[1], Blocks.LARGE_AMETHYST_BUD.getDefaultState());
		}
	}

	/** Where Morok rests between his wanderings, and where he wakes again: a dais and a great empty seat. */
	private static void seatOfMorok(Canvas.Local b, int v) {
		b.site(-4, -4, 4, 4, 9, Blocks.DEEPSLATE.getDefaultState(), Blocks.DEEPSLATE_TILES.getDefaultState());
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				if (Math.abs(x) == 4 || Math.abs(z) == 4) {
					b.set(x, 1, z, Blocks.POLISHED_DEEPSLATE.getDefaultState());
				}
			}
		}
		if (v == 0) {
			return;
		}
		// The seat: a block of chiselled deepslate with arms and a high back, lit by the souls in it.
		for (int x = -2; x <= 2; x++) {
			b.set(x, 1, 2, Blocks.CHISELED_DEEPSLATE.getDefaultState());
			b.set(x, 2, 3, Blocks.DEEPSLATE_BRICKS.getDefaultState());
			for (int y = 2; y <= 7; y++) {
				b.set(x, y, 4, Math.abs(x) == 2 ? Blocks.DEEPSLATE_BRICK_WALL.getDefaultState() : Blocks.CHISELED_DEEPSLATE.getDefaultState());
			}
		}
		b.set(-3, 2, 2, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.set(3, 2, 2, Blocks.DEEPSLATE_BRICKS.getDefaultState());
		b.set(0, 8, 4, Blocks.SCULK_CATALYST.getDefaultState());
		b.set(-4, 2, -4, Blocks.SOUL_LANTERN.getDefaultState());
		b.set(4, 2, -4, Blocks.SOUL_LANTERN.getDefaultState());
	}

	/** The Tower of Eternal Night, on its own cushion of amethyst: an obsidian spire with a black star at its top. */
	private static void tower(Canvas.Local b, int v) {
		// The cushion and the disc.
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				int d = x * x + z * z;
				if (d <= 36) {
					b.set(x, 0, z, d >= 30 ? Blocks.QUARTZ_BLOCK.getDefaultState() : Blocks.CALCITE.getDefaultState());
					for (int y = -1; y >= -4 + d / 12; y--) {
						b.set(x, y, z, (x + y + z) % 3 == 0 ? Blocks.BUDDING_AMETHYST.getDefaultState() : Blocks.AMETHYST_BLOCK.getDefaultState());
					}
				}
			}
		}
		int height = v == 0 ? 14 : 38;
		for (int y = 1; y <= height; y++) {
			int w = y < 6 ? 2 : 1;
			for (int x = -w; x <= w; x++) {
				for (int z = -w; z <= w; z++) {
					BlockState state = y % 6 == 0 ? Blocks.CRYING_OBSIDIAN.getDefaultState()
							: (Math.abs(x) == w && Math.abs(z) == w) ? Blocks.AMETHYST_BLOCK.getDefaultState() : Blocks.OBSIDIAN.getDefaultState();
					if (y % 9 == 4 && x == 0 && Math.abs(z) == w) {
						state = Blocks.TINTED_GLASS.getDefaultState();
					}
					b.set(x, y, z, state);
				}
			}
		}
		if (v == 0) {
			return;
		}
		// The black star: a sphere of black glass caught in end rods.
		for (int x = -3; x <= 3; x++) {
			for (int y = -3; y <= 3; y++) {
				for (int z = -3; z <= 3; z++) {
					int d = x * x + y * y + z * z;
					if (d <= 7) {
						b.set(x, height + 4 + y, z, d <= 2 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.BLACK_STAINED_GLASS.getDefaultState());
					}
				}
			}
		}
		for (Direction direction : Direction.values()) {
			BlockPos tip = new BlockPos(0, height + 4, 0).offset(direction, 4);
			b.set(tip.getX(), tip.getY(), tip.getZ(), Blocks.END_ROD.getDefaultState().with(net.minecraft.block.EndRodBlock.FACING, direction));
		}
	}

	/** Whether a work stands whole enough to work: a town broken by half or more has its great work broken too. */
	public static boolean working(Faction faction, Project project) {
		Settlement seat = Projects.seat(faction, project);
		return Projects.done(faction, project) && seat != null && seat.counter(Damage.DAMAGE) < 50;
	}
}
