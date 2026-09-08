package net.spidrotech.duality.abilities.teleportation;

import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.CharacterWaypoint;

import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * Turns a picker selectionId (see TeleportMarker / TeleportNetwork) back into an actual
 * destination - either a saved CharacterWaypoint (selectionId == its name) or a live
 * WhitelighterCallRegistry caller (selectionId == the caller's UUID string), resolved against
 * their CURRENT position, not a snapshot of wherever they were when the picker opened.
 */
public final class OrbDestinationResolver {
	private OrbDestinationResolver() {
	}

	public record Destination(Vec3 position, ServerLevel level) {
	}

	public static Destination resolve(ServerPlayer whitelighter, String selectionId) {
		try {
			UUID callerId = UUID.fromString(selectionId);
			long now = whitelighter.level().getGameTime();
			boolean stillCalling = WhitelighterCallRegistry.get().activeCalls(whitelighter.getUUID(), now).stream().anyMatch(call -> call.callerId().equals(callerId));
			if (!stillCalling)
				return null;
			ServerPlayer caller = whitelighter.getServer().getPlayerList().getPlayer(callerId);
			if (caller != null && caller.level() instanceof ServerLevel callerLevel) {
				return new Destination(caller.position(), callerLevel);
			}
			return null;
		} catch (IllegalArgumentException notAUuid) {
			// fall through - selectionId is a waypoint name, not a caller UUID
		}
		JsonObject profile = DualityDatabaseManager.getPlayerProfile(whitelighter);
		String characterId = profile != null && profile.has("active_character_id") ? profile.get("active_character_id").getAsString() : "";
		if (characterId.isEmpty())
			return null;
		List<CharacterWaypoint> waypoints = DualityDatabaseManager.getWaypoints(whitelighter, characterId);
		return waypoints.stream().filter(w -> w.name().equalsIgnoreCase(selectionId)).findFirst().map(w -> {
			ServerLevel level = whitelighter.getServer().getLevel(w.dimension());
			return level != null ? new Destination(w.position(), level) : null;
		}).orElse(null);
	}
}