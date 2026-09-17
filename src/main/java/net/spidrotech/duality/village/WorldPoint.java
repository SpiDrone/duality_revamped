package net.spidrotech.duality.village;

import com.google.gson.JsonObject;

/**
 * A dimension-qualified block position, stored as plain numbers so the village files stay readable
 * and hand-editable. Deliberately not a BlockPos: everything in this package below the Minecraft
 * glue layer is ordinary Java, which is what lets the simulation be tested without a game running.
 */
public record WorldPoint(String dimension, int x, int y, int z) {
	public static final WorldPoint ORIGIN = new WorldPoint("minecraft:overworld", 0, 0, 0);

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("dimension", dimension);
		json.addProperty("x", x);
		json.addProperty("y", y);
		json.addProperty("z", z);
		return json;
	}

	public static WorldPoint fromJson(JsonObject json) {
		if (json == null)
			return ORIGIN;
		return new WorldPoint(json.has("dimension") ? json.get("dimension").getAsString() : ORIGIN.dimension(), json.has("x") ? json.get("x").getAsInt() : 0,
				json.has("y") ? json.get("y").getAsInt() : 0, json.has("z") ? json.get("z").getAsInt() : 0);
	}

	/** Squared horizontal distance, or -1 when the two points aren't even in the same dimension.
	 *  Squared so "which of these is nearest" needs no square roots. */
	public double horizontalDistanceSqr(WorldPoint other) {
		if (other == null || !dimension.equals(other.dimension()))
			return -1;
		double dx = x - other.x();
		double dz = z - other.z();
		return dx * dx + dz * dz;
	}

	@Override
	public String toString() {
		return dimension + " " + x + ", " + y + ", " + z;
	}
}
