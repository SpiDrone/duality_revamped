package net.spidrotech.duality.abilities.teleportation;

import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.CharacterWaypoint;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.registries.Registries;

import com.google.gson.JsonObject;

@EventBusSubscriber(modid = "duality", bus = EventBusSubscriber.Bus.MOD)
public final class WaypointNetwork {
	private WaypointNetwork() {
	}

	/** C->S - "save this waypoint." Send this from your GUI's confirm button. */
	public record CreateWaypointPayload(String name, ResourceLocation icon, ResourceLocation dimension, double x, double y, double z) implements CustomPacketPayload {

		public static final Type<CreateWaypointPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "create_waypoint"));
		public static final StreamCodec<FriendlyByteBuf, CreateWaypointPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, CreateWaypointPayload payload) -> {
			buf.writeUtf(payload.name());
			buf.writeResourceLocation(payload.icon());
			buf.writeResourceLocation(payload.dimension());
			buf.writeDouble(payload.x());
			buf.writeDouble(payload.y());
			buf.writeDouble(payload.z());
		}, (FriendlyByteBuf buf) -> new CreateWaypointPayload(buf.readUtf(), buf.readResourceLocation(), buf.readResourceLocation(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
		@Override
		public Type<CreateWaypointPayload> type() {
			return TYPE;
		}
	}

	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToServer(CreateWaypointPayload.TYPE, CreateWaypointPayload.STREAM_CODEC, WaypointNetwork::handleCreateWaypoint);
	}

	private static void handleCreateWaypoint(final CreateWaypointPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			if (payload.name() == null || payload.name().isBlank())
				return; // TODO: feedback for "name required"
			JsonObject profile = DualityDatabaseManager.getPlayerProfile(player);
			String characterId = profile != null && profile.has("active_character_id") ? profile.get("active_character_id").getAsString() : "";
			if (characterId.isEmpty())
				return; // no active character to attach the waypoint to
			ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, payload.dimension());
			CharacterWaypoint waypoint = new CharacterWaypoint(payload.name().trim(), payload.icon(), dimensionKey, payload.x(), payload.y(), payload.z());
			DualityDatabaseManager.addWaypoint(player, characterId, waypoint);
		});
	}
}