package com.worldbetween.entity.kament;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A Kament in the world: a little taller than a man, a little shorter than an enderman, hewn from the rock
 * of its mountain. Slow and very hard to move; sinks in water rather than swims, and walks on along the
 * bottom. Like the Neris, they exist only while a player is near, and are never saved.
 */
public class KamentEntity extends PathAwareEntity implements com.worldbetween.trade.Stall.Keeper, com.worldbetween.entity.FactionMember {
	private static final TrackedData<Integer> ROLE = DataTracker.registerData(KamentEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> MATERIAL = DataTracker.registerData(KamentEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private BlockPos anchor;
	private int anchorRadius = 4;
	private BlockPos workSpot;
	/** Where it is marching to: a war band's next stretch. */
	private BlockPos destination;
	private UUID quarry;
	private String speechKey = "";
	private Object[] speechArgs = new Object[0];
	private Consumer<DamageSource> onDeath;
	/** Client: ticks left of the overhead blow, for the animation. */
	private int attackTicks;
	/** Traders: the city they deal for, and whether this is its Ancient (charms) or a carver (stone). */
	private int market = -1;
	private boolean charmer;
	private final com.worldbetween.trade.Stall stall = new com.worldbetween.trade.Stall();

	public KamentEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 60)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8)
				.add(EntityAttributes.GENERIC_ARMOR, 8)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.9)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE, 0);
		builder.add(MATERIAL, 0);
	}

	public KamentRole role() {
		return KamentRole.byIndex(dataTracker.get(ROLE));
	}

	public KamentMaterial material() {
		return KamentMaterial.byIndex(dataTracker.get(MATERIAL));
	}

	public void setMaterial(KamentMaterial material) {
		dataTracker.set(MATERIAL, material.ordinal());
	}

	public void setRole(KamentRole role) {
		dataTracker.set(ROLE, role.ordinal());
		switch (role) {
			case GUARD -> attribute(100, 14, 0.22);
			case SCULPTOR -> attribute(70, 6, 0.18);
			case ANCIENT -> attribute(160, 16, 0.15);
			default -> attribute(60, 8, 0.2);
		}
	}

	private void attribute(double health, double damage, double speed) {
		getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
		getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
		getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
		setHealth((float) health);
	}

	public void setAnchor(BlockPos anchor, int radius) {
		this.anchor = anchor;
		this.anchorRadius = radius;
	}

	public void setWorkSpot(BlockPos workSpot) {
		this.workSpot = workSpot;
	}

	public void setDestination(BlockPos destination) {
		this.destination = destination;
	}

	@Override
	public FactionType faction() {
		return FactionType.KAMENT;
	}

	@Override
	public boolean fights() {
		return role().fights();
	}

	public void setQuarry(UUID quarry) {
		this.quarry = quarry;
	}

	public void setSpeech(String key, Object... args) {
		this.speechKey = key;
		this.speechArgs = args;
	}

	public void setOnDeath(Consumer<DamageSource> onDeath) {
		this.onDeath = onDeath;
	}

	/** Makes this Kament deal with strangers for its city: in stone, or (an Ancient) in charms. */
	public void setTrader(int settlement, boolean charmer) {
		this.market = settlement;
		this.charmer = charmer;
		addCommandTag(charmer ? "worldbetween.charmer" : "worldbetween.trader");
	}

	@Override
	public com.worldbetween.trade.Stall stall() {
		return stall;
	}

	@Override
	public boolean isClient() {
		return getWorld().isClient();
	}

	@Override
	public void remove(RemovalReason reason) {
		stall.close();
		super.remove(reason);
	}

	public int attackTicks() {
		return attackTicks;
	}

	@Override
	protected void initGoals() {
		// No swimming: stone sinks, and walks on along the bottom.
		goalSelector.add(1, new MeleeAttackGoal(this, 1.0, true));
		goalSelector.add(2, new FollowQuarryGoal());
		goalSelector.add(3, new MarchGoal());
		goalSelector.add(4, new WorkGoal());
		goalSelector.add(5, new KeepNearGoal());
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12f));
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::isEnemy));
		targetSelector.add(3, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false,
				target -> com.worldbetween.war.WarRules.isEnemy(this, target)));
	}

	/** The guards and the Ancients turn on whoever the people have judged an enemy. */
	private boolean isEnemy(LivingEntity target) {
		if (!role().fights() || !(target instanceof PlayerEntity player) || player.isCreative() || player.isSpectator()
				|| !(getWorld() instanceof ServerWorld world)) {
			return false;
		}
		return WorldBetweenState.get(world.getServer()).faction(FactionType.KAMENT).isCriminal(player.getUuid());
	}

	@Override
	public boolean tryAttack(Entity target) {
		getWorld().sendEntityStatus(this, (byte) 4);
		boolean hit = super.tryAttack(target);
		if (hit) {
			// A blow of stone: the target goes up and back.
			target.setVelocity(target.getVelocity().add(0, 0.45, 0));
		}
		playSound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 1f, 0.6f);
		return hit;
	}

	@Override
	public void handleStatus(byte status) {
		if (status == 4) {
			attackTicks = 10;
		} else {
			super.handleStatus(status);
		}
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (getWorld().isClient() || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.SUCCESS;
		}
		getLookControl().lookAt(player);
		if (KamentSecurity.surrender(this, serverPlayer)) {
			return ActionResult.SUCCESS;
		}
		Faction faction = WorldBetweenState.get(serverPlayer.getServer()).faction(FactionType.KAMENT);
		if (market >= 0 && stall.customer() == null && getTarget() == null) {
			com.worldbetween.faction.Settlement city = faction.settlements().stream().filter(s -> s.id() == market).findFirst().orElse(null);
			if (city != null && com.worldbetween.trade.KamentTrade.open(this, serverPlayer, faction, city, charmer)) {
				return ActionResult.SUCCESS;
			}
		}
		String key = speechKey.isEmpty() ? "speech.worldbetween.kament." + role().id() + "." + random.nextInt(3) : speechKey;
		if (faction.isCriminal(player.getUuid())) {
			key = "speech.worldbetween.kament.enemy";
		}
		say(serverPlayer, key, speechArgs);
		return ActionResult.SUCCESS;
	}

	public void say(ServerPlayerEntity player, String key, Object... args) {
		Text name = getCustomName() != null ? getCustomName() : getName();
		player.sendMessage(Text.empty().append(name.copy().formatted(Formatting.GRAY)).append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
				.append(Text.translatable(key, args).formatted(Formatting.WHITE)), false);
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!getWorld().isClient() && onDeath != null) {
			onDeath.accept(source);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient()) {
			if (attackTicks > 0) {
				attackTicks--;
			}
			return;
		}
		if (age % 100 == 50 && !KamentPresence.isManaged(getUuid()) && !com.worldbetween.war.WarPresence.isManaged(getUuid())) {
			discard();
			return;
		}
		stall.tick(this);
		// One that keeps a fixed place (an Ancient on its seat, a guard at its post) and has been knocked or has
		// fallen far from it, goes back: as if it had always stood there.
		if (anchor != null && anchorRadius == 0 && getTarget() == null && quarry == null && age % 40 == 0
				&& getBlockPos().getSquaredDistance(anchor) > 16) {
			refreshPositionAndAngles(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, getYaw(), 0);
			getNavigation().stop();
		}
		setAir(getMaxAir()); // stone does not drown
		if (isTouchingWater()) {
			// Stone sinks, slowly, and wades along the bottom.
			setVelocity(getVelocity().multiply(0.6, 1, 0.6).add(0, -0.04, 0));
		}
	}


	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLOCK_DEEPSLATE_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLOCK_DEEPSLATE_BRICKS_BREAK;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.BLOCK_STONE_STEP, 0.6f, 0.5f);
	}

	@Override
	public int getMaxLookPitchChange() {
		return 20;
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	public static ServerPlayerEntity killer(DamageSource source) {
		return source.getAttacker() instanceof ServerPlayerEntity player ? player : null;
	}

	/** Comes to a stranger to say what has to be said. */
	private class FollowQuarryGoal extends Goal {
		FollowQuarryGoal() {
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		private PlayerEntity player() {
			return quarry == null ? null : getWorld().getPlayerByUuid(quarry);
		}

		@Override
		public boolean canStart() {
			PlayerEntity player = player();
			return player != null && getTarget() == null && squaredDistanceTo(player) > 9;
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			PlayerEntity player = player();
			if (player != null) {
				getLookControl().lookAt(player);
				if (age % 20 == 0) {
					getNavigation().startMovingTo(player, 1.0);
				}
			}
		}
	}

	/** Marches with its band towards a far point, a stretch at a time. */
	private class MarchGoal extends Goal {
		MarchGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return destination != null && getTarget() == null && getBlockPos().getSquaredDistance(destination) > 9;
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			if (age % 40 != 0 && !getNavigation().isIdle()) {
				return;
			}
			net.minecraft.util.math.Vec3d here = getPos();
			net.minecraft.util.math.Vec3d toward = net.minecraft.util.math.Vec3d.ofBottomCenter(destination).subtract(here);
			double distance = toward.horizontalLength();
			net.minecraft.util.math.Vec3d step = distance > 16 ? here.add(toward.multiply(16 / distance)) : net.minecraft.util.math.Vec3d.ofBottomCenter(destination);
			int x = (int) Math.floor(step.x);
			int z = (int) Math.floor(step.z);
			int y = getWorld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			getNavigation().startMovingTo(x + 0.5, y, z + 0.5, 1.0);
		}
	}

	/** Workers stand at their stone and work it; they need no daylight in the deep. */
	private class WorkGoal extends Goal {
		WorkGoal() {
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return workSpot != null && getTarget() == null && quarry == null;
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			if (getBlockPos().getSquaredDistance(workSpot) > 4) {
				if (age % 40 == 0) {
					getNavigation().startMovingTo(workSpot.getX() + 0.5, workSpot.getY(), workSpot.getZ() + 0.5, 0.9);
				}
				return;
			}
			if (age % 40 == 0) {
				swingHand(Hand.MAIN_HAND);
				playSound(SoundEvents.BLOCK_STONE_HIT, 0.7f, 0.7f + random.nextFloat() * 0.2f);
			}
		}
	}

	/** Stays by its place, now and then shifting a step or two, as stone does. */
	private class KeepNearGoal extends Goal {
		private int cooldown;

		KeepNearGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return anchor != null && getTarget() == null && quarry == null && --cooldown <= 0;
		}

		@Override
		public boolean shouldContinue() {
			return !getNavigation().isIdle();
		}

		@Override
		public void start() {
			cooldown = 160 + random.nextInt(200);
			if (getBlockPos().getSquaredDistance(anchor) > (anchorRadius + 2) * (anchorRadius + 2) || anchorRadius == 0) {
				getNavigation().startMovingTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 0.8);
				return;
			}
			int x = anchor.getX() + random.nextInt(anchorRadius * 2 + 1) - anchorRadius;
			int z = anchor.getZ() + random.nextInt(anchorRadius * 2 + 1) - anchorRadius;
			getNavigation().startMovingTo(x + 0.5, anchor.getY(), z + 0.5, 0.6);
		}
	}
}
