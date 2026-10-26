package com.worldbetween.item;

import com.worldbetween.WorldBetween;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The things of World Between a player can hold: the Kaments' coin, and the stone path they open to a
 * friend: granite and deep monolith worked with their chisel, heartstone, and amulets the Ancients charm.
 * None of it is metal, so none of it offends the mountain.
 */
public final class WorldBetweenItems {
	public static final ComponentType<StoneCharm> CHARM = ComponentType.<StoneCharm>builder()
			.codec(StoneCharm.CODEC).packetCodec(StoneCharm.PACKET_CODEC).build();

	private static final List<Item> ALL = new ArrayList<>();

	/** The Kaments' coin: flint broken into nine, as iron is into nuggets. */
	public static final Item FLINT_SHARD = item("flint_shard", new Item(new Item.Settings()));
	/** The Kaments' chisel: what a friend needs to work stone as they do. It never wears out. */
	public static final Item CHISEL = item("chisel", new ChiselItem(new Item.Settings().maxCount(1)));
	public static final Item HEWN_GRANITE = item("hewn_granite", new Item(new Item.Settings()));
	public static final Item DEEP_MONOLITH = item("deep_monolith", new Item(new Item.Settings()));
	/** Stone with a spark of a sculptor's soul left in it. Only the Kaments can give it. */
	public static final Item HEARTSTONE = item("heartstone", new Item(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item BLANK_AMULET = item("blank_amulet", new Item(new Item.Settings().maxCount(1)));
	public static final Item STONE_AMULET = item("stone_amulet", new StoneAmuletItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	/** The king's writ: whoever carries it goes into the Nether on the kingdom's business, and its purge squads let them be. */
	public static final Item ROYAL_WRIT = item("royal_writ", new Item(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	public static final ToolMaterial GRANITE = new StoneMaterial(300, 6.5f, 2f, BlockTags.INCORRECT_FOR_IRON_TOOL, 12, () -> Ingredient.ofItems(HEWN_GRANITE));
	public static final ToolMaterial MONOLITH = new StoneMaterial(1400, 7.5f, 3f, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 14, () -> Ingredient.ofItems(DEEP_MONOLITH));

	public static final RegistryEntry<ArmorMaterial> GRANITE_ARMOR = armor("granite", new int[]{2, 5, 6, 2}, 12, 0.5f, 0.05f, () -> Ingredient.ofItems(HEWN_GRANITE));
	public static final RegistryEntry<ArmorMaterial> MONOLITH_ARMOR = armor("monolith", new int[]{3, 6, 8, 3}, 14, 2f, 0.15f, () -> Ingredient.ofItems(DEEP_MONOLITH));

	public static final List<Item> GRANITE_GEAR = gear("granite", GRANITE, GRANITE_ARMOR, 16);
	public static final List<Item> MONOLITH_GEAR = gear("monolith", MONOLITH, MONOLITH_ARMOR, 34);
	/** Everything worked from the mountain's stone: it slowly mends while its bearer stands on rock. */
	public static final Set<Item> STONE_GEAR = Set.copyOf(concat(GRANITE_GEAR, MONOLITH_GEAR));

	private WorldBetweenItems() {
	}

	public static void register() {
		Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(WorldBetween.MOD_ID, "stone_charm"), CHARM);
		Registry.register(Registries.ITEM_GROUP, Identifier.of(WorldBetween.MOD_ID, "items"), FabricItemGroup.builder()
				.icon(() -> new ItemStack(HEARTSTONE))
				.displayName(Text.translatable("itemGroup.worldbetween"))
				.entries((context, entries) -> {
					for (Item item : ALL) {
						if (item == STONE_AMULET) {
							for (StoneCharm.Kind kind : StoneCharm.Kind.values()) {
								entries.add(StoneAmuletItem.of(kind, 1));
							}
						} else {
							entries.add(item);
						}
					}
				})
				.build());
	}

	private static <T extends Item> T item(String id, T item) {
		ALL.add(item);
		return Registry.register(Registries.ITEM, Identifier.of(WorldBetween.MOD_ID, id), item);
	}

	private static List<Item> gear(String name, ToolMaterial tool, RegistryEntry<ArmorMaterial> armor, int durability) {
		List<Item> items = new ArrayList<>();
		items.add(item(name + "_sword", new SwordItem(tool, new Item.Settings().attributeModifiers(SwordItem.createAttributeModifiers(tool, 3, -2.4f)))));
		items.add(item(name + "_pickaxe", new PickaxeItem(tool, new Item.Settings().attributeModifiers(MiningToolItem.createAttributeModifiers(tool, 1f, -2.8f)))));
		items.add(item(name + "_axe", new AxeItem(tool, new Item.Settings().attributeModifiers(MiningToolItem.createAttributeModifiers(tool, 6f, -3.1f)))));
		items.add(item(name + "_shovel", new ShovelItem(tool, new Item.Settings().attributeModifiers(MiningToolItem.createAttributeModifiers(tool, 1.5f, -3f)))));
		items.add(item(name + "_hoe", new HoeItem(tool, new Item.Settings().attributeModifiers(MiningToolItem.createAttributeModifiers(tool, -2f, -1f)))));
		for (ArmorItem.Type type : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS}) {
			items.add(item(name + "_" + type.getName(), new ArmorItem(armor, type, new Item.Settings().maxDamage(type.getMaxDamage(durability)))));
		}
		return items;
	}

	private static RegistryEntry<ArmorMaterial> armor(String id, int[] defense, int enchantability, float toughness, float knockback,
			Supplier<Ingredient> repair) {
		Map<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
		map.put(ArmorItem.Type.BOOTS, defense[0]);
		map.put(ArmorItem.Type.LEGGINGS, defense[1]);
		map.put(ArmorItem.Type.CHESTPLATE, defense[2]);
		map.put(ArmorItem.Type.HELMET, defense[3]);
		map.put(ArmorItem.Type.BODY, defense[2]);
		Identifier name = Identifier.of(WorldBetween.MOD_ID, id);
		return Registry.registerReference(Registries.ARMOR_MATERIAL, name, new ArmorMaterial(map, enchantability,
				SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, repair, List.of(new ArmorMaterial.Layer(name)), toughness, knockback));
	}

	private static List<Item> concat(List<Item> a, List<Item> b) {
		List<Item> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	private record StoneMaterial(int durability, float speed, float damage, TagKey<Block> inverseTag, int enchantability,
			Supplier<Ingredient> repair) implements ToolMaterial {
		@Override
		public int getDurability() {
			return durability;
		}

		@Override
		public float getMiningSpeedMultiplier() {
			return speed;
		}

		@Override
		public float getAttackDamage() {
			return damage;
		}

		@Override
		public TagKey<Block> getInverseTag() {
			return inverseTag;
		}

		@Override
		public int getEnchantability() {
			return enchantability;
		}

		@Override
		public Ingredient getRepairIngredient() {
			return repair.get();
		}
	}
}
