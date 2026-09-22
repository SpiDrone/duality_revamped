package net.spidrotech.duality.abilities.teleportation;

import net.spidrotech.duality.DimensionStackConfig;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Target;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceKey;

import javax.annotation.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * Computes a "general direction" (yaw/pitch, from the viewer's current position) toward a
 * teleport target - shared math for every teleport ability's destination picker, not specific to
 * Orb. Works the same whether the target came from a CharacterWaypoint or a
 * WhitelighterCallRegistry call - both just look like a TeleportMarker by the time this runs.
 *
 * YAW (horizontal): real compass bearing toward the target's raw X/Z, same dimension or not -
 * this is what lets several markers spread out around the player like scattered stars instead of
 * stacking in one spot. NEGATED atan2(dx, dz), matching vanilla's own Entity#lookAt (which
 * computes -atan2(dx, dz)), so it compares directly against the player's look yaw. The minus sign
 * is load-bearing, NOT cosmetic: without it every bearing comes out mirrored, and a "what am I
 * looking at" comparison silently matches the marker on the opposite side of the ring - see
 * client.TeleportMarkerRenderer#bearingFor, where exactly that was a real selection bug.
 *
 * PITCH (vertical): deliberately NOT based on real relative position at all, not even within the
 * same dimension - see DimensionStackConfig#tier. A target in an equal-or-higher tier (same
 * dimension counts as equal) always renders up in the sky, like you're looking into the heavens;
 * a target in a strictly lower tier always renders below the player. This was also a bugfix, not
 * just a style choice: computing real 3D pitch toward a very close target (e.g. a waypoint set
 * right at the player's own feet, which is common while testing) is numerically unstable -
 * atan2(dy, horizontalDist) swings wildly as horizontalDist approaches zero, which is exactly
 * the "marker drifts/grows as I move slightly" symptom this replaced.
 *
 * SIGN CONVENTION (important, previously the source of a real bug): pitch here is
 * POSITIVE-UP, matching TeleportMarkerRenderer's own placement math (dy = sin(pitchRad), so a
 * positive pitch places the marker above eye level). Vanilla's own player pitch
 * (getXRot()/partialTick pitch) is the OPPOSITE convention - POSITIVE-DOWN. Any code comparing
 * a Bearing's pitch against a live look pitch from the player (see closestToLook below) MUST
 * negate one side first, or "looking at" comparisons silently fail for anything not near the
 * horizon - which is exactly what happened before this fix: a sky-tier marker could never
 * register as looked-at, because the two pitches disagreed in sign by up to ~110 degrees.
 */
public final class TeleportDirectionUtil {
	// Fixed sky/ground bands, not a continuous function of real position - see class doc.
	private static final double SKY_BASE_PITCH = 55.0; // degrees up, for equal-or-higher tier targets
	private static final double GROUND_BASE_PITCH = 55.0; // degrees down, for strictly-lower-tier targets
	private static final double PITCH_PER_EXTRA_TIER = 5.0;
	private static final double MAX_PITCH = 80.0; // stops short of straight up/down, where yaw becomes meaningless
	private static final double MIN_HORIZONTAL_DISTANCE_FOR_YAW = 2.0;
	private static final double OVERLAP_THRESHOLD_DEGREES = 6.0;
	private static final double NUDGE_STEP_DEGREES = 8.0;
	// Public - closestToLook now takes its threshold explicitly (different callers may want
	// different tolerances), but this is the sensible default every caller in this codebase
	// currently uses, kept in one place instead of duplicated as a magic number at each call site.
	public static final double LOOK_SELECT_THRESHOLD_DEGREES = 6.0;

	private TeleportDirectionUtil() {
	}

	public record Target<T>(T value, ResourceKey<Level> dimension, Vec3 position) {
	}

	public record Bearing<T>(T value, float yaw, float pitch) {
	}

