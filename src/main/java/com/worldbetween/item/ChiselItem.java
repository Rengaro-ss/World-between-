package com.worldbetween.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** The Kaments' chisel. It stays in the grid after every cut: stone does not blunt it. */
public class ChiselItem extends Item {
	public ChiselItem(Settings settings) {
		super(settings);
	}

	@Override
	public ItemStack getRecipeRemainder(ItemStack stack) {
		return stack.copyWithCount(1);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.worldbetween.chisel.tooltip").formatted(Formatting.GRAY));
	}
}
