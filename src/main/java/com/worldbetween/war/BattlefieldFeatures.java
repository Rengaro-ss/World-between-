package com.worldbetween.war;

import com.worldbetween.faction.FactionType;
import com.worldbetween.item.WorldBetweenItems;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.Ground;
import com.worldbetween.town.TownFeatures;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BannerBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * What a fight leaves behind: trampled, torn-up ground, a fallen banner or two, and the fallen. The Neris
 * leave armour and blades on the ground (on stands a looter can strip); the Kaments break into heaps of their
 * own stone; the Arki shatter into crystal. What they carried lies in a few chests of remains: free for the
 * taking, since the dead own nothing.
 */
public final class BattlefieldFeatures {
	private static final int MAX_CHESTS = 4;

	private BattlefieldFeatures() {
	}

	public static List<TownFeatures.Feature> of(ServerWorld world) {
		List<TownFeatures.Feature> features = new ArrayList<>();
		for (Battlefield field : WorldBetweenState.get(world.getServer()).wars().fields()) {
			if (Ground.isWater(world, field.pos().getX(), field.pos().getZ())) {
				continue; // the sea keeps its dead
			}
			features.add(TownFeatures.structure("war/field_" + field.id(), 0, v -> draw(world, field), new int[]{0}));
		}
		return features;
	}

	public static int radius(Battlefield field) {
		return (int) Math.min(12, 4 + Math.ceil(Math.sqrt(field.fallen()) * 1.5));
	}

