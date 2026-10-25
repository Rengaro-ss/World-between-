package com.worldbetween.mixin;

import com.worldbetween.item.Ownership;
import com.worldbetween.mine.MineManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Whatever drops from a faction's furnishings is marked as that faction's property.
 */
@Mixin(Block.class)
public abstract class BlockDropsMixin {
	@Inject(method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
			at = @At("RETURN"))
	private static void worldbetween$markFactionDrops(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity,
			Entity entity, ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
		MineManager.Site site = MineManager.siteOfFurnishing(world, pos, state);
		if (site != null) {
			cir.getReturnValue().forEach(stack -> Ownership.mark(stack, site.owner()));
		}
	}
}
