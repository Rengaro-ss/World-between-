package com.worldbetween.entity;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A person of Neris in the world. They exist only while a player is near: the town, the mine or the
 * seeker's journey goes on in the simulation either way, and these are its visible side. So they are
 * never saved; whoever manages them brings them back when a player returns.
 */
public class NerisEntity extends PathAwareEntity {
	private static final TrackedData<Integer> ROLE = DataTracker.registerData(NerisEntity.class, TrackedDataHandlerRegistry.INTEGER);

	/** Where the person is headed: a seeker's goal, a guard's quarry. */
	private BlockPos destination;
	/** The place the person keeps to: the town square, a tower, a hall. */
	private BlockPos anchor;
	private int anchorRadius = 8;
	/** A work spot to stand at and work, for workers. */
	private BlockPos workSpot;
	/** A player this person is following (a guard after a stranger, a seeker watching one). */
	private UUID quarry;
	/** Lines for when a player talks to them; supplied by whoever spawned them. */
	private String speechKey = "";
	private Object[] speechArgs = new Object[0];
	/** Called when the person dies, so the simulation can mourn them. */
	private Consumer<DamageSource> onDeath;

	public NerisEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE, 0);
	}

	public NerisRole role() {
		return NerisRole.byIndex(dataTracker.get(ROLE));
	}

	/** Sets the role and dresses for it. */
	public void setRole(NerisRole role) {
		dataTracker.set(ROLE, role.ordinal());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setEquipmentDropChance(slot, 0f);
		}
		switch (role) {
			case GUARD -> {
				attribute(48, 6, 0.3);
				equip(EquipmentSlot.MAINHAND, Items.IRON_SWORD);
				equip(EquipmentSlot.HEAD, Items.IRON_HELMET);
				equip(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
			}
			case SEEKER -> {
				attribute(60, 8, 0.33);
				equip(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD);
				equip(EquipmentSlot.CHEST, Items.CHAINMAIL_CHESTPLATE);
				equip(EquipmentSlot.LEGS, Items.CHAINMAIL_LEGGINGS);
			}
			case KING, BARON -> attribute(40, 4, 0.25);
			default -> attribute(24, 3, 0.3);
		}
	}

	public void setTool(Item tool) {
		equip(EquipmentSlot.MAINHAND, tool);
	}

	private void attribute(double health, double damage, double speed) {
		getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
		getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);
		getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
		setHealth((float) health);
	}

	private void equip(EquipmentSlot slot, Item item) {
		equipStack(slot, new ItemStack(item));
	}

	public void setDestination(BlockPos destination) {
		this.destination = destination;
	}

	public BlockPos destination() {
		return destination;
	}

	public void setAnchor(BlockPos anchor, int radius) {
		this.anchor = anchor;
		this.anchorRadius = radius;
	}

	public void setWorkSpot(BlockPos workSpot) {
		this.workSpot = workSpot;
	}

	public void setQuarry(UUID quarry) {
		this.quarry = quarry;
	}

	public UUID quarry() {
		return quarry;
	}

	public void setSpeech(String key, Object... args) {
		this.speechKey = key;
		this.speechArgs = args;
	}

	public void setOnDeath(Consumer<DamageSource> onDeath) {
		this.onDeath = onDeath;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(1, new MeleeAttackGoal(this, 1.15, false));
		goalSelector.add(2, new FollowQuarryGoal());
		goalSelector.add(3, new TravelGoal());
		goalSelector.add(4, new WorkGoal());
		goalSelector.add(5, new KeepNearGoal());
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::isEnemyPlayer));
		targetSelector.add(3, new ActiveTargetGoal<>(this, HostileEntity.class, 10, true, false, target -> role().fights()));
	}

	/** Guards and seekers attack the kingdom's criminals on sight. */
	private boolean isEnemyPlayer(LivingEntity target) {
		if (!role().fights() || !(target instanceof PlayerEntity player) || player.isCreative() || player.isSpectator()
				|| !(getWorld() instanceof ServerWorld world)) {
			return false;
		}
		return WorldBetweenState.get(world.getServer()).faction(FactionType.NERIS).isCriminal(player.getUuid());
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (getWorld().isClient() || hand != Hand.MAIN_HAND) {
			return ActionResult.SUCCESS;
		}
		getLookControl().lookAt(player);
		Text name = getCustomName() != null ? getCustomName() : getName();
		String key = speechKey.isEmpty() ? "speech.worldbetween." + role().id() + "." + random.nextInt(3) : speechKey;
		Faction faction = WorldBetweenState.get(((ServerWorld) getWorld()).getServer()).faction(FactionType.NERIS);
		if (role().fights() && faction.isCriminal(player.getUuid())) {
			key = "speech.worldbetween.criminal";
		}
		player.sendMessage(Text.empty().append(name.copy().formatted(Formatting.GOLD)).append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(Text.translatable(key, speechArgs).formatted(Formatting.WHITE)), false);
		return ActionResult.SUCCESS;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!getWorld().isClient() && onDeath != null) {
			onDeath.accept(source);
		}
	}

	private Vec3d lastCheck = Vec3d.ZERO;
	private int stuckTicks;

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient() && role() == NerisRole.SEEKER) {
			climbIfStuck();
		}
	}

	/**
	 * Seekers do not get stuck behind a wall or a cliff: when they make no headway for a few seconds,
	 * they climb over and carry on a few blocks further along.
	 */
	private void climbIfStuck() {
		Vec3d goal = null;
		if (quarry != null && getWorld().getPlayerByUuid(quarry) != null) {
			goal = getWorld().getPlayerByUuid(quarry).getPos();
		} else if (destination != null) {
			goal = Vec3d.ofBottomCenter(destination);
		}
		if (goal == null || goal.squaredDistanceTo(getPos()) < 49 || getTarget() != null) {
			stuckTicks = 0;
			return;
		}
		if (age % 20 != 0) {
			return;
		}
		if (getPos().squaredDistanceTo(lastCheck) < 0.5) {
			stuckTicks += 20;
		} else {
			stuckTicks = 0;
		}
		lastCheck = getPos();
		if (stuckTicks < 100) {
			return;
		}
		stuckTicks = 0;
		Vec3d toward = goal.subtract(getPos()).multiply(1, 0, 1).normalize();
		for (int step = 6; step >= 3; step--) {
			int x = (int) Math.floor(getX() + toward.x * step);
			int z = (int) Math.floor(getZ() + toward.z * step);
			int y = getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, y, z);
			if (Math.abs(y - getBlockY()) <= 8 && getWorld().getBlockState(at.down()).isSolidBlock(getWorld(), at.down())) {
				requestTeleport(x + 0.5, y, z + 0.5);
				getNavigation().stop();
				return;
			}
		}
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	/** Walks towards a far destination in steps the pathfinder can manage. */
	private class TravelGoal extends Goal {
		private int cooldown;

		TravelGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return destination != null && quarry == null && getTarget() == null
					&& getBlockPos().getSquaredDistance(destination) > 9;
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			if (--cooldown > 0 && !getNavigation().isIdle()) {
				return;
			}
			cooldown = 40;
			Vec3d here = getPos();
			Vec3d toward = Vec3d.ofBottomCenter(destination).subtract(here);
			double distance = toward.horizontalLength();
			Vec3d step = distance > 20 ? here.add(toward.multiply(20 / distance)) : Vec3d.ofBottomCenter(destination);
			int x = (int) Math.floor(step.x);
			int z = (int) Math.floor(step.z);
			int y = distance > 20 ? getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) : destination.getY();
			getNavigation().startMovingTo(x + 0.5, y, z + 0.5, 1.0);
		}
	}

	/** Follows a particular player: a guard closing in, a seeker taking a good look. */
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
			return player != null && getTarget() == null && squaredDistanceTo(player) > 16;
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
					getNavigation().startMovingTo(player, 1.1);
				}
			}
		}
	}

	/** Workers stand at their spot and get on with it while it is light. */
	private class WorkGoal extends Goal {
		WorkGoal() {
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return workSpot != null && getTarget() == null && getWorld().isDay();
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			double distance = getBlockPos().getSquaredDistance(workSpot);
			if (distance > 6) {
				if (age % 40 == 0) {
					getNavigation().startMovingTo(workSpot.getX() + 0.5, workSpot.getY(), workSpot.getZ() + 0.5, 0.8);
				}
				return;
			}
			getLookControl().lookAt(workSpot.getX() + 0.5, workSpot.getY() + 0.5, workSpot.getZ() + 0.5);
			if (age % 25 == 0) {
				swingHand(Hand.MAIN_HAND);
			}
		}
	}

	/** Strolls about the place the person belongs to. */
	private class KeepNearGoal extends Goal {
		private int cooldown;

		KeepNearGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return anchor != null && getTarget() == null && --cooldown <= 0;
		}

		@Override
		public boolean shouldContinue() {
			return !getNavigation().isIdle();
		}

		@Override
		public void start() {
			cooldown = 100 + random.nextInt(160);
			int x = anchor.getX() + random.nextInt(anchorRadius * 2 + 1) - anchorRadius;
			int z = anchor.getZ() + random.nextInt(anchorRadius * 2 + 1) - anchorRadius;
			int y = anchorRadius <= 2 ? anchor.getY() : getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (Math.abs(y - anchor.getY()) > 3) {
				return; // a roof or a wall top: not somewhere to stroll to
			}
			getNavigation().startMovingTo(x + 0.5, y, z + 0.5, 0.6);
		}
	}

	/** Damage from the player who struck last, if any. */
	public static ServerPlayerEntity killer(DamageSource source) {
		return source.getAttacker() instanceof ServerPlayerEntity player ? player : null;
	}
}