	private static Canvas draw(ServerWorld world, Battlefield field) {
		Canvas canvas = new Canvas();
		Random random = Random.create(field.id() * 0x9E3779B97F4A7C15L + field.day());
		int cx = field.pos().getX();
		int cz = field.pos().getZ();
		int r = radius(field);
		// Ground torn up by boots, stone feet and bolts of light.
		for (int i = 0; i < r * r; i++) {
			int x = cx + random.nextInt(2 * r + 1) - r;
			int z = cz + random.nextInt(2 * r + 1) - r;
			if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > r * r || Ground.isWater(world, x, z)) {
				continue;
			}
			BlockState scar = switch (random.nextInt(4)) {
				case 0 -> Blocks.COARSE_DIRT.getDefaultState();
				case 1 -> Blocks.GRAVEL.getDefaultState();
				case 2 -> Blocks.ROOTED_DIRT.getDefaultState();
				default -> Blocks.PODZOL.getDefaultState();
			};
			canvas.put(new BlockPos(x, Ground.floor(world, x, z), z), scar, Canvas.Mode.SET);
		}
		fallen(world, canvas, field, field.a(), field.fallenA(), random, cx, cz, r);
		fallen(world, canvas, field, field.b(), field.fallenB(), random, cx, cz, r);
		// The remains: what the fallen carried.
		int chests = Math.max(1, Math.min(MAX_CHESTS, field.fallen() / 4));
		for (int i = 0; i < chests; i++) {
			int x = cx + random.nextInt(r) - r / 2;
			int z = cz + random.nextInt(r) - r / 2;
			if (Ground.isWater(world, x, z)) {
				continue;
			}
			BlockPos at = new BlockPos(x, Ground.floor(world, x, z) + 1, z);
			canvas.put(at, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.Type.HORIZONTAL.random(random)), Canvas.Mode.FILL);
			long seed = random.nextLong();
			int share = i;
			canvas.hooks().add(new Canvas.Hook(at, (w, pos) -> fillRemains(w, pos, field, seed, share, chests), true));
		}
		return canvas;
	}

	/** The fallen of one side, laid out across the field the way that people falls. */
	private static void fallen(ServerWorld world, Canvas canvas, Battlefield field, FactionType side, int count, Random random,
			int cx, int cz, int r) {
		int shown = Math.min(count, 14);
		for (int i = 0; i < shown; i++) {
			int x = cx + random.nextInt(2 * r + 1) - r;
			int z = cz + random.nextInt(2 * r + 1) - r;
			if (Ground.isWater(world, x, z)) {
				continue;
			}
			BlockPos at = new BlockPos(x, Ground.floor(world, x, z) + 1, z);
			switch (side) {
				case NERIS -> {
					if (i % 2 == 0) {
						long seed = random.nextLong();
						canvas.hooks().add(new Canvas.Hook(at, (w, pos) -> fallenSoldier(w, pos, seed), true));
					}
				}
				case KAMENT -> {
					// A Kament breaks into a heap of the stone it was hewn from.
					BlockState stone = switch (random.nextInt(4)) {
						case 0 -> Blocks.COBBLESTONE.getDefaultState();
						case 1 -> Blocks.DIORITE.getDefaultState();
						case 2 -> Blocks.GRANITE.getDefaultState();
						default -> Blocks.ANDESITE.getDefaultState();
					};
					canvas.put(at, stone, Canvas.Mode.FILL);
					if (random.nextBoolean()) {
						canvas.put(at.east(random.nextBoolean() ? 1 : -1), Blocks.COBBLESTONE_SLAB.getDefaultState(), Canvas.Mode.FILL);
					}
					if (random.nextInt(3) == 0) {
						canvas.put(at.up(), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Canvas.Mode.FILL);
					}
				}
				case ARKI -> {
					// An Arki shatters: crystal where it fell, and a scorch of its light.
					canvas.put(at, Blocks.AMETHYST_CLUSTER.getDefaultState(), Canvas.Mode.FILL);
					if (random.nextBoolean()) {
						canvas.put(at.north(), Blocks.MEDIUM_AMETHYST_BUD.getDefaultState(), Canvas.Mode.FILL);
					}
					if (random.nextInt(3) == 0) {
						canvas.put(at.down(), Blocks.CALCITE.getDefaultState(), Canvas.Mode.SET);
					}
				}
			}
		}
		if (count > 0) {
			// A banner of the side, knocked crooked.
			int x = cx + random.nextInt(r + 1) - r / 2;
			int z = cz + random.nextInt(r + 1) - r / 2;
			if (!Ground.isWater(world, x, z)) {
				BlockState banner = switch (side) {
					case NERIS -> Blocks.BLACK_BANNER.getDefaultState();
					case KAMENT -> Blocks.GRAY_BANNER.getDefaultState();
					case ARKI -> Blocks.PURPLE_BANNER.getDefaultState();
				};
				canvas.put(new BlockPos(x, Ground.floor(world, x, z) + 1, z), banner.with(BannerBlock.ROTATION, random.nextInt(16)), Canvas.Mode.FILL);
			}
		}
	}

	/** A soldier of Neris where he fell: his dented armour and his blade, for a looter to strip. */
	private static void fallenSoldier(ServerWorld world, BlockPos pos, long seed) {
		Random random = Random.create(seed);
		ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
		if (stand == null) {
			return;
		}
		stand.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
		stand.setShowArms(true);
		stand.setHideBasePlate(true);
		stand.setHeadRotation(new EulerAngle(30 + random.nextFloat() * 30, random.nextFloat() * 40 - 20, 10));
		stand.setBodyRotation(new EulerAngle(15 + random.nextFloat() * 15, 0, random.nextFloat() * 10 - 5));
		stand.setRightArmRotation(new EulerAngle(-20 - random.nextFloat() * 40, 0, 20));
		stand.setLeftArmRotation(new EulerAngle(-10, 0, -15));
		if (random.nextInt(3) > 0) {
			stand.equipStack(EquipmentSlot.HEAD, worn(Items.IRON_HELMET, random));
		}
		if (random.nextBoolean()) {
			stand.equipStack(EquipmentSlot.CHEST, worn(Items.IRON_CHESTPLATE, random));
		}
		if (random.nextBoolean()) {
			stand.equipStack(EquipmentSlot.MAINHAND, worn(Items.IRON_SWORD, random));
		}
		world.spawnEntity(stand);
	}

	private static ItemStack worn(Item item, Random random) {
		ItemStack stack = new ItemStack(item);
		stack.setDamage((int) (stack.getMaxDamage() * (0.5 + random.nextFloat() * 0.45)));
		return stack;
	}

	/** One chest's share of what the fallen carried. */
	private static void fillRemains(ServerWorld world, BlockPos pos, Battlefield field, long seed, int share, int chests) {
		BlockEntity entity = world.getBlockEntity(pos);
		if (!(entity instanceof ChestBlockEntity chest)) {
			return;
		}
		Random random = Random.create(seed);
		List<ItemStack> loot = new ArrayList<>();
		loot(loot, field.a(), (field.fallenA() + chests - 1 - share) / chests, random);
		loot(loot, field.b(), (field.fallenB() + share) / chests, random);
		int slot = 0;
		for (ItemStack stack : loot) {
			if (slot >= chest.size()) {
				break;
			}
			slot += random.nextInt(2);
			if (slot < chest.size()) {
				chest.setStack(slot++, stack);
			}
		}
		NbtCompound nbt = chest.createNbt(world.getRegistryManager());
		nbt.putString("CustomName", Text.Serialization.toJsonString(Text.translatable("container.worldbetween.remains"), world.getRegistryManager()));
		chest.read(nbt, world.getRegistryManager());
		chest.markDirty();
	}

	/** What a fallen fighter of a people leaves behind, a little of it each. */
	private static void loot(List<ItemStack> loot, FactionType side, int fallen, Random random) {
		for (int i = 0; i < Math.min(fallen, 12); i++) {
			switch (side) {
				case NERIS -> {
					if (random.nextInt(10) < 3) loot.add(worn(Items.IRON_SWORD, random));
					if (random.nextInt(10) < 2) loot.add(worn(random.nextBoolean() ? Items.IRON_HELMET : Items.IRON_CHESTPLATE, random));
					if (random.nextInt(10) < 2) loot.add(worn(Items.SHIELD, random));
					if (random.nextInt(10) < 4) loot.add(new ItemStack(Items.IRON_NUGGET, 2 + random.nextInt(6)));
					if (random.nextInt(10) < 4) loot.add(new ItemStack(Items.BREAD, 1 + random.nextInt(3)));
					if (random.nextInt(10) < 2) loot.add(new ItemStack(Items.LEATHER, 1 + random.nextInt(2)));
				}
				case KAMENT -> {
					if (random.nextInt(10) < 6) loot.add(new ItemStack(WorldBetweenItems.FLINT_SHARD, 2 + random.nextInt(5)));
					if (random.nextInt(10) < 3) loot.add(new ItemStack(WorldBetweenItems.HEWN_GRANITE, 1 + random.nextInt(2)));
					if (random.nextInt(10) < 3) loot.add(new ItemStack(Items.FLINT, 1 + random.nextInt(3)));
					// Now and then the heart of the fallen Kament: a spark of soul left in the stone.
					if (random.nextInt(20) == 0) loot.add(new ItemStack(WorldBetweenItems.HEARTSTONE));
				}
				case ARKI -> {
					if (random.nextInt(10) < 6) loot.add(new ItemStack(Items.AMETHYST_SHARD, 2 + random.nextInt(5)));
					if (random.nextInt(10) < 3) loot.add(new ItemStack(Items.GLOWSTONE_DUST, 1 + random.nextInt(3)));
					if (random.nextInt(10) < 2) loot.add(new ItemStack(Items.PRISMARINE_CRYSTALS, 1 + random.nextInt(2)));
					if (random.nextInt(12) == 0) loot.add(new ItemStack(Items.ENDER_PEARL));
				}
			}
		}
	}
}
