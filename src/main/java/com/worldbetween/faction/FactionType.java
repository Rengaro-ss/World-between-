package com.worldbetween.faction;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.Set;

/**
 * The three peoples of World Between.
 */
public enum FactionType implements StringIdentifiable {
	/** Kingdom of Neris: escaped slaves of the Hellsmiths. Plains and forests, later the Nether. */
	NERIS("neris", 0xC8742F),
	/** The Arki: comet-born mages. Oceans and skies. */
	ARKI("arki", 0x7FA8FF),
	/** The Kaments: awakened stone golems. Mountains and the deep. */
	KAMENT("kament", 0x9C9C9C);

	private static final Set<RegistryKey<Biome>> NERIS_BIOMES = Set.of(
			BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.MEADOW,
			BiomeKeys.SAVANNA, BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST,
			BiomeKeys.FLOWER_FOREST, BiomeKeys.CHERRY_GROVE);

	private final String id;
	private final int color;

	FactionType(String id, int color) {
		this.id = id;
		this.color = color;
	}

	public String id() {
		return id;
	}

	public int color() {
		return color;
	}

	public MutableText displayName() {
		return Text.translatable("faction.worldbetween." + id).styled(style -> style.withColor(color));
	}

	/** Whether this faction is willing to settle in the given biome. */
	public boolean prefersBiome(RegistryEntry<Biome> biome) {
		return switch (this) {
			case NERIS -> NERIS_BIOMES.stream().anyMatch(biome::matchesKey);
			case ARKI -> biome.isIn(BiomeTags.IS_OCEAN);
			case KAMENT -> biome.isIn(BiomeTags.IS_MOUNTAIN);
		};
	}

	public static FactionType byId(String id) {
		for (FactionType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}

	@Override
	public String asString() {
		return id;
	}
}
