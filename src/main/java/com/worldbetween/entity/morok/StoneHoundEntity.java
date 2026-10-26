package com.worldbetween.entity.morok;

import com.worldbetween.entity.FactionMember;
import com.worldbetween.faction.FactionType;
import com.worldbetween.war.WarPresence;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.UUID;

/**
 * A hound of stone, called up by Morok out of a block of rock. Fast, hard to break, and short-lived: it
 * hunts what he hunts and, when its time is over or he is gone, crumbles back into the stone it was.
 */
public class StoneHoundEntity extends PathAwareEntity implements FactionMember {
	private static final int LIFE = 2400;
	private UUID owner;

	public StoneHoundEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.36)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5)
				.add(EntityAttributes.GENERIC_ARMOR, 6)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
	}

	public void setOwner(MorokEntity morok) {
		this.owner = morok.getUuid();
	}

	public boolean isOwnedBy(MorokEntity morok) {
		return morok.getUuid().equals(owner);
	}

	private MorokEntity master() {
		return owner != null && getWorld() instanceof ServerWorld world && world.getEntity(owner) instanceof MorokEntity morok && morok.isAlive() ? morok : null;
	}

	@Override
	public FactionType faction() {
		return FactionType.KAMENT;
	}

	@Override
	public boolean fights() {
		return true;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new MeleeAttackGoal(this, 1.3, true));
		goalSelector.add(3, new HeelGoal());
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this, MorokEntity.class, StoneHoundEntity.class));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) {
			return;
		}
		MorokEntity morok = master();
		if (morok == null || age > LIFE) {
			crumble(world);
			return;
		}
		// The pack hunts what its master hunts, and anything else he would.
		if (getTarget() == null || !getTarget().isAlive()) {
			if (morok.getTarget() != null) {
				setTarget(morok.getTarget());
			} else if (age % 20 == 0) {
				LivingEntity prey = world.getClosestEntity(LivingEntity.class, net.minecraft.entity.ai.TargetPredicate.createAttackable().setPredicate(morok::isPrey),
						this, getX(), getY(), getZ(), getBoundingBox().expand(16));
				if (prey != null) {
					setTarget(prey);
				}
			}
		}
		setAir(getMaxAir());
	}

	/** Back into the stone it came from. */
	private void crumble(ServerWorld world) {
		BlockPos pos = getBlockPos();
		if (world.getBlockState(pos).isAir()) {
			world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState());
		}
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBBLESTONE.getDefaultState()), getX(), getY() + 0.5, getZ(),
				20, 0.3, 0.3, 0.3, 0.05);
		world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1f, 0.8f);
		WarPresence.release(getUuid());
		discard();
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		return super.canTarget(target) && !(target instanceof MorokEntity) && !(target instanceof StoneHoundEntity);
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return random.nextInt(3) == 0 ? SoundEvents.ENTITY_WOLF_GROWL : null;
	}

	@Override
	public float getSoundPitch() {
		return 0.5f;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLOCK_STONE_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLOCK_STONE_BREAK;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	/** Keeps to its master's heel when there is nothing to hunt. */
	private class HeelGoal extends Goal {
		HeelGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			MorokEntity morok = master();
			return morok != null && getTarget() == null && squaredDistanceTo(morok) > 36;
		}

		@Override
		public void tick() {
			MorokEntity morok = master();
			if (morok != null && age % 20 == 0) {
				getNavigation().startMovingTo(morok, 1.2);
			}
		}
	}
}
