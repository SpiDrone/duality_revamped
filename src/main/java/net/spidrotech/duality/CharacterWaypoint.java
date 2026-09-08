package net.spidrotech.duality;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A named, fixed teleport point - reusable by ANY teleport-style ability (Orb, a future
 * hearthstone item, a recall scroll, whatever), not tied to any one of them. Lives inside a
 * character's JSON sheet (see DualityDatabaseManager) as one entry in that character's
 * "waypoints" array - e.g. name="home". Plain Gson JsonObject in/out, matching
 * DualityDatabaseManager's existing hand-rolled JSON persistence style.
 */
public record CharacterWaypoint(String name, ResourceLocation icon, ResourceKey<Level> dimension, double x, double y, double z) {

	public Vec3 position() {
		return new Vec3(x, y, z);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("name", name);
		json.addProperty("icon", icon.toString());
		json.addProperty("dimension", dimension.location().toString());
		json.addProperty("x", x);
		json.addProperty("y", y);
		json.addProperty("z", z);
		return json;
	}

	public static CharacterWaypoint fromJson(JsonObject json) {
		return new CharacterWaypoint(
				json.get("name").getAsString(),
				ResourceLocation.parse(json.get("icon").getAsString()),
				ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(json.get("dimension").getAsString())),
				json.get("x").getAsDouble(),
				json.get("y").getAsDouble(),
				json.get("z").getAsDouble());
	}
}
