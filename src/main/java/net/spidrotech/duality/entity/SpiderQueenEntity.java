package net.spidrotech.duality.entity;

import net.spidrotech.duality.entity.ai.DemonSpiderChaseGoal;
import net.spidrotech.duality.entity.ai.DemonSpiderLeapGoal;
import net.spidrotech.duality.entity.ai.DemonSpiderNavigation;
import net.spidrotech.duality.entity.ai.DemonSpiderSpitGoal;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The demonic spider. MCreator owns this entity's registration, model, texture and renderer
 * wiring (elements/SpiderQueen.mod.json); everything below the generated skeleton - the
 * pursuit/jump/climb state machine, the synced animation state and the spit attack hook - is
 * hand-written and will be flattened if you regenerate this element in MCreator. Same
 * "MCreator owns the plumbing, this class owns the behavior" split as StagnantVisualEntity.
 *
 * BEHAVIOR: once it has a target it runs it down - pathing at it (DemonSpiderChaseGoal, which
 * also hunts for another route when the direct one is blocked), telegraphing and then leaping
 * over gaps or up ledges, and sticking to walls to climb when it collides with one
 * (DemonSpiderNavigation). Its only offense is a ranged spit.
 *
 * ANIMATION: the client never guesses what the spider is doing - the server drives
 * DATA_ANIM_STATE and the client mirrors it into the AnimationState fields below, which
 * creatures/DemonSpiderModel reads in setupAnim. Tuning constants for the jump telegraph,
 * landing lead and chase speed are gathered at the top of this class and in TUNING.md.
 */
public class SpiderQueenEntity extends Monster {
	/**
	 * How long the spider crouches and plays jump_start before it actually leaves the ground.
	 * Matches the 0.5s length of the jump_start animation so the wind-up reads as one motion
	 * that ends exactly on the launch rather than being cut off partway.
	 */
	public static final int JUMP_WINDUP_TICKS = 5;

	/**
	 * How far ahead of touching down the spider switches to jump_land. jump_land is 0.6s (12
	 * ticks) long, so starting it 8 ticks early means the spider is already reaching its legs
	 * out as it makes contact and finishes settling ~4 ticks after - i.e. it lands on its feet
	 * instead of snapping out of the airborne pose.
	 */
	public static final int LAND_ANIM_LEAD_TICKS = 8;

	/** Ticks the landing pose is held in total before handing back to walk/idle. */
	private static final int LAND_ANIM_TICKS = 12;

	/**
	 * Speed multiplier the chase goal paths at, on top of the MOVEMENT_SPEED attribute. The
	 * brief is "slightly slower than the player", i.e. a sprinting player can just barely open
	 * a gap. 0.3 attribute x 1.0 is vanilla-spider chase pace and is the intended starting
	 * point - this is the one number to measure in game and nudge, see TUNING.md.
	 */
	private static final double CHASE_SPEED = 1.1;

	/** How long the spider keeps hunting a target it can't currently see. */
	private static final int TARGET_MEMORY_TICKS = 300;

	/** Ticks between leaps, so the spider punctuates its chase instead of pogo-ing constantly. */
	public static final int LEAP_COOLDOWN_TICKS = 70;

