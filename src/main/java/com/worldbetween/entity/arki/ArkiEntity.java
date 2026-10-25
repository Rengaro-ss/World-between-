package com.worldbetween.entity.arki;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * An Arki in the world: not a person as men are, but something like an efreet of the night sky, a glowing
 * core with crystal shards turning round it, shedding motes the colours of space. They float rather than
 * walk, and for now they do not fight: struck, they flee upwards. Never saved, like all the peoples' folk.
 */
public class ArkiEntity extends PathAwareEntity {
	private static final TrackedData<Integer> ROLE = DataTracker.registerData(ArkiEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private BlockPos anchor;
	private int anchorRadius = 4;
	private BlockPos workSpot;
	private String speechKey = "";
	private Consumer<DamageSource> onDeath;
	private int fleeing;

	public ArkiEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		moveControl = new FlightMoveControl(this, 10, true);
		setNoGravity(true);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.45)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24);
	}

	@Override
	protected EntityNavigation createNavigation(World world) {
		BirdNavigation navigation = new BirdNavigation(this, world);
		navigation.setCanPathThroughDoors(false);
		navigation.setCanSwim(true);
		return navigation;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE, 0);
	}

	public ArkiRole role() {
		return ArkiRole.byIndex(dataTracker.get(ROLE));
	}

	public void setRole(ArkiRole role) {
		dataTracker.set(ROLE, role.ordinal());
	}

	public void setAnchor(BlockPos anchor, int radius) {
		this.anchor = anchor;
		this.anchorRadius = radius;
	}

	public void setWorkSpot(BlockPos workSpot) {
		this.workSpot = workSpot;
	}

	public void setSpeech(String key) {
		this.speechKey = key;
	}

	public void setOnDeath(Consumer<DamageSource> onDeath) {
		this.onDeath = onDeath;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new FleeGoal());
		goalSelector.add(3, new WorkGoal());
		goalSelector.add(4, new DriftGoal());
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
		goalSelector.add(7, new LookAroundGoal(this));
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt && !getWorld().isClient()) {
			fleeing = 80;
		}
		return hurt;
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (getWorld().isClient() || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.SUCCESS;
		}
		getLookControl().lookAt(player);
		String key = speechKey.isEmpty() ? "speech.worldbetween.arki." + role().id() + "." + random.nextInt(3) : speechKey;
		Text name = getCustomName() != null ? getCustomName() : getName();
		serverPlayer.sendMessage(Text.empty().append(name.copy().formatted(Formatting.LIGHT_PURPLE))
				.append(Text.literal(": ").formatted(Formatting.DARK_GRAY)).append(Text.translatable(key).formatted(Formatting.WHITE)), false);
		playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f + random.nextFloat() * 0.4f);
		return ActionResult.SUCCESS;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!getWorld().isClient() && onDeath != null) {
			onDeath.accept(source);
		}
	}

	@Override
	public void tickMovement() {
		super.tickMovement();
		if (getWorld().isClient()) {
			// Motes the colours of space drift off them, as smoke drifts off an efreet.
			float[][] motes = role().motes();
			for (int i = 0; i < 2; i++) {
				float[] c = motes[random.nextInt(4) == 0 ? 1 : 0];
				getWorld().addParticle(new DustParticleEffect(new Vector3f(c[0], c[1], c[2]), 0.8f + random.nextFloat() * 0.6f),
						getParticleX(0.6), getRandomBodyY(), getParticleZ(0.6), 0, 0.01, 0);
			}
			if (random.nextInt(8) == 0) {
				getWorld().addParticle(ParticleTypes.END_ROD, getParticleX(0.5), getRandomBodyY(), getParticleZ(0.5), 0, 0.02, 0);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient()) {
			if (fleeing > 0) {
				fleeing--;
			}
			if (age % 100 == 50 && !ArkiPresence.isManaged(getUuid())) {
				discard();
			}
		}
	}

	@Override
	public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return random.nextInt(4) == 0 ? SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK;
	}

	@Override
	public float getBrightnessAtEyes() {
		return 1f;
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

	/** Struck, an Arki goes up and away; they will answer with their magic one day, but not yet. */
	private class FleeGoal extends Goal {
		FleeGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return fleeing > 0;
		}

		@Override
		public void start() {
			Vec3d away = getPos().add((random.nextDouble() - 0.5) * 24, 8 + random.nextInt(6), (random.nextDouble() - 0.5) * 24);
			getNavigation().startMovingTo(away.x, away.y, away.z, 1.8);
		}

		@Override
		public boolean shouldContinue() {
			return fleeing > 0;
		}
	}

	/** Hovers over its work: the crystals, the lens, the pool. */
	private class WorkGoal extends Goal {
		WorkGoal() {
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return workSpot != null;
		}

		@Override
		public boolean shouldContinue() {
			return canStart() && fleeing == 0;
		}

		@Override
		public void tick() {
			if (getBlockPos().getSquaredDistance(workSpot.up()) > 4) {
				if (age % 30 == 0) {
					getNavigation().startMovingTo(workSpot.getX() + 0.5, workSpot.getY() + 1, workSpot.getZ() + 0.5, 1.0);
				}
				return;
			}
			getLookControl().lookAt(workSpot.getX() + 0.5, workSpot.getY(), workSpot.getZ() + 0.5);
		}
	}

	/** Drifts about its disc, rising and sinking a little. */
	private class DriftGoal extends Goal {
		private int cooldown;

		DriftGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return anchor != null && --cooldown <= 0;
		}

		@Override
		public boolean shouldContinue() {
			return !getNavigation().isIdle();
		}

		@Override
		public void start() {
			cooldown = 80 + random.nextInt(120);
			double x = anchor.getX() + 0.5 + (random.nextDouble() - 0.5) * anchorRadius * 2;
			double z = anchor.getZ() + 0.5 + (random.nextDouble() - 0.5) * anchorRadius * 2;
			double y = anchor.getY() + 1 + random.nextDouble() * 3;
			getNavigation().startMovingTo(x, y, z, 0.6);
		}
	}
}