	public static <T> Bearing<T> resolve(ResourceKey<Level> viewerDimension, Vec3 viewerPos, Target<T> target) {
		double dx = target.position().x - viewerPos.x;
		double dz = target.position().z - viewerPos.z;
		double horizontalDist = Math.sqrt(dx * dx + dz * dz);
		float yaw = horizontalDist < MIN_HORIZONTAL_DISTANCE_FOR_YAW
				? 0f // degenerate case: target is essentially where the viewer is standing
				: (float) (-Mth.atan2(dx, dz) * (180.0 / Math.PI));
		float pitch = (float) verticalPitchForDimensions(viewerDimension, target.dimension());
		return new Bearing<>(target.value(), yaw, pitch);
	}

	/** Equal-or-higher tier (same dimension always counts as equal) -> positive (sky) pitch.
	 *  Strictly lower tier -> negative (ground) pitch. Magnitude grows slightly per tier beyond
	 *  the immediate one, capped at MAX_PITCH either direction. */
	private static double verticalPitchForDimensions(ResourceKey<Level> viewerDimension, ResourceKey<Level> targetDimension) {
		if (viewerDimension == targetDimension) {
			return SKY_BASE_PITCH;
		}
		int viewerTier = DimensionStackConfig.tier(viewerDimension);
		int targetTier = DimensionStackConfig.tier(targetDimension);
		int tierDelta = targetTier - viewerTier;
		if (tierDelta >= 0) {
			return Math.min(MAX_PITCH, SKY_BASE_PITCH + tierDelta * PITCH_PER_EXTRA_TIER);
		} else {
			return Math.max(-MAX_PITCH, -GROUND_BASE_PITCH + (tierDelta + 1) * PITCH_PER_EXTRA_TIER);
		}
	}

	public static <T> List<Bearing<T>> resolveAll(ResourceKey<Level> viewerDimension, Vec3 viewerPos, List<Target<T>> targets) {
		List<Bearing<T>> result = new ArrayList<>();
		for (Target<T> target : targets) {
			result.add(resolve(viewerDimension, viewerPos, target));
		}
		for (int i = 0; i < result.size(); i++) {
			for (int j = 0; j < i; j++) {
				Bearing<T> a = result.get(i);
				Bearing<T> b = result.get(j);
				if (angularDistance(a, b) < OVERLAP_THRESHOLD_DEGREES) {
					double angle = (i * 47) % 360;
					float nudgedYaw = a.yaw() + (float) (Math.cos(Math.toRadians(angle)) * NUDGE_STEP_DEGREES);
					float nudgedPitch = (float) Mth.clamp(a.pitch() + Math.sin(Math.toRadians(angle)) * NUDGE_STEP_DEGREES, -MAX_PITCH, MAX_PITCH);
					result.set(i, new Bearing<>(a.value(), nudgedYaw, nudgedPitch));
				}
			}
		}
		return result;
	}

	/** lookPitch is expected straight from the player (e.g. Minecraft#player.getXRot()), which
	 *  is POSITIVE-DOWN - the opposite of this class's own POSITIVE-UP Bearing pitch (see class
	 *  doc). Negated below before comparing. */
	@Nullable
	public static <T> T closestToLook(List<Bearing<T>> bearings, float lookYaw, float lookPitch, double thresholdDegrees) {
		T best = null;
		double bestDist = thresholdDegrees;
		float lookPitchUpPositive = -lookPitch;
		for (Bearing<T> bearing : bearings) {
			double dYaw = Mth.wrapDegrees(bearing.yaw() - lookYaw);
			double dPitch = bearing.pitch() - lookPitchUpPositive;
			double dist = Math.sqrt(dYaw * dYaw + dPitch * dPitch);
			if (dist < bestDist) {
				bestDist = dist;
				best = bearing.value();
			}
		}
		return best;
	}

	private static double angularDistance(Bearing<?> a, Bearing<?> b) {
		double dYaw = Mth.wrapDegrees(a.yaw() - b.yaw());
		double dPitch = a.pitch() - b.pitch();
		return Math.sqrt(dYaw * dYaw + dPitch * dPitch);
	}
}