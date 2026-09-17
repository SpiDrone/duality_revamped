package net.spidrotech.duality.village;

import com.google.gson.JsonObject;

/**
 * One structure a village has put up, and where it is.
 *
 * <p>The position matters for exactly one reason today, and it's the important one: a pantry has to
 * be somewhere a player can walk to. {@link VillagePantry} scans the containers inside the pantry's
 * footprint and nowhere else, so "the village's food" is whatever is in those chests.
 */
public class VillageBuilding {
	/** How far from a building's position its footprint reaches, in blocks. Containers inside this
	 *  of a pantry count as the pantry. */
	public static final int FOOTPRINT_RADIUS = 5;

	private final BuildingType type;
	private final long builtDay;
	/** Where it stands, or null if it's only ever existed on paper. An unplaced pantry stores
	 *  nothing, which is the honest answer - there's no chest to put anything in. */
	private WorldPoint position;

	public VillageBuilding(BuildingType type, long builtDay, WorldPoint position) {
		this.type = type;
		this.builtDay = builtDay;
		this.position = position;
	}

	public BuildingType type() {
		return type;
	}

	public long builtDay() {
		return builtDay;
	}

	public WorldPoint position() {
		return position;
	}

	public void setPosition(WorldPoint position) {
		this.position = position;
	}

	public boolean isPlaced() {
		return position != null;
	}

	/** A pantry that actually exists somewhere, and so can hold something. */
	public boolean isUsableStore() {
		return type.storesFood() && isPlaced();
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("type", type.name());
		json.addProperty("built_day", builtDay);
		if (position != null)
			json.add("position", position.toJson());
		return json;
	}

	public static VillageBuilding fromJson(JsonObject json) {
		return new VillageBuilding(BuildingType.parse(json.has("type") ? json.get("type").getAsString() : null),
				json.has("built_day") ? json.get("built_day").getAsLong() : 0L, json.has("position") ? WorldPoint.fromJson(json.getAsJsonObject("position")) : null);
	}

	@Override
	public String toString() {
		return type.displayName() + (position == null ? " (unplaced)" : " at " + position);
	}
}