	private static final EntityDataAccessor<Byte> DATA_ANIM_STATE = SynchedEntityData.defineId(SpiderQueenEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_CLIMBING = SynchedEntityData.defineId(SpiderQueenEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_CLIMB_WALL_YAW = SynchedEntityData.defineId(SpiderQueenEntity.class, EntityDataSerializers.FLOAT);

	/**
	 * What the spider is doing, as far as its animation is concerned. Synced as the ordinal
	 * byte of DATA_ANIM_STATE - the server is the only thing that ever decides this, so the
	 * client can switch animations on a plain state change instead of trying to re-derive
	 * "is it jumping?" from position deltas.
	 */
	public enum AnimState {
		GROUND, JUMP_WINDUP, AIRBORNE, LANDING, CLIMBING, SPIT;

		private static final AnimState[] VALUES = values();

		public static AnimState byId(byte id) {
			return id >= 0 && id < VALUES.length ? VALUES[id] : GROUND;
		}
	}

	public final AnimationState idleAnimState = new AnimationState();
	public final AnimationState walkAnimState = new AnimationState();
	public final AnimationState jumpStartAnimState = new AnimationState();
	public final AnimationState jumpIdleAnimState = new AnimationState();
	public final AnimationState jumpLandAnimState = new AnimationState();
	public final AnimationState climbAnimState = new AnimationState();
	public final AnimationState spitAnimState = new AnimationState();

	private AnimState lastClientAnimState = AnimState.GROUND;
	private int stateTicks;
	private int leapCooldown;

	/**
	 * 0 = upright on the floor, 1 = fully rotated onto the wall. Eased every tick on both
	 * sides so the renderer can lerp it with partialTicks and the spider tips onto the wall
	 * over a few frames instead of snapping 90 degrees in one.
	 */
	private float climbAmount;
	private float climbAmountO;

	public SpiderQueenEntity(EntityType<SpiderQueenEntity> type, Level world) {
		super(type, world);
		xpReward = 0;
		setNoAi(false);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ANIM_STATE, (byte) AnimState.GROUND.ordinal());
		builder.define(DATA_CLIMBING, false);
		builder.define(DATA_CLIMB_WALL_YAW, 0f);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new DemonSpiderLeapGoal(this));
		this.goalSelector.addGoal(2, new DemonSpiderSpitGoal(this));
		this.goalSelector.addGoal(3, new DemonSpiderChaseGoal(this, CHASE_SPEED));
		this.goalSelector.addGoal(4, new RandomStrollGoal(this, 1));
		this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
		// Vanilla drops a target after 3s out of sight, which is shorter than it takes to find
		// and walk a route around whatever is blocking the view. The spider still has to see
		// you to start the hunt; it just doesn't forget you the moment you duck behind a floor.
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setUnseenMemoryTicks(TARGET_MEMORY_TICKS));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true).setUnseenMemoryTicks(TARGET_MEMORY_TICKS));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new DemonSpiderNavigation(this, level);
	}

	@Override
	public boolean onClimbable() {
		return this.isClimbingWall();
	}

	@Override
	public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
		// Leaping is its core movement; a long pounce or a drop off a wall must never hurt it.
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide) {
			this.tickServerState();
		} else {
			this.tickClientAnimations();
		}
		this.tickClimbLean();
	}

	// ---------------------------------------------------------------- server state machine

	private void tickServerState() {
		if (this.leapCooldown > 0)
			this.leapCooldown--;
		this.stateTicks++;

		AnimState state = this.getAnimState();
		// Wall-sticking is suppressed mid-leap so a jump that brushes a wall arcs past it
		// instead of the spider grabbing on and killing its own momentum.
		boolean canCling = state == AnimState.GROUND || state == AnimState.CLIMBING || state == AnimState.SPIT;
		boolean clinging = canCling && this.horizontalCollision;
		this.entityData.set(DATA_CLIMBING, clinging);
		if (clinging) {
			Direction wall = this.findWallDirection();
			if (wall != null)
				this.entityData.set(DATA_CLIMB_WALL_YAW, wall.toYRot());
		}

		switch (state) {
			case JUMP_WINDUP, SPIT -> {
				// Both are owned by their goal, which holds the state for a fixed number of
				// ticks and then hands it back. Nothing to do here.
			}
			case AIRBORNE -> {
				if (this.onGround())
					this.setAnimState(AnimState.GROUND);
				else if (this.isAboutToLand())
					this.setAnimState(AnimState.LANDING);
			}
			case LANDING -> {
				if (this.onGround() && this.stateTicks >= LAND_ANIM_TICKS)
					this.setAnimState(AnimState.GROUND);
			}
			case CLIMBING -> {
				if (!clinging)
					this.setAnimState(AnimState.GROUND);
			}
			case GROUND -> {
				if (clinging)
					this.setAnimState(AnimState.CLIMBING);
				else if (!this.onGround() && this.getDeltaMovement().y < 0)
					this.setAnimState(AnimState.AIRBORNE);
			}
		}
	}

	/**
	 * Looks LAND_ANIM_LEAD_TICKS of fall time straight down and reports whether there is
	 * ground inside that window. Using the spider's actual fall speed rather than a fixed
	 * distance keeps the lead time constant whether it is dropping off a kerb or out of a
	 * long leap.
	 */
	private boolean isAboutToLand() {
		Vec3 motion = this.getDeltaMovement();
		if (motion.y >= 0)
			return false;
		double lookAhead = Math.max(0.5, -motion.y * LAND_ANIM_LEAD_TICKS);
		Vec3 from = this.position();
		BlockHitResult hit = this.level()
				.clip(new ClipContext(from, from.add(0, -lookAhead, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() != HitResult.Type.MISS;
	}

	/**
	 * Picks which of the four horizontal neighbours the spider is actually pressed against,
	 * preferring the one it is moving into so that brushing a corner does not flip the model
	 * onto the wrong face.
	 */
	private Direction findWallDirection() {
		BlockPos base = this.blockPosition().above();
		Vec3 motion = this.getDeltaMovement();
		Vec3 heading = motion.horizontalDistanceSqr() > 1.0E-4 ? motion.multiply(1, 0, 1).normalize() : Vec3.directionFromRotation(0, this.getYRot());
		Direction best = null;
		double bestDot = -2;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos pos = base.relative(dir);
			if (!this.level().getBlockState(pos).isFaceSturdy(this.level(), pos, dir.getOpposite()))
				continue;
			double dot = Vec3.atLowerCornerOf(dir.getNormal()).dot(heading);
			if (dot > bestDot) {
				bestDot = dot;
				best = dir;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- jump

	/** True when the spider is allowed to start a new wind-up. */
	public boolean canStartLeap() {
		return this.leapCooldown <= 0 && this.onGround() && this.getAnimState() == AnimState.GROUND;
	}

	public void beginLeapWindup() {
		this.setAnimState(AnimState.JUMP_WINDUP);
		this.leapCooldown = LEAP_COOLDOWN_TICKS;
	}

	/**
	 * Fires the spider along a ballistic arc that lands on {@code target}. Flight time scales
	 * with horizontal distance so short hops stay snappy and long leaps float, and the vertical
	 * kick is solved from that time rather than being a fixed impulse - which is what lets one
	 * jump serve both "hop this ledge" and "clear this gap".
	 */
	public void launchLeapAt(LivingEntity target) {
		double dx = target.getX() - this.getX();
		double dz = target.getZ() - this.getZ();
		double dy = target.getY() - this.getY();
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float flightTicks = (float) Mth.clamp(horizontal * 2.5, 8, 30);
		// MC applies ~0.98 horizontal drag per tick, so an undamped solution undershoots.
		// This scales the horizontal impulse back up by the drag lost over the flight.
		double dragCompensation = 1.0 / Math.max(0.35, Math.pow(0.98, flightTicks * 0.5));
		double vy = dy / flightTicks + 0.5 * 0.08 * flightTicks;

		this.setDeltaMovement(dx / flightTicks * dragCompensation, Mth.clamp(vy, 0.42, 1.2), dz / flightTicks * dragCompensation);
		this.setAnimState(AnimState.AIRBORNE);
		this.hasImpulse = true;
		this.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.spider.step")), 0.6f, 0.6f);
	}

	// ---------------------------------------------------------------- spit

	/**
	 * PLACEHOLDER - the spit itself is not implemented. Everything around it is: the goal
	 * picks the moment, holds the spider still, drives the attack animation and calls this on
	 * the frame the mandibles snap open. Drop the real projectile spawn in here and the attack
	 * is done; nothing else needs to change.
	 *
	 * The obvious home for the real thing is this mod's own projectile stack
	 * (ProjectileRegistry / AbilityProjectileBase) rather than a bespoke entity.
	 */
	public void performSpit(LivingEntity target) {
		this.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.llama.spit")), 1.0f, 0.6f);
	}

	// ---------------------------------------------------------------- state accessors

	public AnimState getAnimState() {
		return AnimState.byId(this.entityData.get(DATA_ANIM_STATE));
	}

	public void setAnimState(AnimState state) {
		if (this.getAnimState() != state) {
			this.entityData.set(DATA_ANIM_STATE, (byte) state.ordinal());
			this.stateTicks = 0;
		}
	}

	public boolean isClimbingWall() {
		return this.entityData.get(DATA_CLIMBING);
	}

	/** Yaw, in degrees, of the direction pointing into the wall the spider is clinging to. */
	public float getClimbWallYaw() {
		return this.entityData.get(DATA_CLIMB_WALL_YAW);
	}

	public float getClimbAmount(float partialTicks) {
		return Mth.lerp(partialTicks, this.climbAmountO, this.climbAmount);
	}

	private void tickClimbLean() {
		this.climbAmountO = this.climbAmount;
		float goal = this.isClimbingWall() ? 1f : 0f;
		this.climbAmount = Mth.approach(this.climbAmount, goal, 0.15f);
	}

	// ---------------------------------------------------------------- client animation

	private void tickClientAnimations() {
		AnimState state = this.getAnimState();
		if (state != this.lastClientAnimState) {
			this.jumpStartAnimState.stop();
			this.jumpIdleAnimState.stop();
			this.jumpLandAnimState.stop();
			this.climbAnimState.stop();
			this.spitAnimState.stop();
			switch (state) {
				case JUMP_WINDUP -> this.jumpStartAnimState.start(this.tickCount);
				case AIRBORNE -> this.jumpIdleAnimState.start(this.tickCount);
				case LANDING -> this.jumpLandAnimState.start(this.tickCount);
				case CLIMBING -> this.climbAnimState.start(this.tickCount);
				case SPIT -> this.spitAnimState.start(this.tickCount);
				case GROUND -> {
					// walk/idle are driven by limb swing in the model, not by this switch.
				}
			}
			this.lastClientAnimState = state;
		}
		this.idleAnimState.animateWhen(state == AnimState.GROUND && this.walkAnimation.speed() < 0.01f, this.tickCount);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);
		compound.putInt("LeapCooldown", this.leapCooldown);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);
		this.leapCooldown = compound.getInt("LeapCooldown");
	}

	@Override
	public SoundEvent getAmbientSound() {
		return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.spider.ambient"));
	}

	@Override
	public void playStepSound(BlockPos pos, BlockState blockIn) {
		this.playSound(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.spider.step")), 0.15f, 1);
	}

	@Override
	public SoundEvent getHurtSound(DamageSource ds) {
		return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.spider.hurt"));
	}

	@Override
	public SoundEvent getDeathSound() {
		return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("entity.spider.death"));
	}

	public static void init(RegisterSpawnPlacementsEvent event) {
	}

	public static AttributeSupplier.Builder createAttributes() {
		AttributeSupplier.Builder builder = Mob.createMobAttributes();
		builder = builder.add(Attributes.MOVEMENT_SPEED, 0.3);
		builder = builder.add(Attributes.MAX_HEALTH, 10);
		builder = builder.add(Attributes.ARMOR, 0);
		builder = builder.add(Attributes.ATTACK_DAMAGE, 3);
		builder = builder.add(Attributes.FOLLOW_RANGE, 16);
		builder = builder.add(Attributes.STEP_HEIGHT, 0.6);
		return builder;
	}
}
