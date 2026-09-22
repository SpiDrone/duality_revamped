package net.spidrotech.duality.abilities.teleportation;

import org.slf4j.Logger;

import net.spidrotech.duality.init.DualityModParticleTypes;
import net.spidrotech.duality.init.DualityModAttributes;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.AbilityContext;
import net.spidrotech.duality.AbilityCondition;
import net.spidrotech.duality.AbilityCalculations;
import net.spidrotech.duality.Ability;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.SimpleParticleType;

import java.util.Set;

import com.mojang.logging.LogUtils;

/**
 * The angel's "Orb" - a channeled teleport, with a full level-gated progression system driven
 * by DualityModAttributes.ORBING_PROFICIENCY (a 1-10 attribute):
 *
 *   Level 1  - up to 100 blocks, Overworld only, no passengers
 *   Level 3  - unlocks Upper Regions (Heaven) travel; can bring 1 passenger (Overworld only)
 *   Level 5  - unlocks the Underworld; passengers can now go to Heaven/Underworld too
 *   Level 10 - any dimension (including Nether/End), unlimited range
 *
 * TIME FORMULA: delegated entirely to AbilityCalculations#orbChargeTicks (see that class for
 * the actual effort/reduction math) - this class is only responsible for gathering the inputs
 * (distance, dimension-crossing, passenger count, proficiency level, ORBCHARGEREDUCTION) and
 * handing them off, not computing the formula itself.
 *
 * After a successful teleport, this ability stays ACTIVE for 1 second purely to spawn a burst
 * of orb particles at the arrival point (see onTick) - it does nothing else during that window.
 *
 * DESTINATION SELECTION: targetPos/destinationLevel are set exclusively by TeleportNetwork's
 * confirm-selection handler (see that class) once the whitelighter has picked a destination via
 * the teleport picker (see the client.Teleport* classes) - see HAS_DESTINATION below.
 *
 * PASSENGERS: can be selected before confirming a destination (bundled into the confirm
 * payload) OR added mid-charge (see TeleportNetwork#handleTogglePassenger, which calls
 * AbilityManager#addTarget on the live running context). Either path is validated against
 * canBringPassengers() - selecting/adding more than the caster can actually carry either
 * refuses the addition (pre-charge) or cancels the whole charge with feedback (mid-charge).
 *
 * CLIENT NOTIFICATION: notifyOrbFinished sends TeleportNetwork.OrbFinishedPayload from every
 * point where a charge genuinely ends - a blocked activation (onActivate's two early returns),
 * an interrupted charge (onChargeInterrupted), and natural completion of the post-teleport
 * particle window (onDeactivate). Without this, the client's picker stays LOCKED forever after
 * a successful (or blocked) teleport, since nothing else ever told it the attempt was over.
 *
 * No chat messages here on purpose - hook the TODO spots up to your own visual indicators.
 * Register once at startup - see DualityAbilities#registerAll.
 *
 * REUSE: not final, and takes (id, allowHeaven) via a protected constructor, specifically so
 * ShimmerAbility (a demon power, see that class) can reuse everything here unchanged except
 * Heaven access (always denied, regardless of level - Heaven is whitelighters-only) and the
 * charge visual (overrides spawnChargeVisual instead of orb particles). Every other rule -
 * range, Underworld access, passengers, the whole progression table - stays identical because
 * it's the literal same code path, not a fork of it.
 */
