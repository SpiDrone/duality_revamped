package net.spidrotech.duality.village;

import com.google.gson.JsonObject;

/**
 * The world's standing on the good/evil axis, persisted to duality_data/world.json.
 *
 * <p>Characters already carry their own duality_score on their character sheet (see
 * DualityDatabaseManager). This is the other half: a score that belongs to the save rather than to
 * anyone in it, and that moves on its own. Every raid a village fails to stop drags it toward evil
 * whether or not a player was there to see it, which is the point - the world has an opinion about
 * what you let happen.
 */
public class WorldDualityState {
	/** Hard bounds on the score. Symmetric, so "how far gone is this world" is just score/LIMIT. */
	public static final double LIMIT = 1000.0;

	private double dualityScore = 0.0;
	/** Last in-game day the background simulation caught up to. */
	private long lastSimulatedDay = 0;
	/** Total days the simulation has ever run, for diagnostics. */
	private long simulatedDayCount = 0;

	public double dualityScore() {
		return dualityScore;
	}

	public void setDualityScore(double score) {
		this.dualityScore = clamp(score);
	}

	/** Negative pushes evil, positive pushes good. Returns the delta actually applied, which is
	 *  smaller than asked for once the score is pinned at a limit. */
	public double addDualityScore(double delta) {
		double before = dualityScore;
		dualityScore = clamp(dualityScore + delta);
		return dualityScore - before;
	}

	public long lastSimulatedDay() {
		return lastSimulatedDay;
	}

	public void setLastSimulatedDay(long day) {
		this.lastSimulatedDay = day;
	}

	public long simulatedDayCount() {
		return simulatedDayCount;
	}

	public void addSimulatedDays(long days) {
		this.simulatedDayCount += days;
	}

	/** -1 (wholly evil) .. +1 (wholly good). The normalized form gameplay should read. */
	public double normalized() {
		return dualityScore / LIMIT;
	}

	/** A readable band for chat and UI. */
	public String descriptor() {
		double n = normalized();
		if (n <= -0.6)
			return "Damned";
		if (n <= -0.25)
			return "Darkening";
		if (n < 0.25)
			return "Balanced";
		if (n < 0.6)
			return "Blessed";
		return "Radiant";
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("duality_score", dualityScore);
		json.addProperty("last_simulated_day", lastSimulatedDay);
		json.addProperty("simulated_day_count", simulatedDayCount);
		return json;
	}

	public static WorldDualityState fromJson(JsonObject json) {
		WorldDualityState state = new WorldDualityState();
		if (json == null)
			return state;
		if (json.has("duality_score"))
			state.dualityScore = clamp(json.get("duality_score").getAsDouble());
		if (json.has("last_simulated_day"))
			state.lastSimulatedDay = json.get("last_simulated_day").getAsLong();
		if (json.has("simulated_day_count"))
			state.simulatedDayCount = json.get("simulated_day_count").getAsLong();
		return state;
	}

	private static double clamp(double value) {
		return Math.max(-LIMIT, Math.min(LIMIT, value));
	}
}
