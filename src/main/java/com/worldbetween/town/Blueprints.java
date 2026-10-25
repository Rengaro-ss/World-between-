package com.worldbetween.town;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.FactionType;
import com.worldbetween.item.Ownership;
import com.worldbetween.town.Canvas.Local;
import com.worldbetween.town.Canvas.Work;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.WallBannerBlock;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BannerPattern;
import net.minecraft.block.entity.BannerPatterns;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.function.Supplier;

/**
 * Neris architecture. Dark oak and black stone, bronze-coloured copper on the grander roofs, orange and
 * black banners with the forge-fire. Every building is drawn facing north in its own coordinates.
 */
public final class Blueprints {
	static final BlockState FOUNDATION = Blocks.COBBLED_DEEPSLATE.getDefaultState();
	static final BlockState PLANKS = Blocks.DARK_OAK_PLANKS.getDefaultState();
	static final BlockState FLOOR = Blocks.SPRUCE_PLANKS.getDefaultState();
	static final BlockState BRICK = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
	static final BlockState STONE = Blocks.STONE_BRICKS.getDefaultState();
	static final BlockState TILE = Blocks.DEEPSLATE_TILES.getDefaultState();
	static final BlockState POLISHED = Blocks.POLISHED_DEEPSLATE.getDefaultState();
	static final BlockState COPPER = Blocks.WAXED_CUT_COPPER.getDefaultState();
	static final BlockState AIR = Blocks.AIR.getDefaultState();

	private final Local l;
	private final FactionType owner;
	private final long seed;
	private final Palette pal;
	private final BlockState planks;
	private final BlockState brick;
	private final BlockState stone;
	private final BlockState tile;
	private final BlockState floor;

	private Blueprints(Local local, FactionType owner, long seed, Palette palette) {
		this.l = local;
		this.owner = owner;
		this.seed = seed;
		this.pal = palette;
		this.planks = palette.planks().getDefaultState();
		this.brick = palette.brick();
		this.stone = palette.stone();
		this.tile = palette.tile();
		this.floor = palette.floor();
	}

	/** Draws a plot building. */
	public static void building(Local local, FactionType owner, long seed, Palette palette, BuildingType type, int level) {
		Blueprints b = new Blueprints(local, owner, seed, palette);
		switch (type) {
			case HOUSE -> b.house(level);
			case FARM -> b.farm();
			case PASTURE -> b.pasture();
			case MILL -> b.mill();
			case BAKERY -> b.bakery();
			case KITCHEN -> b.kitchen();
			case LUMBER_CAMP -> b.lumberCamp();
			case SAWMILL -> b.sawmill();
			case QUARRY -> b.quarry();
			case MASON -> b.mason();
			case CHARCOAL_KILN -> b.kiln();
			case SMELTER -> b.smelter();
			case TANNERY -> b.tannery();
			case WEAVER -> b.weaver();
			case FORGE -> b.forge();
			case LIBRARY -> b.library();
			case BARRACKS -> b.barracks();
			case WAREHOUSE -> b.warehouse(level);
			default -> {
			}
		}
	}

	public static void townHall(Local local, FactionType owner, long seed, Palette palette, int tier, boolean capital) {
		Blueprints b = new Blueprints(local, owner, seed, palette);
		switch (tier) {
			case 0 -> b.tent();
			case 1 -> b.villageHall();
			case 2 -> b.fortHall();
			default -> {
				if (capital) {
					b.throneHall();
				} else {
					b.keep();
				}
			}
		}
	}

	/** Depth of the town hall, so the layout can set it back from the square. */
	public static int townHallDepth(int tier, boolean capital) {
		return switch (tier) {
			case 0 -> 5;
			case 1 -> 9;
			case 2 -> 11;
			default -> capital ? 19 : 13;
		};
	}

	public static void watchtower(Local local, FactionType owner, long seed, Palette palette, boolean masonry) {
		new Blueprints(local, owner, seed, palette).tower(masonry);
	}

	public static void stall(Local local, FactionType owner, long seed, Palette palette, int index) {
		new Blueprints(local, owner, seed, palette).marketStall(index);
	}

	public static void pier(Local local, FactionType owner, long seed, Palette palette, int length) {
		new Blueprints(local, owner, seed, palette).dock(length);
	}

	public static void plazaCenter(Local local, FactionType owner, long seed, Palette palette, int tier) {
		new Blueprints(local, owner, seed, palette).monument(tier);
	}

	// ---------------------------------------------------------------- houses

	private void house(int level) {
		switch (level) {
			case 0 -> hut();
			case 1 -> cottage();
			case 2 -> twoStorey();
			default -> townhouse();
		}
	}

	private void hut() {
		l.site(-2, -2, 2, 2, 6, FOUNDATION, floor);
		l.walls(-2, 1, -2, 2, 3, 2, planks);
		corners(-2, -2, 2, 2, 1, 3, log(Direction.Axis.Y));
		door(0, 1, -2);
		windowSide(-2, 2, 0);
		windowSide(2, 2, 0);
		windowFront(0, 2, 2);
		beam(-2, 2, 4, 0);
		gable(-2, 2, -2, 2, 4, pal.stairs(), pal.slab(), planks);
		bed(-1, 1, 0, Direction.SOUTH, Blocks.ORANGE_BED);
		chest(1, 1, 1, Direction.WEST, HOME);
		l.set(1, 1, -1, Blocks.CRAFTING_TABLE.getDefaultState());
		hanging(0, 3, 0);
	}

	private void cottage() {
		l.site(-3, -3, 3, 3, 7, FOUNDATION, floor);
		l.walls(-3, 1, -3, 3, 1, 3, FOUNDATION);
		l.walls(-3, 2, -3, 3, 4, 3, planks);
		corners(-3, -3, 3, 3, 1, 4, log(Direction.Axis.Y));
		for (int y = 2; y <= 4; y++) {
			l.set(0, y, 3, log(Direction.Axis.Y));
		}
		door(0, 1, -3);
		windowFront(-2, 3, -3);
		windowFront(2, 3, -3);
		windowSide(-3, 3, -1);
		windowSide(-3, 3, 1);
		windowSide(3, 3, -1);
		windowSide(3, 3, 1);
		windowFront(-2, 3, 3);
		windowFront(2, 3, 3);
		beam(-3, 3, 5, 0);
		gable(-3, 3, -3, 3, 5, pal.stairs(), pal.slab(), planks);
		bed(-2, 1, 1, Direction.SOUTH, Blocks.ORANGE_BED);
		bed(-1, 1, 1, Direction.SOUTH, Blocks.BLACK_BED);
		furnace(2, 1, 2, Direction.WEST, Blocks.FURNACE);
		barrel(2, 1, 1, HOME);
		l.set(2, 1, -2, Blocks.CRAFTING_TABLE.getDefaultState());
		chest(-2, 1, -2, Direction.EAST, HOME);
		hanging(0, 4, 0);
		chimney(2, 2, 2, 9);
	}

