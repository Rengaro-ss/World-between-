package com.worldbetween.entity.morok;

import com.worldbetween.entity.WorldBetweenEntities;
import com.worldbetween.entity.kament.KamentEntity;
import com.worldbetween.entity.kament.KamentMaterial;
import com.worldbetween.entity.kament.KamentRole;
import com.worldbetween.faction.FactionType;
import com.worldbetween.war.WarPresence;
import com.worldbetween.war.WarRules;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * The Ancient Morok in the flesh of stone: a Kament half as tall again as any other, cracked through with
 * the light of the souls that woke him. He fights everyone the mountain does not trust, and calls up hounds
 * of stone out of the rock around him; they crumble back into it when their time is done.
 */
public class MorokEntity extends KamentEntity {
	public static final int MAX_HOUNDS = 6;
	/** Below this standing with the Kaments, a stranger is his prey. */
	public static final int TRUSTED = 60;

	public MorokEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		setRole(KamentRole.ANCIENT);
		setMaterial(KamentMaterial.DEEPSLATE);
		getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(600);
		getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(22);
		getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.24);
		setHealth(600);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return KamentEntity.createAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 600)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 22)
				.add(EntityAttributes.GENERIC_ARMOR, 14)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40);
	}

	@Override
	protected void initGoals() {
		super.initGoals();
		targetSelector.add(4, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, MorokEntity::distrusted));
		targetSelector.add(5, new ActiveTargetGoal<>(this, WanderingTraderEntity.class, 10, true, false, target -> true));
	}

	/** Strangers the mountain does not trust enough. */
	public static boolean distrusted(LivingEntity target) {
		if (!(target instanceof PlayerEntity player) || player.isCreative() || player.isSpectator() || !(target.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		return WorldBetweenState.get(world.getServer()).faction(FactionType.KAMENT).reputation(player.getUuid()) < TRUSTED;
	}

	/** Whom he, and his hounds, go for. */
	public boolean isPrey(LivingEntity target) {
		return target.isAlive() && target != this && !(target instanceof StoneHoundEntity)
				&& (distrusted(target) || target instanceof WanderingTraderEntity || WarRules.isEnemy(this, target));
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (!getWorld().isClient() && hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity serverPlayer) {
			getLookControl().lookAt(player);
			say(serverPlayer, distrusted(player) ? "speech.worldbetween.morok.enemy" : "speech.worldbetween.morok." + random.nextInt(3));
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient() || !(getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (age % 20 == 0 && getHealth() < getMaxHealth()) {
			heal(2f); // stone knits
		}
		if (age % 140 == 0 && (getTarget() != null || world.getClosestPlayer(this, 24) != null)) {
			raiseHound(world);
		}
	}

	/** A block of stone near him stirs, and stands up as a hound. */
	private void raiseHound(ServerWorld world) {
		int hounds = world.getEntitiesByClass(StoneHoundEntity.class, new Box(getBlockPos()).expand(32), h -> h.isOwnedBy(this)).size();
		if (hounds >= MAX_HOUNDS) {
			return;
		}
		for (int i = 0; i < 40; i++) {
			BlockPos pos = getBlockPos().add(random.nextInt(25) - 12, random.nextInt(9) - 6, random.nextInt(25) - 12);
			BlockState state = world.getBlockState(pos);
			boolean stone = state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.MOSSY_COBBLESTONE);
			if (!stone || !world.getBlockState(pos.up()).isAir() || !world.getBlockState(pos.up(2)).isAir()) {
				continue;
			}
			StoneHoundEntity hound = WorldBetweenEntities.STONE_HOUND.create(world);
			if (hound == null) {
				return;
			}
			world.setBlockState(pos, Blocks.AIR.getDefaultState());
			hound.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0);
			hound.setOwner(this);
			world.spawnEntity(hound);
			WarPresence.adopt(hound.getUuid());
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					30, 0.4, 0.4, 0.4, 0.1);
			world.playSound(null, pos, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 1.2f, 0.6f);
			swingHand(Hand.MAIN_HAND);
			return;
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean fights() {
		return true;
	}
}
