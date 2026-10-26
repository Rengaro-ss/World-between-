package com.worldbetween.entity.arki;

import com.worldbetween.entity.FactionMember;
import com.worldbetween.entity.WorldBetweenEntities;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.trade.ArkiTrade;
import com.worldbetween.trade.Stall;
import com.worldbetween.war.WarRules;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * An Arki in the world: not a person as men are, but something like an efreet of the night sky, a glowing
 * core with crystal shards turning round it, shedding motes the colours of space. They float rather than
 * walk, and fight with their magic: now and then a volley of spheres, a burst that throws back whoever is
 * too close, and a blade of light that strikes three blocks out; while the blade gathers again they keep
 * their distance. Stronger by night. Never saved, like all the peoples' folk.
 */
public class ArkiEntity extends PathAwareEntity implements Stall.Keeper, FactionMember {
	private static final TrackedData<Integer> ROLE = DataTracker.registerData(ArkiEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final DustParticleEffect BLADE = new DustParticleEffect(new Vector3f(0.75f, 0.6f, 1f), 1.2f);
	private static final DustParticleEffect BURST = new DustParticleEffect(new Vector3f(0.45f, 0.55f, 1f), 1.6f);
	/** Reach of the blade of light. */
	public static final double BLADE_REACH = 3.2;

	private BlockPos anchor;
	private int anchorRadius = 4;
	private BlockPos workSpot;
	/** Where it is flying to: a war band's next stretch. */
	private BlockPos destination;
	private String speechKey = "";
	private Consumer<DamageSource> onDeath;
	private int fleeing;
	/** Keepers trade for their colony. */
	private int market = -1;
	private final Stall stall = new Stall();

	public ArkiEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
		moveControl = new FlightMoveControl(this, 10, true);
		setNoGravity(true);
		setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 30)
				.add(EntityAttributes.GENERIC_FLYING_SPEED, 0.5)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28);
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
		double health = role == ArkiRole.KEEPER ? 48 : 34;
		getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);
		getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(role == ArkiRole.KEEPER ? 7 : 5.5);
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

	public void setSpeech(String key) {
		this.speechKey = key;
	}

	public void setOnDeath(Consumer<DamageSource> onDeath) {
		this.onDeath = onDeath;
	}

	public void setMarket(int colony) {
		this.market = colony;
		addCommandTag("worldbetween.trader");
	}

	@Override
	public FactionType faction() {
		return FactionType.ARKI;
	}

	@Override
	public boolean fights() {
		return true;
	}

	@Override
	public Stall stall() {
		return stall;
	}

	@Override
	public boolean isClient() {
		return getWorld().isClient();
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new FleeGoal());
		goalSelector.add(1, new MagicGoal());
		goalSelector.add(2, new TravelGoal());
		goalSelector.add(3, new WorkGoal());
		goalSelector.add(4, new DriftGoal());
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this).setGroupRevenge());
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::isOutlaw));
		targetSelector.add(3, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false, target -> WarRules.isEnemy(this, target)));
		targetSelector.add(4, new ActiveTargetGoal<>(this, HostileEntity.class, 10, true, false, target -> role() == ArkiRole.KEEPER));
	}

	/** Whoever broke the Arki's law is fought on sight. */
	private boolean isOutlaw(LivingEntity target) {
		if (!(target instanceof PlayerEntity player) || player.isCreative() || player.isSpectator() || !(getWorld() instanceof ServerWorld world)) {
			return false;
		}
		return WorldBetweenState.get(world.getServer()).faction(FactionType.ARKI).isCriminal(player.getUuid());
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt && !getWorld().isClient() && getHealth() < getMaxHealth() * 0.3f) {
			fleeing = 100; // badly hurt: away and up, and back to the fight once the light has gathered again
		}
		return hurt;
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (getWorld().isClient() || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.SUCCESS;
		}
		getLookControl().lookAt(player);
		Faction faction = WorldBetweenState.get(serverPlayer.getServer()).faction(FactionType.ARKI);
		if (faction.isCriminal(player.getUuid())) {
			say(serverPlayer, "speech.worldbetween.arki.outlaw");
			return ActionResult.SUCCESS;
		}
		if (market >= 0 && stall.customer() == null && getTarget() == null) {
			Settlement colony = faction.settlements().stream().filter(s -> s.id() == market).findFirst().orElse(null);
			if (colony != null && ArkiTrade.open(this, serverPlayer, faction, colony)) {
				return ActionResult.SUCCESS;
			}
		}
		say(serverPlayer, speechKey.isEmpty() ? "speech.worldbetween.arki." + role().id() + "." + random.nextInt(3) : speechKey);
		return ActionResult.SUCCESS;
	}

	public void say(ServerPlayerEntity player, String key, Object... args) {
		Text name = getCustomName() != null ? getCustomName() : getName();
		player.sendMessage(Text.empty().append(name.copy().formatted(Formatting.LIGHT_PURPLE))
				.append(Text.literal(": ").formatted(Formatting.DARK_GRAY)).append(Text.translatable(key, args).formatted(Formatting.WHITE)), false);
		playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f + random.nextFloat() * 0.4f);
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!getWorld().isClient() && onDeath != null) {
			onDeath.accept(source);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		stall.close();
		super.remove(reason);
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
		if (getWorld().isClient()) {
			return;
		}
		if (fleeing > 0) {
			fleeing--;
		}
		stall.tick(this);
		if (age % 100 == 50 && !ArkiPresence.isManaged(getUuid()) && !com.worldbetween.war.WarPresence.isManaged(getUuid())) {
			discard();
		}
	}

	/** By night the starlight is in them, and they strike harder. */
	private float power() {
		return getWorld().isNight() ? 1.25f : 1f;
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

	// ---------------------------------------------------------------- magic

	/** The blade of light: a strike three blocks out, a line of light from the core to the target. */
	private void blade(LivingEntity target) {
		ServerWorld world = (ServerWorld) getWorld();
		Vec3d from = getPos().add(0, getHeight() * 0.6, 0);
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		for (int i = 0; i <= 8; i++) {
			Vec3d p = from.lerp(to, i / 8.0);
			world.spawnParticles(BLADE, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
		}
		world.spawnParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 1, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 6, 0.2, 0.2, 0.2, 0.05);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1f, 1.4f);
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.HOSTILE, 1f, 1.6f);
		float damage = (float) getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * power();
		target.damage(getDamageSources().indirectMagic(this, this), damage);
		swingHand(Hand.MAIN_HAND);
	}

	/** A burst of light that throws back everyone hostile close by. */
	private void burst() {
		ServerWorld world = (ServerWorld) getWorld();
		Vec3d at = getPos().add(0, getHeight() * 0.5, 0);
		for (int i = 0; i < 24; i++) {
			double angle = i * Math.PI * 2 / 24;
			world.spawnParticles(BURST, at.x + Math.cos(angle) * 1.6, at.y, at.z + Math.sin(angle) * 1.6, 1, 0, 0.1, 0, 0);
		}
		world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 30, 0.3, 0.3, 0.3, 0.25);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 1.2f, 1.3f);
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.2f, 1.2f);
		for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, new Box(getBlockPos()).expand(3.5),
				e -> e != this && e.isAlive() && (e == getTarget() || isOutlaw(e) || WarRules.isEnemy(this, e)))) {
			Vec3d push = near.getPos().subtract(getPos()).multiply(1, 0, 1).normalize().multiply(1.6 * power());
			near.damage(getDamageSources().indirectMagic(this, this), 2f);
			near.addVelocity(push.x, 0.5, push.z);
			near.velocityModified = true;
		}
	}

	/** Lets one sphere of the volley go, at the target. */
	private void orb(LivingEntity target) {
		ArkiOrbEntity orb = new ArkiOrbEntity(WorldBetweenEntities.ARKI_ORB, getWorld());
		orb.setOwner(this);
		orb.setTarget(target);
		orb.setDamage(5f * power());
		Vec3d from = getPos().add((random.nextDouble() - 0.5) * 0.8, getHeight() * 0.7, (random.nextDouble() - 0.5) * 0.8);
		orb.refreshPositionAndAngles(from.x, from.y, from.z, 0, 0);
		Vec3d toward = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(from).normalize();
		orb.setVelocity(toward.x * 0.5 + (random.nextDouble() - 0.5) * 0.3, toward.y * 0.5 + 0.15, toward.z * 0.5 + (random.nextDouble() - 0.5) * 0.3);
		getWorld().spawnEntity(orb);
		playSound(SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, 1f, 1.5f + random.nextFloat() * 0.3f);
	}

	/**
	 * How an Arki fights: the blade when it is ready (in close, strike, out again), the burst when someone
	 * gets too close, and now and then a volley; while the blade gathers again, keep the distance.
	 */
	private class MagicGoal extends Goal {
		private int bladeCooldown;
		private int burstCooldown = 40;
		private int volleyCooldown = 60;
		private int orbsLeft;

		MagicGoal() {
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			LivingEntity target = getTarget();
			return target != null && target.isAlive() && fleeing == 0;
		}

		@Override
		public boolean shouldContinue() {
			return canStart() && squaredDistanceTo(getTarget()) < 40 * 40;
		}

		@Override
		public void stop() {
			getNavigation().stop();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = getTarget();
			if (target == null) {
				return;
			}
			getLookControl().lookAt(target, 30f, 30f);
			double distance = Math.sqrt(squaredDistanceTo(target));
			boolean sees = canSee(target);
			bladeCooldown--;
			burstCooldown--;
			volleyCooldown--;
			if (orbsLeft > 0) {
				if (age % 5 == 0) {
					orb(target);
					orbsLeft--;
				}
			} else if (volleyCooldown <= 0 && sees && distance > 4 && distance < 20) {
				orbsLeft = 3 + random.nextInt(3);
				volleyCooldown = 300 + random.nextInt(100); // a rare volley: fifteen to twenty seconds
				((ServerWorld) getWorld()).spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 20, 0.4, 0.4, 0.4, 0.05);
				playSound(SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1f, 1.5f);
			}
			if (burstCooldown <= 0 && distance < 2.8) {
				burst();
				burstCooldown = 160;
			}
			if (bladeCooldown <= 0) {
				if (distance <= BLADE_REACH && sees) {
					blade(target);
					bladeCooldown = 60 + random.nextInt(21); // three to four seconds for the light to gather again
					Vec3d away = getPos().subtract(target.getPos()).multiply(1, 0, 1).normalize().multiply(7);
					Vec3d back = target.getPos().add(away).add(0, 2, 0);
					getNavigation().startMovingTo(back.x, back.y, back.z, 1.5);
				} else if (age % 5 == 0) {
					// In to the blade's reach and no further: a fist or a sword reaches less far.
					Vec3d from = target.getPos().add(0, target.getHeight() * 0.6, 0);
					Vec3d to = from.add(getPos().subtract(from).normalize().multiply(2.7));
					getNavigation().startMovingTo(to.x, to.y, to.z, 1.4);
				}
				return;
			}
			// The blade is gathering: hang back, out of reach of a sword, but in sight.
			if (age % 10 == 0) {
				if (distance < 6) {
					Vec3d away = getPos().subtract(target.getPos()).multiply(1, 0, 1).normalize().multiply(7);
					Vec3d to = target.getPos().add(away).add(0, 2 + random.nextInt(2), 0);
					getNavigation().startMovingTo(to.x, to.y, to.z, 1.3);
				} else if (distance > 11) {
					getNavigation().startMovingTo(target.getX(), target.getY() + 3, target.getZ(), 1.1);
				}
			}
		}
	}

	/** Badly hurt, an Arki goes up and away for a while. */
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

	/** Flies after its band, above the ground or the sea. */
	private class TravelGoal extends Goal {
		TravelGoal() {
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			return destination != null && getTarget() == null && getBlockPos().getSquaredDistance(destination) > 16;
		}

		@Override
		public boolean shouldContinue() {
			return canStart();
		}

		@Override
		public void tick() {
			if (age % 20 != 0) {
				return;
			}
			Vec3d toward = Vec3d.ofCenter(destination).subtract(getPos());
			double distance = toward.horizontalLength();
			Vec3d step = distance > 16 ? getPos().add(toward.multiply(16 / distance)) : Vec3d.ofCenter(destination);
			int ground = getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(step.x), (int) Math.floor(step.z));
			getNavigation().startMovingTo(step.x, Math.max(ground + 4, destination.getY()), step.z, 1.0);
		}
	}

	/** Hovers over its work: the crystals, the lens, the pool. */
	private class WorkGoal extends Goal {
		WorkGoal() {
			setControls(EnumSet.of(Control.MOVE, Control.LOOK));
		}

		@Override
		public boolean canStart() {
			return workSpot != null && getTarget() == null;
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
			return anchor != null && destination == null && getTarget() == null && stall.customer() == null && --cooldown <= 0;
		}

		@Override
		public boolean shouldContinue() {
			return !getNavigation().isIdle() && getTarget() == null && stall.customer() == null;
		}

		@Override
		public void start() {
			cooldown = 80 + random.nextInt(120);
			// Back home after a fight, if it strayed.
			if (getBlockPos().getSquaredDistance(anchor) > (anchorRadius + 8) * (anchorRadius + 8)) {
				getNavigation().startMovingTo(anchor.getX() + 0.5, anchor.getY() + 2, anchor.getZ() + 0.5, 1.0);
				return;
			}
			double x = anchor.getX() + 0.5 + (random.nextDouble() - 0.5) * anchorRadius * 2;
			double z = anchor.getZ() + 0.5 + (random.nextDouble() - 0.5) * anchorRadius * 2;
			double y = anchor.getY() + 1 + random.nextDouble() * 3;
			getNavigation().startMovingTo(x, y, z, 0.6);
		}
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		return super.canTarget(target) && !(target instanceof ArkiEntity);
	}

	@Override
	public boolean isTeammate(Entity other) {
		return other instanceof ArkiEntity || super.isTeammate(other);
	}
}