	private void twoStorey() {
		l.site(-3, -4, 3, 4, 10, FOUNDATION, floor);
		l.walls(-3, 1, -4, 3, 3, 4, stone);
		l.box(-2, 4, -3, 2, 4, 3, floor);
		l.walls(-3, 4, -4, 3, 4, 4, log(Direction.Axis.Y));
		l.walls(-3, 5, -4, 3, 7, 4, planks);
		corners(-3, -4, 3, 4, 1, 7, log(Direction.Axis.Y));
		door(0, 1, -4);
		for (int x : new int[]{-2, 2}) {
			windowFront(x, 2, -4);
			windowFront(x, 6, -4);
			windowFront(x, 6, 4);
		}
		for (int z : new int[]{-2, 1}) {
			windowSide(-3, 2, z);
			windowSide(3, 2, z);
			windowSide(-3, 6, z);
			windowSide(3, 6, z);
		}
		beam(-3, 3, 8, 0);
		gable(-3, 3, -4, 3, 8, pal.stairs(), pal.slab(), planks);
		ladder(-2, 1, 3, 4, Direction.NORTH);
		l.set(-2, 4, 3, AIR);
		furnace(2, 1, 3, Direction.WEST, Blocks.SMOKER);
		barrel(2, 1, 2, HOME);
		l.set(2, 1, -3, Blocks.CRAFTING_TABLE.getDefaultState());
		chest(-2, 1, -3, Direction.EAST, HOME);
		hanging(0, 3, 0);
		bed(-2, 5, -2, Direction.SOUTH, Blocks.ORANGE_BED);
		bed(0, 5, 1, Direction.SOUTH, Blocks.BLACK_BED);
		bed(2, 5, 1, Direction.SOUTH, Blocks.ORANGE_BED);
		chest(2, 5, -3, Direction.WEST, HOME);
		hanging(0, 7, 0);
		chimney(2, 3, 2, 12);
	}