public class OrbAbility extends Ability {
	// TEMP DEBUG - confirms the particle/activation pipeline is actually being reached. Remove
	// all LOGGER.info calls in this file once particles are confirmed working.
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "orb_normal");
	private static final double DEFAULT_LOOK_DISTANCE = 32; // defensive fallback only - see resolveDestination
	private static final double POST_TELEPORT_PARTICLE_TICKS = 20; // 1s of particles after arriving
	// Kept in sync with client.TeleportPickerInputHandler's client-side hint - this is the one
	// that actually matters, since the client is never trusted for anything that affects
	// gameplay. A passenger who wanders further than this away mid-charge is dropped from the
	// cast entirely.
	private static final double PASSENGER_MAX_DISTANCE = 5.0;
	// ---- progression thresholds, all keyed off ORBING_PROFICIENCY (1-10) ----
	private static final int LEVEL_HEAVEN_ACCESS = 3;
	private static final int LEVEL_PASSENGER_UNLOCK = 3; // can bring 1 passenger, Overworld-only destinations
	private static final int LEVEL_UNDERWORLD_ACCESS = 5;
	private static final int LEVEL_PASSENGER_ANY_REALM = 5; // passengers can now go to Heaven/Underworld too
	private static final int LEVEL_ANY_DIMENSION = 10; // Nether/End/anything, unlimited range
	// TODO: verify these against your actual dimension IDs.
	private static final ResourceKey<Level> HEAVEN_KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("duality", "upper_regions"));
	private static final ResourceKey<Level> UNDERWORLD_KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("duality", "underworld"));
	// ================================================================== cast conditions
	// HAS_DESTINATION/CAN_BRING_PASSENGERS are heaven-independent, so they stay static and
	// shared. IN_RANGE has to be an instance field (not static like the other two) because it
	// needs to call the instance-level isDestinationInRange overload below, which is the one
	// that actually knows this particular ability's allowHeaven.
	private static final AbilityCondition HAS_DESTINATION = ctx -> ctx.targetPos().isPresent();
	private static final AbilityCondition CAN_BRING_PASSENGERS = ctx -> {
		LivingEntity caster = ctx.caster();
		ServerLevel destLevel = ctx.get("destinationLevel", (ServerLevel) null);
		ResourceKey<Level> destDim = destLevel != null ? destLevel.dimension() : caster.level().dimension();
		return canBringPassengers(caster, destDim, ctx.targets().size());
	};
	private final boolean allowHeaven;
	private final AbilityCondition inRange;

	public OrbAbility() {
		this(ID, true);
	}

	/** allowHeaven=false is the ONLY behavior difference ShimmerAbility configures via this
	 *  constructor - everything else (range, Underworld access, passengers) is identical because
	 *  it's the same code, not a copy of it.
	 *
	 *  inRange is assigned here rather than as a field initializer specifically so its lambda
	 *  can close over this.allowHeaven - a field initializer runs before allowHeaven above it is
	 *  actually assigned (that only happens on the next line, in the constructor body), which
	 *  javac's definite-assignment check rejects even though the lambda body itself only runs
	 *  much later, at cast-condition-check time. */
	protected OrbAbility(ResourceLocation id, boolean allowHeaven) {
		super(id, AbilityType.CHANNELED);
		this.allowHeaven = allowHeaven;
		this.inRange = ctx -> {
			LivingEntity caster = ctx.caster();
			Vec3 destPos = ctx.targetPos().orElse(null);
			if (destPos == null)
				return false;
			ServerLevel destLevel = ctx.get("destinationLevel", (ServerLevel) null);
			ResourceKey<Level> destDim = destLevel != null ? destLevel.dimension() : caster.level().dimension();
			return isDestinationInRange(caster, destDim, destPos, this.allowHeaven);
		};
		setChargeTime(this::computeChargeTicks);
		setDuration(ctx -> Boolean.TRUE.equals(ctx.get("orbSucceeded", Boolean.FALSE)) ? POST_TELEPORT_PARTICLE_TICKS : 0.0);
		setInterruptOnDamage(true);
		addCastCondition(HAS_DESTINATION.withMessage(Component.literal("No orb destination selected.")));
		addCastCondition(inRange.withMessage(Component.literal("That destination is out of range.")));
		addCastCondition(CAN_BRING_PASSENGERS.withMessage(Component.literal("You can't bring that many passengers there.")));
		addMaintainCondition(CAN_BRING_PASSENGERS);
	}

	@Override
	public void onChargeStart(AbilityContext ctx) {
		LOGGER.info("[duality] Orb charge STARTED for {}", ctx.caster().getName().getString());
	}

	@Override
	public void onChargeTick(AbilityContext ctx, float progress) {
		prunePassengersOutOfRange(ctx, ctx.caster());
		spawnChargeVisual(ctx);
	}

	@Override
	public void onChargeInterrupted(AbilityContext ctx) {
		LOGGER.info("[duality] Orb charge INTERRUPTED for {}", ctx.caster().getName().getString());
		notifyOrbFinished(ctx.caster());
	}

	@Override
	public void onActivate(AbilityContext ctx) {
		LivingEntity caster = ctx.caster();
		if (!(caster.level() instanceof ServerLevel currentLevel))
			return;
		int level = orbingLevel(caster);
		Vec3 destination = resolveDestination(ctx, caster, level);
		ServerLevel destLevel = ctx.get("destinationLevel", currentLevel);
		LOGGER.info("[duality] Orb ACTIVATING (teleport) for {} - destination={} destDim={} passengers={}", caster.getName().getString(), destination, destLevel.dimension().location(), ctx.targets().size());
		// Explicit allowHeaven arg, NOT the ambient 3-arg overload below (which is hardcoded
		// true) - an unqualified call here would silently resolve to that static overload
		// instead of consulting this.allowHeaven, since Java never dispatches a static method
		// virtually. That would let ShimmerAbility's own recheck here wave through a Heaven
		// destination it should still refuse post-charge, even though the pre-charge cast
		// condition (which DOES use this.allowHeaven, via the inRange field) already caught it.
		if (!isDestinationInRange(caster, destLevel.dimension(), destination, this.allowHeaven)) {
			LOGGER.info("[duality] Orb BLOCKED for {}: destination unreachable", caster.getName().getString());
			notifyOrbFinished(caster);
			return;
		}
		if (!canBringPassengers(caster, destLevel.dimension(), ctx.targets().size())) {
			LOGGER.info("[duality] Orb BLOCKED for {}: can't bring {} passenger(s) to {} at level {}", caster.getName().getString(), ctx.targets().size(), destLevel.dimension().location(), level);
			notifyOrbFinished(caster);
			return;
		}
		ctx.set("orbSucceeded", true);
		prunePassengersOutOfRange(ctx, caster);
		teleport(caster, destLevel, destination);
		int maxPassengers = maxPassengers(caster);
		int total = Math.max(1, Math.min(maxPassengers, ctx.targets().size()));
		int i = 0;
		for (Entity passenger : ctx.targets()) {
			if (i >= maxPassengers)
				break;
			double angle = (2 * Math.PI * i) / total;
			Vec3 offset = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(1.5);
			teleport(passenger, destLevel, destination.add(offset));
			i++;
		}
	}

	@Override
	public void onTick(AbilityContext ctx) {
		spawnChargeVisual(ctx);
	}

	@Override
	public void onDeactivate(AbilityContext ctx) {
		notifyOrbFinished(ctx.caster());
	}

	// ================================================================== per-ability policy
	/** Whether THIS ability (Orb, or a subclass like ShimmerAbility) is allowed anywhere near
	 *  Heaven, regardless of level - see canCross. Exposed so ability-agnostic callers (the
	 *  teleport picker, TeleportNetwork) can ask the right question about whichever teleport
	 *  ability is actually selected instead of assuming Orb's own answer. */
	public final boolean allowsHeaven() {
		return allowHeaven;
	}

	/** The LOCKED marker's glow once a destination is confirmed - Orb's own whitelighter pulse by
	 *  default. ShimmerAbility overrides this rather than reusing "whitelighter" styling for a
	 *  demon power. See client.TeleportBrowsingWatcher, which reads this once per ability
	 *  selection. */
	public TeleportGlowStyle glowStyle() {
		return TeleportGlowStyle.PULSE_WHITELIGHTER;
	}

	private static void notifyOrbFinished(LivingEntity caster) {
		if (caster instanceof ServerPlayer player) {
			LOGGER.info("[duality] Notifying client that Orb has finished for {}", player.getName().getString());
			PacketDistributor.sendToPlayer(player, new TeleportNetwork.OrbFinishedPayload());
		}
	}

	// ================================================================== passengers
	private static void prunePassengersOutOfRange(AbilityContext ctx, LivingEntity caster) {
		ctx.targets().removeIf(target -> caster.position().distanceTo(target.position()) > PASSENGER_MAX_DISTANCE);
	}

	public static int maxPassengers(LivingEntity caster) {
		return orbingLevel(caster) >= LEVEL_PASSENGER_UNLOCK ? 1 : 0;
	}

	public static boolean canBringPassengers(LivingEntity caster, ResourceKey<Level> destDimension, int passengerCount) {
		if (passengerCount <= 0)
			return true;
		if (passengerCount > maxPassengers(caster))
			return false;
		int level = orbingLevel(caster);
		OrbRealm destRealm = classify(destDimension);
		return level >= LEVEL_PASSENGER_ANY_REALM || destRealm == OrbRealm.OVERWORLD;
	}

	// ================================================================== charge visual
	/** Orb sparkle particles while charging. ShimmerAbility overrides this entirely (a flicker
	 *  effect instead) rather than sharing any of this method's body. */
	protected void spawnChargeVisual(AbilityContext ctx) {
		LivingEntity caster = ctx.caster();
		if (!(caster.level() instanceof ServerLevel level) || level.getGameTime() % 3 != 0)
			return;
		double x = caster.getX(), y = caster.getY(), z = caster.getZ();
		int particleAmount = 8;
		double particleRadius = 4;
		RandomSource random = RandomSource.create();
		SimpleParticleType particleType = (SimpleParticleType) DualityModParticleTypes.ORBS.get();
		for (int index0 = 0; index0 < particleAmount; index0++) {
			double px = x + Mth.nextDouble(random, -0.1, 0.1) * particleRadius;
			double py = y + Mth.nextDouble(random, 0, 1) * 2;
			double pz = z + Mth.nextDouble(random, -0.1, 0.1) * particleRadius;
			level.sendParticles(particleType, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	// ================================================================== timing
	private double computeChargeTicks(AbilityContext ctx) {
		LivingEntity caster = ctx.caster();
		if (!(caster.level() instanceof ServerLevel currentLevel))
			return AbilityCalculations.orbChargeTicks(0, false, 0, 1, 0);
		int level = orbingLevel(caster);
		Vec3 destination = resolveDestination(ctx, caster, level);
		ServerLevel destLevel = ctx.get("destinationLevel", currentLevel);
		boolean crossingDimensions = destLevel.dimension() != currentLevel.dimension();
		double distance = crossingDimensions ? 0 : caster.position().distanceTo(destination);
		double orbChargeReduction = caster.getAttributeValue(DualityModAttributes.ORBCHARGEREDUCTION);
		return AbilityCalculations.orbChargeTicks(distance, crossingDimensions, ctx.targets().size(), level, orbChargeReduction);
	}

	// ================================================================== progression helpers
	private static int orbingLevel(LivingEntity caster) {
		return (int) Math.round(caster.getAttributeValue(DualityModAttributes.ORBING_PROFICIENCY));
	}

	private static double rangeForLevel(int level) {
		if (level >= LEVEL_ANY_DIMENSION)
			return Double.MAX_VALUE;
		return 100 * Math.pow(2, level - 1);
	}

	/** Orb-specific convenience (always allowHeaven=true) for callers that genuinely mean Orb and
	 *  nothing else. The generalized teleport picker (client.TeleportBrowsingWatcher) does NOT use
	 *  this any more - it holds the actual ability instance for whichever teleport ability is
	 *  selected and calls the 4-arg overload below with that instance's own allowsHeaven(), so a
	 *  demon's Shimmer picker correctly shows Heaven as unreachable instead of borrowing Orb's
	 *  always-true policy. */
	public static boolean isDestinationInRange(LivingEntity caster, ResourceKey<Level> destDimension, Vec3 destPosition) {
		return isDestinationInRange(caster, destDimension, destPosition, true);
	}

	public static boolean isDestinationInRange(LivingEntity caster, ResourceKey<Level> destDimension, Vec3 destPosition, boolean allowHeaven) {
		int level = orbingLevel(caster);
		ResourceKey<Level> fromDim = caster.level().dimension();
		if (!canCross(fromDim, destDimension, level, allowHeaven))
			return false;
		if (destDimension != fromDim)
			return true;
		double maxRange = rangeForLevel(level);
		return caster.position().distanceTo(destPosition) <= maxRange;
	}

	private static Vec3 resolveDestination(AbilityContext ctx, LivingEntity caster, int level) {
		double maxRange = rangeForLevel(level);
		return ctx.targetPos().orElseGet(() -> caster.position().add(caster.getLookAngle().scale(Math.min(maxRange, DEFAULT_LOOK_DISTANCE))));
	}

	private enum OrbRealm {
		OVERWORLD, HEAVEN, UNDERWORLD, OTHER
	}

	private static OrbRealm classify(ResourceKey<Level> dimension) {
		if (dimension == Level.OVERWORLD)
			return OrbRealm.OVERWORLD;
		if (dimension == HEAVEN_KEY)
			return OrbRealm.HEAVEN;
		if (dimension == UNDERWORLD_KEY)
			return OrbRealm.UNDERWORLD;
		return OrbRealm.OTHER;
	}

	private static boolean pairIs(OrbRealm a, OrbRealm b, OrbRealm x, OrbRealm y) {
		return (a == x && b == y) || (a == y && b == x);
	}

	private static boolean canCross(ResourceKey<Level> fromDim, ResourceKey<Level> toDim, int level, boolean allowHeaven) {
		if (fromDim == toDim)
			return true;
		OrbRealm from = classify(fromDim);
		OrbRealm to = classify(toDim);
		// Checked even at max level - "any dimension" was never meant to include a Heaven this
		// ability isn't allowed into at all (Shimmer), as opposed to just not being high enough
		// level yet (Orb below LEVEL_HEAVEN_ACCESS, which the check further down still handles).
		if (!allowHeaven && (from == OrbRealm.HEAVEN || to == OrbRealm.HEAVEN))
			return false;
		if (level >= LEVEL_ANY_DIMENSION)
			return true;
		if (from == OrbRealm.OTHER || to == OrbRealm.OTHER)
			return false;
		if (pairIs(from, to, OrbRealm.OVERWORLD, OrbRealm.HEAVEN))
			return level >= LEVEL_HEAVEN_ACCESS;
		if (pairIs(from, to, OrbRealm.OVERWORLD, OrbRealm.UNDERWORLD) || pairIs(from, to, OrbRealm.HEAVEN, OrbRealm.UNDERWORLD))
			return level >= LEVEL_UNDERWORLD_ACCESS;
		return false;
	}

	private void teleport(Entity entity, ServerLevel level, Vec3 pos) {
		entity.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), entity.getYRot(), entity.getXRot());
	}
}