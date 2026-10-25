package com.worldbetween.entity.arki;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/** A sphere of the Arki's magic: it drifts off, then finds its mark, and bursts in violet light. */
public class ArkiOrbEntity extends ProjectileEntity {
	private static final DustParticleEffect GLOW = new DustParticleEffect(new Vector3f(0.6f, 0.4f, 1f), 1.4f);
	private static final DustParticleEffect CORE = new DustParticleEffect(new Vector3f(0.45f, 0.35f, 1f), 2.2f);
	private static final int LIFE = 100;
	private static final double SPEED = 0.55;

	private UUID target;
	private float damage = 4f;

	public ArkiOrbEntity(EntityType<? extends ProjectileEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
	}

	public void setTarget(LivingEntity target) {
		this.target = target.getUuid();
	}

	public void setDamage(float damage) {
		this.damage = damage;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		World world = getWorld();
		if (world.isClient()) {
			world.addParticle(CORE, getX(), getY(), getZ(), 0, 0, 0);
			world.addParticle(GLOW, getX() + (random.nextDouble() - 0.5) * 0.3, getY() + (random.nextDouble() - 0.5) * 0.3,
					getZ() + (random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
			if (age % 3 == 0) {
				world.addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
			}
			return;
		}
		if (age > LIFE) {
			fizzle();
			return;
		}
		// After a moment of drifting, it turns towards its mark.
		if (age > 6 && target != null && ((ServerWorld) world).getEntity(target) instanceof LivingEntity mark && mark.isAlive()) {
			Vec3d toward = mark.getPos().add(0, mark.getHeight() * 0.5, 0).subtract(getPos()).normalize().multiply(SPEED);
			setVelocity(getVelocity().lerp(toward, 0.18));
		}
		HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
		if (hit.getType() != HitResult.Type.MISS) {
			hitOrDeflect(hit);
		}
		if (isRemoved()) {
			return;
		}
		Vec3d velocity = getVelocity();
		setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
		ProjectileUtil.setRotationFromVelocity(this, 0.5f);
	}

	@Override
	protected boolean canHit(Entity entity) {
		return super.canHit(entity) && !(entity instanceof ArkiEntity) && !(entity instanceof ArkiOrbEntity);
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);
		Entity owner = getOwner();
		hit.getEntity().damage(getDamageSources().indirectMagic(this, owner), damage);
		fizzle();
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		super.onBlockHit(hit);
		fizzle();
	}

	private void fizzle() {
		if (getWorld() instanceof ServerWorld world) {
			world.spawnParticles(GLOW, getX(), getY(), getZ(), 10, 0.25, 0.25, 0.25, 0);
			world.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.08);
			world.playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 0.8f, 1.6f);
		}
		discard();
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}
}