	private void townhouse() {
		l.site(-4, -4, 4, 4, 14, FOUNDATION, POLISHED);
		l.walls(-4, 1, -4, 4, 4, 4, brick);
		l.walls(-4, 5, -4, 4, 8, 4, stone);
		l.walls(-4, 9, -4, 4, 11, 4, planks);
		corners(-4, -4, 4, 4, 1, 11, Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState());
		l.box(-3, 5, -3, 3, 5, 3, floor);
		l.box(-3, 9, -3, 3, 9, 3, floor);
		door(0, 1, -4);
		for (int y : new int[]{2, 6, 10}) {
			for (int x : new int[]{-2, 2}) {
				windowFront(x, y, -4);
				windowFront(x, y, 4);
			}
			windowSide(-4, y, 0);
			windowSide(4, y, 0);
		}
		wallBanner(-1, 3, -5, Direction.NORTH);
		wallBanner(1, 3, -5, Direction.NORTH);
		beam(-4, 4, 12, 0);
		gable(-4, 4, -4, 4, 12, Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_CUT_COPPER_SLAB, COPPER);
		ladder(-3, 1, 3, 9, Direction.NORTH);
		l.set(-3, 5, 3, AIR);
		l.set(-3, 9, 3, AIR);
		furnace(3, 1, 3, Direction.WEST, Blocks.SMOKER);
		furnace(3, 1, 2, Direction.WEST, Blocks.FURNACE);
		barrel(3, 1, -3, HOME);
		chest(-3, 1, -3, Direction.EAST, HOME);
		l.set(0, 1, 2, Blocks.CRAFTING_TABLE.getDefaultState());
		hanging(0, 4, 0);
		for (int x : new int[]{-2, 0, 2}) {
			bed(x, 6, 1, Direction.SOUTH, x == 0 ? Blocks.BLACK_BED : Blocks.ORANGE_BED);
		}
		chest(3, 6, -3, Direction.WEST, HOME);
		hanging(0, 8, 0);
		bed(-2, 10, 1, Direction.SOUTH, Blocks.ORANGE_BED);
		bed(2, 10, 1, Direction.SOUTH, Blocks.ORANGE_BED);
		l.set(0, 10, 2, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, Direction.NORTH));
		hanging(0, 11, 0);
		chimney(3, 3, 2, 16);
	}

	// ---------------------------------------------------------------- town halls

	private void tent() {
		l.site(-2, -2, 2, 2, 4, FOUNDATION, Blocks.COARSE_DIRT.getDefaultState());
		for (int z = -2; z <= 2; z++) {
			BlockState cloth = (z % 2 == 0 ? Blocks.ORANGE_WOOL : Blocks.BLACK_WOOL).getDefaultState();
			l.set(-2, 1, z, cloth);
			l.set(2, 1, z, cloth);
			l.set(-1, 2, z, cloth);
			l.set(1, 2, z, cloth);
			l.set(0, 3, z, cloth);
		}
		l.box(-1, 1, 2, 1, 1, 2, Blocks.BLACK_WOOL.getDefaultState());
		l.set(0, 2, 2, Blocks.BLACK_WOOL.getDefaultState());
		l.set(-1, 1, 1, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
		chest(1, 1, 1, Direction.WEST, SUPPLIES);
		l.set(0, 1, 0, Blocks.ORANGE_CARPET.getDefaultState());
		campfire(0, 1, -4, false);
		l.set(2, 1, -3, fence());
		l.set(2, 2, -3, fence());
		standingBanner(2, 3, -3, 8);
		l.work(0, 1, -4, Work.KITCHEN);
	}

	private void villageHall() {
		l.site(-4, -4, 4, 4, 8, FOUNDATION, floor);
		l.walls(-4, 1, -4, 4, 1, 4, FOUNDATION);
		l.walls(-4, 2, -4, 4, 4, 4, planks);
		corners(-4, -4, 4, 4, 1, 4, log(Direction.Axis.Y));
		for (int y = 1; y <= 4; y++) {
			l.set(-2, y, -4, log(Direction.Axis.Y));
			l.set(2, y, -4, log(Direction.Axis.Y));
		}
		door(0, 1, -4);
		windowFront(-3, 3, -4);
		windowFront(3, 3, -4);
		for (int z : new int[]{-2, 0, 2}) {
			windowSide(-4, 3, z);
			windowSide(4, 3, z);
		}
		wallBanner(-1, 3, -5, Direction.NORTH);
		wallBanner(1, 3, -5, Direction.NORTH);
		beam(-4, 4, 5, 0);
		beam(-4, 4, 5, -2);
		beam(-4, 4, 5, 2);
		gable(-4, 4, -4, 4, 5, pal.stairs(), pal.slab(), planks);
		table(0, 1, -1, 0, 1, 2);
		lectern(0, 1, 3, Direction.NORTH, false);
		chest(-3, 1, 3, Direction.EAST, SUPPLIES);
		chest(3, 1, 3, Direction.WEST, SUPPLIES);
		l.box(0, 1, -3, 0, 1, -2, Blocks.ORANGE_CARPET.getDefaultState());
		hanging(0, 4, -2);
		hanging(0, 4, 2);
		l.work(0, 1, 2, Work.BOOKS);
	}

	private void fortHall() {
		l.site(-5, -5, 5, 5, 9, FOUNDATION, POLISHED);
		l.walls(-5, 1, -5, 5, 5, 5, stone);
		for (int[] c : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
			for (int y = 1; y <= 7; y++) {
				l.set(c[0], y, c[1], brick);
			}
		}
		l.box(-4, 6, -4, 4, 6, 4, tile);
		battlements(-5, -5, 5, 5, 6);
		door(0, 1, -5);
		l.set(-1, 1, -5, brick);
		l.set(1, 1, -5, brick);
		for (int x : new int[]{-3, 3}) {
			windowFront(x, 3, -5);
			windowFront(x, 3, 5);
		}
		for (int z : new int[]{-2, 2}) {
			windowSide(-5, 3, z);
			windowSide(5, 3, z);
		}
		wallBanner(-2, 4, -6, Direction.NORTH);
		wallBanner(2, 4, -6, Direction.NORTH);
		table(0, 1, -1, 0, 1, 2);
		lectern(0, 1, 4, Direction.NORTH, false);
		l.box(0, 1, -4, 0, 1, -2, Blocks.ORANGE_CARPET.getDefaultState());
		chest(-4, 1, 4, Direction.EAST, SUPPLIES);
		chest(4, 1, 4, Direction.WEST, SUPPLIES);
		wallBanner(-2, 3, 4, Direction.NORTH);
		wallBanner(2, 3, 4, Direction.NORTH);
		hanging(-2, 5, 0);
		hanging(2, 5, 0);
		l.work(0, 1, 3, Work.BOOKS);
	}

	private void keep() {
		l.site(-6, -6, 6, 6, 13, FOUNDATION, POLISHED);
		l.walls(-6, 1, -6, 6, 9, 6, brick);
		l.box(-5, 5, -5, 5, 5, 5, floor);
		l.box(-5, 10, -5, 5, 10, 5, tile);
		battlements(-6, -6, 6, 6, 10);
		corneredTowers(6, 12);
		door(0, 1, -6);
		for (int y : new int[]{3, 7}) {
			for (int x : new int[]{-3, 3}) {
				windowFront(x, y, -6);
				windowFront(x, y, 6);
			}
			for (int z : new int[]{-3, 3}) {
				windowSide(-6, y, z);
				windowSide(6, y, z);
			}
		}
		wallBanner(-2, 4, -7, Direction.NORTH);
		wallBanner(2, 4, -7, Direction.NORTH);
		wallBanner(0, 8, -7, Direction.NORTH);
		table(0, 1, -2, 0, 1, 3);
		lectern(0, 1, 5, Direction.NORTH, false);
		l.box(0, 1, -5, 0, 1, -3, Blocks.ORANGE_CARPET.getDefaultState());
		ladder(-5, 1, 5, 9, Direction.NORTH);
		l.set(-5, 5, 5, AIR);
		l.set(-5, 10, 5, AIR);
		for (int x : new int[]{-3, 0, 3}) {
			bed(x, 6, 2, Direction.SOUTH, x == 0 ? Blocks.BLACK_BED : Blocks.ORANGE_BED);
		}
		chest(5, 6, -5, Direction.WEST, SUPPLIES);
		hanging(0, 4, 0);
		hanging(0, 9, 0);
		l.work(0, 1, 4, Work.BOOKS);
	}

	/** The capital's keep: a long hall of black stone with the throne at its end. */
	private void throneHall() {
		l.site(-6, -9, 6, 9, 13, FOUNDATION, POLISHED);
		l.walls(-6, 1, -9, 6, 9, 9, brick);
		l.box(-5, 10, -8, 5, 10, 8, tile);
		battlements(-6, -9, 6, 9, 10);
		for (int[] c : new int[][]{{-6, -9}, {6, -9}, {-6, 9}, {6, 9}}) {
			l.box(c[0] - 1, 1, c[1] - 1, c[0] + 1, 12, c[1] + 1, brick);
			l.box(c[0] - 1, 13, c[1] - 1, c[0] + 1, 13, c[1] + 1, COPPER);
		}
		for (int y = 1; y <= 3; y++) {
			l.set(-1, y, -9, y == 3 ? brick : AIR);
			l.set(0, y, -9, y == 3 ? brick : AIR);
			l.set(1, y, -9, y == 3 ? brick : AIR);
		}
		doorPair(-1, 1, -9);
		for (int z = -6; z <= 6; z += 4) {
			for (int x : new int[]{-4, 4}) {
				for (int y = 1; y <= 9; y++) {
					l.set(x, y, z, y % 3 == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState() : brick);
				}
			}
			for (int y = 4; y <= 7; y++) {
				windowSide(-6, y, z + 2);
				windowSide(6, y, z + 2);
			}
		}
		for (int z = -8; z <= 5; z++) {
			l.set(0, 1, z, Blocks.ORANGE_CARPET.getDefaultState());
			l.set(-1, 1, z, Blocks.BLACK_CARPET.getDefaultState());
			l.set(1, 1, z, Blocks.BLACK_CARPET.getDefaultState());
		}
		l.box(-3, 1, 6, 3, 1, 8, POLISHED);
		l.box(-2, 2, 7, 2, 2, 8, POLISHED);
		l.set(0, 3, 8, Blocks.POLISHED_BLACKSTONE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
		l.set(-1, 3, 8, Blocks.GOLD_BLOCK.getDefaultState());
		l.set(1, 3, 8, Blocks.GOLD_BLOCK.getDefaultState());
		l.set(0, 4, 8, COPPER);
		l.set(0, 5, 8, Blocks.WAXED_COPPER_BLOCK.getDefaultState());
		for (int x : new int[]{-3, -2, 2, 3}) {
			wallBanner(x, 6, 8, Direction.NORTH);
		}
		wallBanner(0, 7, 8, Direction.NORTH);
		for (int x : new int[]{-3, 3}) {
			l.set(x, 2, 7, Blocks.LANTERN.getDefaultState());
		}
		for (int z = -6; z <= 6; z += 4) {
			chandelier(0, 9, z);
		}
		wallBanner(-3, 6, -10, Direction.NORTH);
		wallBanner(3, 6, -10, Direction.NORTH);
		standingBanner(-2, 1, -11, 8);
		standingBanner(2, 1, -11, 8);
		chest(-5, 1, 8, Direction.EAST, TREASURY);
		chest(5, 1, 8, Direction.WEST, TREASURY);
		l.work(0, 3, 7, Work.BOOKS);
	}

	// ---------------------------------------------------------------- food

	private void farm() {
		l.site(-4, -4, 4, 4, 2, FOUNDATION, Blocks.DIRT.getDefaultState());
		Random random = Random.create(seed);
		Block crop = List.of(Blocks.WHEAT, Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS).get(random.nextInt(5));
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				if (x == 0) {
					l.set(x, 0, z, Blocks.WATER.getDefaultState());
					continue;
				}
				l.set(x, 0, z, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
				BlockState plant = crop.getDefaultState();
				if (crop instanceof CropBlock cropBlock) {
					plant = cropBlock.withAge(random.nextInt(cropBlock.getMaxAge() + 1));
				}
				l.set(x, 1, z, plant);
			}
		}
		l.set(0, 0, -4, pal.slab().getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
		l.set(5, 1, -5, fence());
		l.set(5, 2, -5, Blocks.HAY_BLOCK.getDefaultState());
		l.set(5, 3, -5, Blocks.PUMPKIN.getDefaultState());
		l.set(-5, 1, -5, Blocks.COMPOSTER.getDefaultState());
		l.work(-2, 1, 0, Work.FIELD);
		l.work(2, 1, 0, Work.FIELD);
	}

	private void pasture() {
		l.site(-4, -4, 4, 4, 4, FOUNDATION, Blocks.GRASS_BLOCK.getDefaultState());
		for (int x = -4; x <= 4; x++) {
			l.set(x, 1, -4, fence(Direction.Axis.X));
			l.set(x, 1, 4, fence(Direction.Axis.X));
		}
		for (int z = -3; z <= 3; z++) {
			l.set(-4, 1, z, fence(Direction.Axis.Z));
			l.set(4, 1, z, fence(Direction.Axis.Z));
		}
		l.set(0, 1, -4, pal.gate().getDefaultState().with(FenceGateBlock.FACING, Direction.NORTH));
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				l.set(x, 3, z, pal.slab().getDefaultState());
			}
		}
		l.set(1, 1, 3, log(Direction.Axis.Y));
		l.set(1, 2, 3, log(Direction.Axis.Y));
		l.set(3, 1, 1, log(Direction.Axis.Y));
		l.set(3, 2, 1, log(Direction.Axis.Y));
		l.set(3, 1, 3, Blocks.HAY_BLOCK.getDefaultState());
		l.set(2, 1, 3, Blocks.HAY_BLOCK.getDefaultState());
		l.set(-3, 1, 3, Blocks.WATER_CAULDRON.getDefaultState().with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3));
		EntityType<? extends MobEntity> animal = List.<EntityType<? extends MobEntity>>of(
				EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN).get(Math.floorMod((int) seed, 4));
		l.hookOnce(-1, 1, 0, (world, pos) -> {
			for (int i = 0; i < 3; i++) {
				MobEntity mob = animal.create(world);
				if (mob != null) {
					mob.refreshPositionAndAngles(pos.getX() + 0.5 + i - 1, pos.getY(), pos.getZ() + 0.5, i * 90f, 0f);
					mob.setPersistent();
					world.spawnEntity(mob);
				}
			}
		});
		l.work(0, 1, 0, Work.ANIMALS);
	}

	private void mill() {
		l.site(-2, -2, 2, 2, 11, FOUNDATION, floor);
		l.walls(-2, 1, -2, 2, 2, 2, stone);
		l.walls(-2, 3, -2, 2, 8, 2, planks);
		corners(-2, -2, 2, 2, 3, 8, log(Direction.Axis.Y));
		door(0, 1, -2);
		windowSide(-2, 5, 0);
		windowSide(2, 5, 0);
		gable(-2, 2, -2, 2, 9, pal.stairs(), pal.slab(), planks);
		// The sails: a hub on the front with four arms of fence and cloth.
		l.set(0, 6, -3, log(Direction.Axis.Z));
		for (int i = 1; i <= 4; i++) {
			l.set(0, 6 + i, -4, fence());
			l.set(0, 6 - i, -4, i < 4 ? fence() : AIR);
			l.set(i, 6, -4, fence(Direction.Axis.X));
			l.set(-i, 6, -4, fence(Direction.Axis.X));
			if (i > 1) {
				l.set(1, 6 + i, -4, Blocks.WHITE_WOOL.getDefaultState());
				l.set(-1, 6 - i, -4, i < 4 ? Blocks.WHITE_WOOL.getDefaultState() : AIR);
				l.set(i, 5, -4, Blocks.WHITE_WOOL.getDefaultState());
				l.set(-i, 7, -4, Blocks.WHITE_WOOL.getDefaultState());
			}
		}
		l.set(0, 6, -4, log(Direction.Axis.Z));
		l.set(0, 1, 1, Blocks.GRINDSTONE.getDefaultState().with(GrindstoneBlock.FACE, BlockFace.FLOOR));
		l.set(-1, 1, 1, Blocks.HAY_BLOCK.getDefaultState());
		barrel(1, 1, 1, MILL_GOODS);
		hanging(0, 7, 0);
		l.work(0, 1, 1, Work.GRIND);
	}

	private void bakery() {
		l.site(-3, -3, 3, 3, 7, FOUNDATION, floor);
		l.walls(-3, 1, -3, 3, 4, 3, stone);
		corners(-3, -3, 3, 3, 1, 4, log(Direction.Axis.Y));
		door(0, 1, -3);
		windowFront(-2, 2, -3);
		windowFront(2, 2, -3);
		windowSide(-3, 2, 0);
		beam(-3, 3, 5, 0);
		gable(-3, 3, -3, 3, 5, pal.tileStairs(), pal.tileSlab(), stone);
		// A brick oven against the back wall.
		l.box(-1, 1, 2, 2, 3, 2, Blocks.BRICKS.getDefaultState());
		furnace(0, 1, 1, Direction.NORTH, Blocks.FURNACE);
		furnace(1, 1, 1, Direction.NORTH, Blocks.FURNACE);
		l.set(0, 2, 1, Blocks.BRICKS.getDefaultState());
		l.set(1, 2, 1, Blocks.BRICKS.getDefaultState());
		barrel(-2, 1, 2, BAKERY_GOODS);
		barrel(-2, 1, 1, BAKERY_GOODS);
		l.set(2, 1, -2, Blocks.HAY_BLOCK.getDefaultState());
		table(-1, 1, -1, 0, 1, -1);
		hanging(0, 4, 0);
		chimney(1, 2, 4, 9);
		l.work(0, 1, 1, Work.FURNACE);
		l.work(1, 1, 1, Work.FURNACE);
	}

	private void kitchen() {
		l.site(-3, -3, 3, 3, 7, FOUNDATION, POLISHED);
		l.walls(-3, 1, -3, 3, 1, 3, FOUNDATION);
		l.walls(-3, 2, -3, 3, 4, 3, planks);
		corners(-3, -3, 3, 3, 1, 4, log(Direction.Axis.Y));
		door(0, 1, -3);
		windowFront(-2, 3, -3);
		windowFront(2, 3, -3);
		windowSide(-3, 3, 0);
		windowSide(3, 3, 0);
		beam(-3, 3, 5, 0);
		gable(-3, 3, -3, 3, 5, pal.stairs(), pal.slab(), planks);
		furnace(-2, 1, 2, Direction.NORTH, Blocks.SMOKER);
		furnace(-1, 1, 2, Direction.NORTH, Blocks.SMOKER);
		l.set(1, 1, 2, Blocks.WATER_CAULDRON.getDefaultState().with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3));
		barrel(2, 1, 2, KITCHEN_GOODS);
		barrel(2, 1, 1, KITCHEN_GOODS);
		table(-1, 1, -1, 1, 1, -1);
		hanging(0, 4, 0);
		chimney(-2, 2, 2, 9);
		l.work(-2, 1, 2, Work.KITCHEN);
		l.work(-1, 1, 2, Work.FURNACE);
	}

	private void dock(int length) {
		for (int i = 0; i < length; i++) {
			for (int x = -1; x <= 1; x++) {
				l.set(x, 0, -i, planks);
				l.clear(x, 1, -i);
				l.clear(x, 2, -i);
			}
			if (i % 4 == 0) {
				for (int x : new int[]{-1, 1}) {
					l.set(x, 1, -i, fence());
					for (int y = -1; y >= -8; y--) {
						l.fill(x, y, -i, log(Direction.Axis.Y));
					}
				}
			}
		}
		l.set(1, 2, -(length - 1), Blocks.LANTERN.getDefaultState());
		barrel(-1, 1, -2, FISH);
		barrel(-1, 1, -3, FISH);
		l.hookOnce(2, 1, -(length - 2), (world, pos) -> {
			BoatEntity boat = EntityType.BOAT.create(world);
			if (boat != null) {
				boat.setVariant(BoatEntity.Type.DARK_OAK);
				boat.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
				world.spawnEntity(boat);
			}
		});
		l.work(0, 1, -(length - 1), Work.FISHING);
	}

	// ---------------------------------------------------------------- wood and stone

	private void lumberCamp() {
		l.site(-4, -4, 4, 4, 5, FOUNDATION, Blocks.COARSE_DIRT.getDefaultState());
		for (int[] p : new int[][]{{1, 2}, {4, 2}, {1, 4}, {4, 4}}) {
			l.set(p[0], 1, p[1], log(Direction.Axis.Y));
			l.set(p[0], 2, p[1], log(Direction.Axis.Y));
		}
		l.box(1, 3, 2, 4, 3, 4, pal.slab().getDefaultState());
		chest(3, 1, 4, Direction.NORTH, LUMBER);
		for (int x = -4; x <= -1; x++) {
			for (int y = 1; y <= 2; y++) {
				l.set(x, y, 3, Blocks.DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
				l.set(x, y, 4, Blocks.SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
			}
		}
		l.set(-1, 1, -1, pal.log().getDefaultState());
		l.set(-3, 1, -2, pal.log().getDefaultState());
		l.work(-1, 2, -1, Work.AXE);
		l.work(-3, 2, -2, Work.AXE);
	}

	private void sawmill() {
		l.site(-3, -4, 3, 4, 6, FOUNDATION, floor);
		for (int[] p : new int[][]{{-3, -4}, {3, -4}, {-3, 4}, {3, 4}, {-3, 0}, {3, 0}}) {
			for (int y = 1; y <= 3; y++) {
				l.set(p[0], y, p[1], log(Direction.Axis.Y));
			}
		}
		l.walls(-3, 1, 4, 3, 3, 4, planks);
		gable(-3, 3, -4, 4, 4, pal.stairs(), pal.slab(), planks);
		l.set(0, 1, 0, Blocks.STONECUTTER.getDefaultState().with(StonecutterBlock.FACING, Direction.NORTH));
		for (int z = -3; z <= 2; z++) {
			l.set(-2, 1, z, Blocks.DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z));
		}
		for (int z = -3; z <= 2; z++) {
			l.set(2, 1, z, planks);
			if (z % 2 == 0) {
				l.set(2, 2, z, pal.slab().getDefaultState());
			}
		}
		chest(0, 1, 3, Direction.NORTH, SAWMILL_GOODS);
		hanging(0, 4, 0);
		l.work(0, 1, 0, Work.SAW);
	}

	private void quarry() {
		l.site(-4, -4, 4, 4, 4, FOUNDATION, Blocks.GRAVEL.getDefaultState());
		for (int x = -1; x <= 1; x++) {
			for (int z = 0; z <= 2; z++) {
				l.set(x, 0, z, AIR);
				l.set(x, -1, z, AIR);
				l.set(x, -2, z, Blocks.STONE.getDefaultState());
			}
		}
		l.box(-4, 1, 2, -3, 2, 4, Blocks.COBBLESTONE.getDefaultState());
		l.box(3, 1, 3, 4, 1, 4, Blocks.STONE.getDefaultState());
		l.set(3, 1, -3, Blocks.STONECUTTER.getDefaultState());
		chest(4, 1, -3, Direction.WEST, QUARRY_GOODS);
		l.set(2, 1, 0, fence());
		l.set(2, 2, 0, fence());
		l.set(2, 3, 0, log(Direction.Axis.X));
		l.set(1, 3, 0, log(Direction.Axis.X));
		l.set(0, 3, 0, Blocks.CHAIN.getDefaultState());
		l.set(0, 2, 0, Blocks.CHAIN.getDefaultState());
		l.work(0, -1, 1, Work.DRILL);
		l.work(3, 1, -3, Work.CHISEL);
	}

	private void mason() {
		l.site(-3, -3, 3, 3, 5, FOUNDATION, Blocks.COBBLESTONE.getDefaultState());
		for (int[] p : new int[][]{{-3, 0}, {3, 0}, {-3, 3}, {3, 3}}) {
			for (int y = 1; y <= 3; y++) {
				l.set(p[0], y, p[1], stone);
			}
		}
		l.box(-3, 4, 0, 3, 4, 3, Blocks.STONE_BRICK_SLAB.getDefaultState());
		l.set(-1, 1, 1, Blocks.STONECUTTER.getDefaultState());
		l.set(1, 1, 1, Blocks.STONECUTTER.getDefaultState());
		l.box(-3, 1, -3, -2, 2, -2, stone);
		l.box(2, 1, -3, 3, 1, -2, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
		chest(0, 1, 3, Direction.NORTH, MASON_GOODS);
		l.work(-1, 1, 1, Work.CHISEL);
		l.work(1, 1, 1, Work.CHISEL);
	}

	private void kiln() {
		l.site(-2, -2, 2, 2, 6, FOUNDATION, Blocks.COARSE_DIRT.getDefaultState());
		l.walls(-2, 1, -2, 2, 2, 2, Blocks.BRICKS.getDefaultState());
		l.walls(-1, 3, -1, 1, 3, 1, Blocks.BRICKS.getDefaultState());
		l.box(-1, 1, -1, 1, 2, 1, Blocks.COAL_BLOCK.getDefaultState());
		l.set(0, 1, -2, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
		l.set(0, 3, 0, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
		for (int z = -4; z <= -3; z++) {
			for (int x = 2; x <= 4; x++) {
				l.set(x, 1, z, Blocks.DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z));
			}
		}
		chest(-4, 1, -3, Direction.EAST, KILN_GOODS);
		l.work(0, 4, 0, Work.KILN);
	}

	// ---------------------------------------------------------------- metal and crafts

	private void smelter() {
		l.site(-3, -3, 3, 3, 7, FOUNDATION, POLISHED);
		l.walls(-3, 1, -3, 3, 4, 3, brick);
		for (int x = -2; x <= 2; x++) {
			l.set(x, 1, -3, AIR);
			l.set(x, 2, -3, AIR);
		}
		l.box(-3, 5, -3, 3, 5, 3, tile);
		for (int x : new int[]{-2, 0, 2}) {
			furnace(x, 1, 2, Direction.NORTH, Blocks.BLAST_FURNACE);
			l.work(x, 1, 2, Work.FURNACE);
		}
		l.box(-2, 1, -1, -2, 1, 0, Blocks.RAW_IRON_BLOCK.getDefaultState());
		l.set(2, 1, -1, Blocks.COAL_BLOCK.getDefaultState());
		chest(2, 1, 0, Direction.WEST, SMELTER_GOODS);
		hanging(0, 4, 0);
		chimney(0, 0, 6, 10);
	}

	private void forge() {
		l.site(-3, -4, 3, 4, 7, FOUNDATION, POLISHED);
		l.walls(-3, 1, -1, 3, 4, 4, stone);
		for (int x = -2; x <= 2; x++) {
			for (int y = 1; y <= 3; y++) {
				l.set(x, y, -1, AIR);
			}
		}
		for (int[] p : new int[][]{{-3, -4}, {3, -4}}) {
			for (int y = 1; y <= 4; y++) {
				l.set(p[0], y, p[1], log(Direction.Axis.Y));
			}
		}
		l.box(-3, 5, -4, 3, 5, 4, tile);
		l.set(0, 1, -3, Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, Direction.EAST));
		l.set(-2, 1, -3, Blocks.GRINDSTONE.getDefaultState().with(GrindstoneBlock.FACE, BlockFace.FLOOR));
		furnace(-2, 1, 3, Direction.NORTH, Blocks.BLAST_FURNACE);
		l.set(0, 1, 3, Blocks.LAVA_CAULDRON.getDefaultState());
		l.set(2, 1, 3, Blocks.SMITHING_TABLE.getDefaultState());
		chest(2, 1, 1, Direction.WEST, FORGE_GOODS);
		l.set(-2, 1, 1, Blocks.WATER_CAULDRON.getDefaultState().with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3));
		hanging(0, 4, 1);
		chimney(0, 3, 6, 11);
		l.work(0, 1, -3, Work.ANVIL);
		l.work(-2, 1, 3, Work.FURNACE);
		l.work(0, 1, 3, Work.CHIMNEY);
	}

	private void tannery() {
		l.site(-3, -3, 3, 3, 5, FOUNDATION, Blocks.COARSE_DIRT.getDefaultState());
		for (int[] p : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			for (int y = 1; y <= 3; y++) {
				l.set(p[0], y, p[1], log(Direction.Axis.Y));
			}
		}
		l.box(-3, 4, -3, 3, 4, 3, pal.slab().getDefaultState());
		for (int x = -2; x <= 2; x += 2) {
			l.set(x, 1, 2, Blocks.WATER_CAULDRON.getDefaultState().with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3));
		}
		for (int y = 1; y <= 3; y++) {
			l.set(-3, y, 0, Blocks.BROWN_WOOL.getDefaultState());
			l.set(3, y, 0, Blocks.BROWN_WOOL.getDefaultState());
		}
		barrel(2, 1, -2, TANNERY_GOODS);
		l.set(-2, 1, -2, Blocks.COMPOSTER.getDefaultState());
		l.work(0, 1, 2, Work.LEATHER);
	}

	private void weaver() {
		l.site(-3, -3, 3, 3, 7, FOUNDATION, floor);
		l.walls(-3, 1, -3, 3, 4, 3, planks);
		corners(-3, -3, 3, 3, 1, 4, log(Direction.Axis.Y));
		door(0, 1, -3);
		windowFront(-2, 2, -3);
		windowFront(2, 2, -3);
		windowSide(-3, 2, 0);
		windowSide(3, 2, 0);
		beam(-3, 3, 5, 0);
		gable(-3, 3, -3, 3, 5, pal.stairs(), pal.slab(), planks);
		for (int x = -2; x <= 2; x += 2) {
			l.set(x, 1, 2, Blocks.LOOM.getDefaultState().with(LoomBlock.FACING, Direction.NORTH));
			l.work(x, 1, 2, Work.LOOM);
		}
		l.set(-2, 1, -2, Blocks.WHITE_WOOL.getDefaultState());
		l.set(-2, 2, -2, Blocks.ORANGE_WOOL.getDefaultState());
		l.set(-2, 1, -1, Blocks.BLACK_WOOL.getDefaultState());
		chest(2, 1, -2, Direction.WEST, WEAVER_GOODS);
		hanging(0, 4, 0);
	}

	private void library() {
		l.site(-4, -4, 4, 4, 9, FOUNDATION, POLISHED);
		l.walls(-4, 1, -4, 4, 1, 4, brick);
		l.walls(-4, 2, -4, 4, 5, 4, stone);
		corners(-4, -4, 4, 4, 1, 5, brick);
		door(0, 1, -4);
		for (int x : new int[]{-2, 2}) {
			windowFront(x, 3, -4);
			windowFront(x, 4, -4);
		}
		beam(-4, 4, 6, 0);
		gable(-4, 4, -4, 4, 6, pal.tileStairs(), pal.tileSlab(), stone);
		for (int y = 1; y <= 3; y++) {
			for (int z = -3; z <= 3; z++) {
				l.set(-3, y, z, Blocks.BOOKSHELF.getDefaultState());
				l.set(3, y, z, Blocks.BOOKSHELF.getDefaultState());
			}
			for (int x = -2; x <= 2; x++) {
				l.set(x, y, 3, Blocks.BOOKSHELF.getDefaultState());
			}
		}
		lectern(0, 1, 1, Direction.NORTH, true);
		lectern(-2, 1, -2, Direction.EAST, false);
		l.set(2, 1, -2, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
		chest(2, 1, -1, Direction.WEST, LIBRARY_GOODS);
		l.set(-1, 1, 1, Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, true));
		hanging(0, 5, 0);
		hanging(0, 5, -2);
		wallBanner(0, 4, -5, Direction.NORTH);
		l.work(0, 1, 0, Work.BOOKS);
	}

	private void barracks() {
		l.site(-4, -5, 4, 5, 8, FOUNDATION, POLISHED);
		l.walls(-4, 1, -5, 4, 4, 5, stone);
		corners(-4, -5, 4, 5, 1, 4, brick);
		l.box(-4, 5, -5, 4, 5, 5, tile);
		battlements(-4, -5, 4, 5, 5);
		door(0, 1, -5);
		for (int z : new int[]{-3, 0, 3}) {
			windowSide(-4, 3, z);
			windowSide(4, 3, z);
		}
		for (int z = -3; z <= 3; z += 2) {
			bed(-3, 1, z, Direction.EAST, Blocks.ORANGE_BED);
			bed(3, 1, z, Direction.WEST, Blocks.BLACK_BED);
		}
		chest(-3, 1, 4, Direction.EAST, BARRACKS_GOODS);
		chest(3, 1, 4, Direction.WEST, BARRACKS_GOODS);
		l.set(0, 1, 4, Blocks.TARGET.getDefaultState());
		l.set(0, 1, 1, fence());
		l.set(0, 2, 1, Blocks.HAY_BLOCK.getDefaultState());
		l.set(0, 3, 1, Blocks.PUMPKIN.getDefaultState());
		wallBanner(-2, 3, -6, Direction.NORTH);
		wallBanner(2, 3, -6, Direction.NORTH);
		hanging(0, 4, -2);
		hanging(0, 4, 3);
		l.work(0, 1, 0, Work.ANVIL);
	}

	private void warehouse(int level) {
		int half = level == 0 ? 3 : 4;
		int depth = level == 0 ? 4 : 5;
		int height = level == 0 ? 5 : 7;
		l.site(-half, -depth, half, depth, height + 4, FOUNDATION, floor);
		l.walls(-half, 1, -depth, half, 1, depth, FOUNDATION);
		l.walls(-half, 2, -depth, half, height, depth, planks);
		corners(-half, -depth, half, depth, 1, height, log(Direction.Axis.Y));
		for (int x = -1; x <= 1; x++) {
			for (int y = 1; y <= 3; y++) {
				l.set(x, y, -depth, AIR);
			}
		}
		beam(-half, half, height + 1, 0);
		gable(-half, half, -depth, depth, height + 1, pal.stairs(), pal.slab(), planks);
		for (int z = -depth + 2; z < depth; z++) {
			barrel(-half + 1, 1, z, WAREHOUSE_GOODS);
			barrel(half - 1, 1, z, WAREHOUSE_GOODS);
			if (level > 0) {
				barrel(-half + 1, 2, z, WAREHOUSE_GOODS);
				barrel(half - 1, 2, z, WAREHOUSE_GOODS);
			}
		}
		chest(0, 1, depth - 1, Direction.NORTH, WAREHOUSE_GOODS);
		l.set(-1, 1, depth - 1, Blocks.HAY_BLOCK.getDefaultState());
		l.set(1, 1, depth - 1, Blocks.HAY_BLOCK.getDefaultState());
		hanging(0, height, -1);
		hanging(0, height, 2);
		l.work(0, 1, 0, Work.STORE);
	}

	// ---------------------------------------------------------------- civic

	private void tower(boolean masonry) {
		BlockState wall = masonry ? stone : planks;
		BlockState corner = masonry ? brick : log(Direction.Axis.Y);
		int top = masonry ? 8 : 6;
		l.site(-1, -1, 1, 1, top + 4, FOUNDATION, wall);
		l.walls(-1, 1, -1, 1, top - 1, 1, wall);
		corners(-1, -1, 1, 1, 1, top - 1, corner);
		l.set(0, 1, -1, AIR);
		l.set(0, 2, -1, AIR);
		ladder(0, 1, 0, top - 1, Direction.SOUTH);
		l.box(-2, top, -2, 2, top, 2, masonry ? stone : planks);
		l.set(0, top, 0, AIR);
		for (int i = -2; i <= 2; i++) {
			BlockState railX = masonry ? Blocks.STONE_BRICK_WALL.getDefaultState() : fence(Direction.Axis.X);
			BlockState railZ = masonry ? Blocks.STONE_BRICK_WALL.getDefaultState() : fence(Direction.Axis.Z);
			l.set(i, top + 1, -2, railX);
			l.set(i, top + 1, 2, railX);
			l.set(-2, top + 1, i, railZ);
			l.set(2, top + 1, i, railZ);
		}
		l.set(2, top + 2, 2, fence());
		l.set(2, top + 3, 2, fence());
		standingBanner(2, top + 4, 2, 8);
		l.set(-2, top + 2, -2, Blocks.LANTERN.getDefaultState());
	}

	private void marketStall(int index) {
		DyeColor[] colors = {DyeColor.ORANGE, DyeColor.WHITE, DyeColor.BLACK, DyeColor.YELLOW};
		BlockState roof = woolOf(colors[index % colors.length]);
		for (int[] p : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			l.set(p[0], 1, p[1], fence());
			l.set(p[0], 2, p[1], fence());
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				l.set(x, 3, z, roof);
			}
		}
		barrel(0, 1, -1, MARKET_GOODS.get(index % MARKET_GOODS.size()));
		chest(0, 1, 1, Direction.NORTH, MARKET_GOODS.get((index + 1) % MARKET_GOODS.size()));
		l.work(0, 1, 0, Work.TRADE);
	}

	private void monument(int tier) {
		switch (tier) {
			case 0 -> {
				campfire(0, 1, 0, false);
				for (int[] p : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
					l.set(p[0], 1, p[1], pal.log().getDefaultState().with(PillarBlock.AXIS,
							p[0] == 0 ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
			case 1 -> {
				l.walls(-1, 1, -1, 1, 1, 1, Blocks.COBBLESTONE.getDefaultState());
				l.set(0, 1, 0, Blocks.WATER.getDefaultState());
				l.set(0, 0, 0, Blocks.WATER.getDefaultState());
				for (int[] p : new int[][]{{-1, -1}, {1, 1}}) {
					l.set(p[0], 2, p[1], fence());
					l.set(p[0], 3, p[1], fence());
				}
				l.box(-1, 4, -1, 1, 4, 1, pal.slab().getDefaultState());
				hanging(0, 3, 0);
			}
			default -> {
				int r = tier >= 3 ? 2 : 1;
				l.box(-r, 1, -r, r, 1, r, brick);
				l.box(-r + 1, 2, -r + 1, r - 1, 2, r - 1, POLISHED);
				l.set(0, 3, 0, Blocks.ANVIL.getDefaultState());
				for (int[] p : new int[][]{{-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
					l.set(p[0], 2, p[1], Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
				}
				if (tier >= 3) {
					l.set(0, 4, 0, Blocks.WAXED_COPPER_BLOCK.getDefaultState());
					l.set(0, 5, 0, Blocks.WAXED_CUT_COPPER.getDefaultState());
					l.set(0, 6, 0, Blocks.LIGHTNING_ROD.getDefaultState());
				}
				l.work(0, 3, 0, Work.ANVIL);
			}
		}
	}

	// ---------------------------------------------------------------- pieces

	private BlockState log(Direction.Axis axis) {
		return pal.log().getDefaultState().with(PillarBlock.AXIS, axis);
	}

	private BlockState fence() {
		return pal.fence().getDefaultState();
	}

	private BlockState fence(Direction.Axis axis) {
		return axis == Direction.Axis.X
				? fence().with(FenceBlock.EAST, true).with(FenceBlock.WEST, true)
				: fence().with(FenceBlock.NORTH, true).with(FenceBlock.SOUTH, true);
	}

	private static BlockState woolOf(DyeColor color) {
		return switch (color) {
			case WHITE -> Blocks.WHITE_WOOL.getDefaultState();
			case BLACK -> Blocks.BLACK_WOOL.getDefaultState();
			case YELLOW -> Blocks.YELLOW_WOOL.getDefaultState();
			default -> Blocks.ORANGE_WOOL.getDefaultState();
		};
	}

	private void corners(int x0, int z0, int x1, int z1, int y0, int y1, BlockState state) {
		for (int y = y0; y <= y1; y++) {
			l.set(x0, y, z0, state);
			l.set(x1, y, z0, state);
			l.set(x0, y, z1, state);
			l.set(x1, y, z1, state);
		}
	}

	/** A ceiling beam across the building, for hanging lanterns from. */
	private void beam(int x0, int x1, int y, int z) {
		for (int x = x0 + 1; x < x1; x++) {
			l.set(x, y, z, log(Direction.Axis.X));
		}
	}

	/** A pitched roof with its ridge running front to back. */
	private void gable(int x0, int x1, int z0, int z1, int y, Block stairs, Block slab, BlockState fill) {
		for (int i = 0; ; i++) {
			int left = x0 - 1 + i;
			int right = x1 + 1 - i;
			if (left > right) {
				break;
			}
			int level = y + i;
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				if (left == right) {
					l.set(left, level, z, slab.getDefaultState());
				} else {
					l.set(left, level, z, stairs.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
					l.set(right, level, z, stairs.getDefaultState().with(StairsBlock.FACING, Direction.WEST));
				}
			}
			for (int x = left + 1; x < right; x++) {
				l.set(x, level, z0, fill);
				l.set(x, level, z1, fill);
			}
		}
	}

	private void battlements(int x0, int z0, int x1, int z1, int y) {
		for (int x = x0; x <= x1; x++) {
			if ((x - x0) % 2 == 0) {
				l.set(x, y + 1, z0, brick);
				l.set(x, y + 1, z1, brick);
			}
		}
		for (int z = z0; z <= z1; z++) {
			if ((z - z0) % 2 == 0) {
				l.set(x0, y + 1, z, brick);
				l.set(x1, y + 1, z, brick);
			}
		}
	}

	private void corneredTowers(int half, int height) {
		for (int[] c : new int[][]{{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
			l.box(c[0] - 1, 1, c[1] - 1, c[0] + 1, height, c[1] + 1, brick);
			l.set(c[0], height + 1, c[1], Blocks.WAXED_CUT_COPPER_SLAB.getDefaultState());
		}
	}

	private void door(int x, int y, int z) {
		BlockState door = pal.door().getDefaultState().with(DoorBlock.FACING, Direction.NORTH);
		l.set(x, y, z, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		l.set(x, y + 1, z, door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private void doorPair(int x, int y, int z) {
		door(x, y, z);
		BlockState door = pal.door().getDefaultState().with(DoorBlock.FACING, Direction.NORTH)
				.with(DoorBlock.HINGE, net.minecraft.block.enums.DoorHinge.RIGHT);
		l.set(x + 2, y, z, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		l.set(x + 2, y + 1, z, door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		l.set(x + 1, y, z, AIR);
		l.set(x + 1, y + 1, z, AIR);
	}

	private void windowFront(int x, int y, int z) {
		l.set(x, y, z, Blocks.GLASS_PANE.getDefaultState().with(PaneBlock.EAST, true).with(PaneBlock.WEST, true));
	}

	private void windowSide(int x, int y, int z) {
		l.set(x, y, z, Blocks.GLASS_PANE.getDefaultState().with(PaneBlock.NORTH, true).with(PaneBlock.SOUTH, true));
	}

	private void hanging(int x, int y, int z) {
		l.set(x, y, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
	}

	private void chandelier(int x, int y, int z) {
		l.set(x, y, z, Blocks.CHAIN.getDefaultState());
		l.set(x, y - 1, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
	}

	private void ladder(int x, int y0, int z, int y1, Direction facing) {
		for (int y = y0; y <= y1; y++) {
			l.set(x, y, z, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, facing));
		}
	}

	private void table(int x0, int y, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				l.set(x, y, z, Blocks.SPRUCE_FENCE.getDefaultState());
				l.set(x, y + 1, z, Blocks.SPRUCE_PRESSURE_PLATE.getDefaultState());
			}
		}
	}

	private void bed(int x, int y, int z, Direction toward, Block bed) {
		BlockState state = bed.getDefaultState().with(BedBlock.FACING, toward);
		l.set(x, y, z, state.with(BedBlock.PART, BedPart.FOOT));
		l.set(x + toward.getOffsetX(), y, z + toward.getOffsetZ(), state.with(BedBlock.PART, BedPart.HEAD));
	}

	private void furnace(int x, int y, int z, Direction facing, Block block) {
		l.set(x, y, z, block.getDefaultState().with(AbstractFurnaceBlock.FACING, facing));
	}

	private void campfire(int x, int y, int z, boolean signal) {
		l.set(x, y, z, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true).with(CampfireBlock.SIGNAL_FIRE, signal));
	}

	/** A stone chimney at (x, z) from {@code y0} up, with a fire on top so it smokes. */
	private void chimney(int x, int z, int y0, int top) {
		for (int y = y0; y < top; y++) {
			l.set(x, y, z, brick);
		}
		l.set(x, top, z, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true));
		l.work(x, top, z, Work.CHIMNEY);
	}

	private void wallBanner(int x, int y, int z, Direction facing) {
		Block banner = Blocks.BLACK_WALL_BANNER;
		l.set(x, y, z, banner.getDefaultState().with(WallBannerBlock.FACING, facing));
		l.hook(x, y, z, Blueprints::paintBanner);
	}

	private void standingBanner(int x, int y, int z, int rotation) {
		l.set(x, y, z, Blocks.BLACK_BANNER.getDefaultState().with(BannerBlock.ROTATION, rotation));
		l.hook(x, y, z, Blueprints::paintBanner);
	}

	/** The banner of Neris: the forge-fire on black. */
	static void paintBanner(ServerWorld world, BlockPos pos) {
		if (!(world.getBlockEntity(pos) instanceof BannerBlockEntity banner)) {
			return;
		}
		RegistryEntryLookup<BannerPattern> patterns = world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BANNER_PATTERN);
		BannerPatternsComponent component = new BannerPatternsComponent.Builder()
				.add(patterns, BannerPatterns.TRIANGLES_BOTTOM, DyeColor.ORANGE)
				.add(patterns, BannerPatterns.STRIPE_BOTTOM, DyeColor.ORANGE)
				.add(patterns, BannerPatterns.CIRCLE, DyeColor.YELLOW)
				.add(patterns, BannerPatterns.BORDER, DyeColor.ORANGE)
				.build();
		ItemStack stack = new ItemStack(Items.BLACK_BANNER);
		stack.set(DataComponentTypes.BANNER_PATTERNS, component);
		banner.readComponents(stack);
		banner.markDirty();
		world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), Block.NOTIFY_ALL);
	}

	private void lectern(int x, int y, int z, Direction facing, boolean chronicle) {
		l.set(x, y, z, Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, facing).with(LecternBlock.HAS_BOOK, chronicle));
		if (chronicle) {
			l.work(x, y, z, Work.BOOKS);
			l.hook(x, y, z, (world, pos) -> ChronicleBooks.place(world, pos));
		}
	}

	private void chest(int x, int y, int z, Direction facing, List<Loot> loot) {
		l.set(x, y, z, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing));
		stock(x, y, z, loot);
	}

	private void barrel(int x, int y, int z, List<Loot> loot) {
		l.set(x, y, z, Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.UP));
		stock(x, y, z, loot);
	}

	/** Fills a container with the faction's goods, every stack marked as its property. */
	private void stock(int x, int y, int z, List<Loot> loot) {
		FactionType faction = owner;
		long containerSeed = seed ^ (x * 341873128712L + y * 132897987541L + z);
		l.hookOnce(x, y, z, (world, pos) -> {
			BlockEntity entity = world.getBlockEntity(pos);
			if (!(entity instanceof Inventory inventory)) {
				return;
			}
			Random random = Random.create(containerSeed);
			int stacks = 2 + random.nextInt(3);
			for (int i = 0; i < stacks; i++) {
				Loot entry = loot.get(random.nextInt(loot.size()));
				int count = entry.min() + random.nextInt(entry.max() - entry.min() + 1);
				if (count > 0) {
					inventory.setStack(random.nextInt(inventory.size()), Ownership.mark(new ItemStack(entry.item().get(), count), faction));
				}
			}
			entity.markDirty();
		});
	}

	private record Loot(Supplier<Item> item, int min, int max) {
	}

	private static Loot loot(Item item, int min, int max) {
		return new Loot(() -> item, min, max);
	}

	private static final List<Loot> HOME = List.of(loot(Items.BREAD, 2, 6), loot(Items.COAL, 2, 8), loot(Items.TORCH, 2, 8), loot(Items.APPLE, 1, 4), loot(Items.IRON_NUGGET, 1, 9));
	private static final List<Loot> SUPPLIES = List.of(loot(Items.PAPER, 4, 12), loot(Items.BREAD, 4, 12), loot(Items.IRON_NUGGET, 8, 32), loot(Items.MAP, 1, 1));
	private static final List<Loot> TREASURY = List.of(loot(Items.IRON_NUGGET, 32, 64), loot(Items.IRON_NUGGET, 32, 64), loot(Items.GOLD_INGOT, 4, 12), loot(Items.IRON_INGOT, 8, 16), loot(Items.DIAMOND, 1, 3));
	private static final List<Loot> MILL_GOODS = List.of(loot(Items.WHEAT, 8, 24), loot(Items.WHEAT_SEEDS, 4, 16));
	private static final List<Loot> BAKERY_GOODS = List.of(loot(Items.BREAD, 6, 16), loot(Items.WHEAT, 4, 12), loot(Items.CAKE, 1, 1));
	private static final List<Loot> KITCHEN_GOODS = List.of(loot(Items.COOKED_BEEF, 4, 12), loot(Items.BAKED_POTATO, 4, 12), loot(Items.MUSHROOM_STEW, 1, 2), loot(Items.BREAD, 4, 8));
	private static final List<Loot> FISH = List.of(loot(Items.COD, 3, 10), loot(Items.SALMON, 2, 8), loot(Items.FISHING_ROD, 1, 1));
	private static final List<Loot> LUMBER = List.of(loot(Items.DARK_OAK_LOG, 8, 24), loot(Items.SPRUCE_LOG, 8, 16), loot(Items.IRON_AXE, 1, 1));
	private static final List<Loot> SAWMILL_GOODS = List.of(loot(Items.DARK_OAK_PLANKS, 16, 48), loot(Items.STICK, 8, 24));
	private static final List<Loot> QUARRY_GOODS = List.of(loot(Items.COBBLESTONE, 16, 48), loot(Items.STONE, 8, 24), loot(Items.IRON_PICKAXE, 1, 1));
	private static final List<Loot> MASON_GOODS = List.of(loot(Items.STONE_BRICKS, 16, 48), loot(Items.POLISHED_BLACKSTONE_BRICKS, 8, 24));
	private static final List<Loot> KILN_GOODS = List.of(loot(Items.CHARCOAL, 8, 32));
	private static final List<Loot> SMELTER_GOODS = List.of(loot(Items.IRON_INGOT, 4, 16), loot(Items.RAW_IRON, 4, 12), loot(Items.COPPER_INGOT, 4, 16));
	private static final List<Loot> FORGE_GOODS = List.of(loot(Items.IRON_SWORD, 1, 1), loot(Items.IRON_HELMET, 1, 1), loot(Items.IRON_INGOT, 4, 12), loot(Items.SHIELD, 1, 1));
	private static final List<Loot> TANNERY_GOODS = List.of(loot(Items.LEATHER, 4, 16), loot(Items.RABBIT_HIDE, 2, 6));
	private static final List<Loot> WEAVER_GOODS = List.of(loot(Items.ORANGE_WOOL, 4, 12), loot(Items.BLACK_WOOL, 4, 12), loot(Items.STRING, 4, 16));
	private static final List<Loot> LIBRARY_GOODS = List.of(loot(Items.PAPER, 8, 24), loot(Items.BOOK, 2, 6), loot(Items.INK_SAC, 2, 6), loot(Items.FEATHER, 2, 6));
	private static final List<Loot> BARRACKS_GOODS = List.of(loot(Items.IRON_SWORD, 1, 1), loot(Items.ARROW, 8, 24), loot(Items.BOW, 1, 1), loot(Items.SHIELD, 1, 1), loot(Items.BREAD, 4, 8));
	private static final List<Loot> WAREHOUSE_GOODS = List.of(loot(Items.WHEAT, 8, 32), loot(Items.DARK_OAK_LOG, 8, 24), loot(Items.COBBLESTONE, 16, 48), loot(Items.COAL, 8, 24), loot(Items.BREAD, 4, 12), loot(Items.IRON_INGOT, 2, 8));
	private static final List<List<Loot>> MARKET_GOODS = List.of(BAKERY_GOODS, KITCHEN_GOODS, FISH, WEAVER_GOODS, TANNERY_GOODS, SMELTER_GOODS);
}
