package net.spidrotech.duality.abilities.teleportation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One thing a teleport ability's destination picker can show/select. Shared by every
 * teleport-style power (Orb, a future hearthstone item, a recall scroll, whatever) - NOT specific
 * to Orb or to whitelighters. WAYPOINT markers come from CharacterWaypoint and are available to
 * any teleport ability; CALL markers come exclusively from WhitelighterCallRegistry, since
 * "being called by name" is a whitelighter-only mechanic - that exclusivity lives in WHERE a CALL
 * marker gets produced, not in this class.
 *
 * selectionId is what gets sent back on selection: a waypoint name for WAYPOINT markers, a
 * caller's UUID string for CALL markers - see OrbDestinationResolver for how the server turns
 * that back into an actual destination.
 */
public record TeleportMarker(String selectionId, MarkerKind kind, String label, ResourceLocation icon, ResourceKey<Level> dimension, Vec3 position) {
	public enum MarkerKind {
		WAYPOINT, CALL
	}

	public static final StreamCodec<FriendlyByteBuf, TeleportMarker> STREAM_CODEC = StreamCodec.of(
			(FriendlyByteBuf buf, TeleportMarker marker) -> {
				buf.writeUtf(marker.selectionId());
				buf.writeEnum(marker.kind());
				buf.writeUtf(marker.label());
				buf.writeResourceLocation(marker.icon());
				buf.writeResourceLocation(marker.dimension().location());
				buf.writeDouble(marker.position().x);
				buf.writeDouble(marker.position().y);
				buf.writeDouble(marker.position().z);
			},
			(FriendlyByteBuf buf) -> {
				String selectionId = buf.readUtf();
				MarkerKind kind = buf.readEnum(MarkerKind.class);
				String label = buf.readUtf();
				ResourceLocation icon = buf.readResourceLocation();
				ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
				double x = buf.readDouble();
				double y = buf.readDouble();
				double z = buf.readDouble();
				return new TeleportMarker(selectionId, kind, label, icon, dimension, new Vec3(x, y, z));
			});
}
