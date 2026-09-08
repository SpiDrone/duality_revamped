package net.spidrotech.duality;

import net.minecraft.util.Mth;

/**
 * Central home for ability timing/scaling formulas that combine several inputs into one number
 * (charge time, cooldown, whatever) - built so a formula can be tuned or entirely reshaped in
 * one place without hunting through the Ability class that happens to use it. Add new sections
 * here as more abilities need this kind of multi-input math, rather than each Ability computing
 * its own inline.
 *
 * ================================================================== orb effort
 * Orb's charge time is driven by an abstract "effort" score rather than a flat tick formula:
 * distance, dimension-crossing, and passenger count all ADD UP into a total effort cost, and
 * then ORBING_PROFICIENCY and ORBCHARGEREDUCTION each REDUCE how much of that effort remains -
 * but as proportional/diminishing multipliers, not flat subtraction, so a high-effort trip
 * (e.g. many passengers to a far dimension) still costs meaningfully more than a trivial one
 * even at max stats, rather than both converging to the same tiny floor. Final tick count is
 * just the remaining effort directly (1 effort point = 1 tick), clamped to a sane range.
 */
public final class AbilityCalculations {
	private AbilityCalculations() {
	}

	// ---- tunable knobs ----
	private static final double ORB_EFFORT_PER_BLOCK = 6.0;
	private static final double ORB_DIMENSION_CROSSING_EFFORT = 200.0;
	private static final double ORB_EFFORT_PER_PASSENGER = 300.0;
	// Multiplicative, not subtractive - level 1 applies no reduction at all; level 10 leaves
	// roughly a fifth of the base effort remaining. Interpolated linearly between those two
	// data points, matching the two-point tuning approach already used elsewhere in this mod.
	private static final double ORB_PROFICIENCY_MULTIPLIER_LEVEL_1 = 1.0;
	private static final double ORB_PROFICIENCY_MULTIPLIER_LEVEL_10 = 0.22;
	// Diminishing-returns curve (half-value style, same shape as vanilla armor toughness):
	// multiplier = HALF_VALUE / (HALF_VALUE + statValue). At statValue=0, multiplier=1 (no
	// reduction). As statValue grows the multiplier approaches (but never reaches) 0 - smooth
	// and naturally clamps itself without ever needing a manual floor/ceiling check.
	private static final double ORB_CHARGE_REDUCTION_HALF_VALUE = 200.0;
	private static final double ORB_MIN_CHARGE_TICKS = 20; // 1s floor
	private static final double ORB_MAX_CHARGE_TICKS = 30 * 20; // 600 - 30s ceiling

	/** The raw, pre-reduction effort cost of a prospective Orb trip - purely additive, since
	 *  "how much is being asked for" is naturally a sum of its parts. Reduction (see
	 *  orbProficiencyMultiplier / orbChargeReductionMultiplier) is where the non-linear math
	 *  actually happens, not here. */
	public static double orbBaseEffort(double distanceBlocks, boolean crossingDimensions, int passengerCount) {
		double effort = distanceBlocks * ORB_EFFORT_PER_BLOCK;
		if (crossingDimensions) {
			effort += ORB_DIMENSION_CROSSING_EFFORT;
		}
		effort += passengerCount * ORB_EFFORT_PER_PASSENGER;
		return effort;
	}

	/** 1.0 (no reduction) at level 1, down to ORB_PROFICIENCY_MULTIPLIER_LEVEL_10 at level 10 -
	 *  multiply this against base effort, don't subtract it. */
	public static double orbProficiencyMultiplier(int orbingProficiencyLevel) {
		double t = Mth.clamp((orbingProficiencyLevel - 1) / 9.0, 0.0, 1.0);
		return Mth.lerp(t, ORB_PROFICIENCY_MULTIPLIER_LEVEL_1, ORB_PROFICIENCY_MULTIPLIER_LEVEL_10);
	}

	/** Diminishing-returns multiplier from the flat ORBCHARGEREDUCTION stat - see class doc for
	 *  the curve shape. Never goes negative or needs an external clamp, unlike flat subtraction. */
	public static double orbChargeReductionMultiplier(double orbChargeReductionStatValue) {
		double stat = Math.max(0.0, orbChargeReductionStatValue);
		return ORB_CHARGE_REDUCTION_HALF_VALUE / (ORB_CHARGE_REDUCTION_HALF_VALUE + stat);
	}

	/** The full pipeline: base effort in, both multiplicative reductions applied, clamped to a
	 *  sane tick range. This is the one method OrbAbility's charge-time supplier should call. */
	public static double orbChargeTicks(double distanceBlocks, boolean crossingDimensions, int passengerCount, int orbingProficiencyLevel, double orbChargeReductionStatValue) {
		double baseEffort = orbBaseEffort(distanceBlocks, crossingDimensions, passengerCount);
		double remainingEffort = baseEffort * orbProficiencyMultiplier(orbingProficiencyLevel) * orbChargeReductionMultiplier(orbChargeReductionStatValue);
		return Mth.clamp(remainingEffort, ORB_MIN_CHARGE_TICKS, ORB_MAX_CHARGE_TICKS);
	}
}
