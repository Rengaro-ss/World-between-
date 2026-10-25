package com.worldbetween.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** An amulet an Ancient has cut a charm of stone into. It works from anywhere in the inventory. */
public class StoneAmuletItem extends Item {
	public StoneAmuletItem(Settings settings) {
		super(settings);
	}

	public static ItemStack of(StoneCharm.Kind kind, int level) {
		ItemStack stack = new ItemStack(WorldBetweenItems.STONE_AMULET);
		stack.set(WorldBetweenItems.CHARM, new StoneCharm(kind, level));
		return stack;
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public Text getName(ItemStack stack) {
		StoneCharm charm = stack.get(WorldBetweenItems.CHARM);
		if (charm == null) {
			return super.getName(stack);
		}
		return Text.translatable("item.worldbetween.stone_amulet.named", charm.kind().displayName(),
				Text.translatable("enchantment.level." + charm.level()));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		StoneCharm charm = stack.get(WorldBetweenItems.CHARM);
		if (charm != null) {
			tooltip.add(Text.translatable("charm.worldbetween." + charm.kind().id() + ".effect." + Math.min(2, charm.level())).formatted(Formatting.LIGHT_PURPLE));
		}
		tooltip.add(Text.translatable("item.worldbetween.stone_amulet.tooltip").formatted(Formatting.GRAY));
	}
}
