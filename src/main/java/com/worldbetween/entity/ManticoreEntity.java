package com.worldbetween.entity;

import com.worldbetween.faction.FactionType;
import com.worldbetween.war.WarRules;
import com.worldbetween.war.WarPresence;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * A manticore of the kingdom: a lion crossed with a great bat in the menagerie, bred for war, ridden by a
 * seeker with a bow. In a fight it circles high over its mark while the rider shoots, and now and then
 * folds its wings and dives to strike with its own claws and teeth. Never saved, like the people.
 */
public class ManticoreEntity extends PathAwareEntity implements FactionMember {
	private BlockPos destination;
	private BlockPos anchor;
	private int anchorRadius = 6;
	private Consumer<DamageSource> onDeath;
	/** Ticks of the current dive, or 0 while circling. */
	private int diving;

	public ManticoreEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		moveControl = new FlightMoveControl(this, 12, true);
		setNoGravity(true);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 80)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 9)
				.add(EntityAttributes.GENERIC_ARMOR, 4)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation navigation = new BirdNavigation(this, world);
		navigation.setCanPathThroughDoors(false);
		return navigation;
	}

	@Override
	public FactionType faction() {
		return FactionType.NERIS;
	}

	@Override
	public boolean fights() {
		return true;
	}

	public void setDestination(BlockPos destination) {
		this.destination = destination;
	}

	public void setAnchor(BlockPos anchor, int radius) {
		this.anchor = anchor;
		this.anchorRadius = radius;
	}

	public void setOnDeath(Consumer<DamageSource> onDeath) {
		this.onDeath = onDeath;
	}

	public boolean isDiving() {
		return diving > 0;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new AerialFightGoal());
		goalSelector.add(2, new FlyToGoal());
		goalSelector.add(3, new CircleGoal());
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this, NerisEntity.class));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::isCriminal));
		targetSelector.add(3, new ActiveTargetGoal<>(this, LivingEntity.class, 10, false, false, target -> WarRules.isEnemy(this, target)));
		targetSelector.add(4, new ActiveTargetGoal<>(this, HostileEntity.class, 10, true, false, target -> true));
	}

	private boolean isCriminal(LivingEntity target) {
		return target instanceof PlayerEntity player && !player.isCreative() && !player.isSpectator()
				&& getWorld() instanceof ServerWorld world && WorldBetweenState.get(world.getServer()).faction(FactionType.NERIS).isCriminal(player.getUuid());
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		return super.canTarget(target) && !(target instanceof NerisEntity) && !(target instanceof ManticoreEntity);
	}

	/** The kingdom's arrows, and its own rider's, do not find it. */
	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.getAttacker() instanceof NerisEntity || source.getAttacker() == this) {
			return false;
		}
		return super.damage(source, amount);
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient() && age % 100 == 50 && !WarPresence.isManaged(getUuid())) {
			discard();
		}
		// The rider looks where the beast fights.
		if (!getWorld().isClient() && getFirstPassenger() instanceof NerisEntity rider && getTarget() != null && rider.getTarget() == null) {
			rider.setTarget(getTarget());
		}
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!getWorld().isClient() && onDeath != null) {
			onDeath.accept(source);
		}
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return random.nextInt(3) == 0 ? SoundEvents.ENTITY_RAVAGER_AMBIENT : SoundEvents.ENTITY_PHANTOM_FLAP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_RAVAGER_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 0.8f;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	/**
	 * Circle twelve blocks off and eight up while the rider shoots; every few seconds fold the wings, dive,
	 * strike, and climb away again.
	 */
	private class AerialFightGoal extends Goal {
		private int nextDive = 80;

		AerialFightGoal() {
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return getTarget() != null && getTarget().isAlive();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			diving = 0;
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				return;
			}
			getLookControl().lookAt(target, 30f, 30f);
			if (diving > 0) {
				diving--;
				getMoveControl().moveTo(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(), 1.9);
				if (squaredDistanceTo(target) < 5.5) {
					tryAttack(target);
					target.addVelocity(0, 0.4, 0);
					playSound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1f, 1.2f);
					diving = 0;
					nextDive = 140 + random.nextInt(80);
				}
				return;
			}
			if (--nextDive <= 0) {
				diving = 50;
				playSound(SoundEvents.ENTITY_PHANTOM_SWOOP, 1.5f, 0.8f);
				return;
			}
			if (age % 10 == 0) {
				double angle = (age / 60.0) + getId();
				Vec3d around = target.getPos().add(Math.cos(angle) * 12, 8, Math.sin(angle) * 12);
				getMoveControl().moveTo(around.x, around.y, around.z, 1.2);
			}
		}
	}

	/** Flies with its band, a little above the treetops. */
	private class FlyToGoal extends Goal {
		FlyToGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return destination != null && getTarget() == null && getBlockPos().getSquaredDistance(destination) > 36;
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			if (age % 10 != 0) {
				return;
			}
			Vec3d toward = Vec3d.ofCenter(destination).subtract(getPos());
			double distance = toward.horizontalLength();
			Vec3d step = distance > 20 ? getPos().add(toward.multiply(20 / distance)) : Vec3d.ofCenter(destination);
			int ground = getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(step.x), (int) Math.floor(step.z));
			getMoveControl().moveTo(step.x, ground + 10, step.z, 1.0);
		}
	}

	/** At rest in the menagerie, or waiting with its band: slow circles about the place. */
	private class CircleGoal extends Goal {
		private int cooldown;

		CircleGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return anchor != null && destination == null && getTarget() == null && --cooldown <= 0;
		}

		@Override
		public void start() {
			cooldown = 60 + random.nextInt(60);
			double x = anchor.getX() + 0.5 + (random.nextDouble() - 0.5) * anchorRadius * 2;
			double z = anchor.getZ() + 0.5 + (random.nextDouble() - 0.5) * anchorRadius * 2;
			getMoveControl().moveTo(x, anchor.getY() + 1 + random.nextDouble() * 4, z, 0.7);
		}
	}
}
