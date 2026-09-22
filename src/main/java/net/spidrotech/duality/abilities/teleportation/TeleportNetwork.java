package net.spidrotech.duality.abilities.teleportation;

import net.spidrotech.duality.abilities.teleportation.client.TeleportPickerClientState;
import net.spidrotech.duality.abilities.teleportation.client.ClientOrbitVisualManager;
import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.CharacterWaypoint;
import net.spidrotech.duality.AbilityManager;
import net.spidrotech.duality.AbilityContext;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.ChatFormatting;

import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

import com.google.gson.JsonObject;

/**
 * All networking for teleport-ability destination pickers, in one place - request/sync of
 * available markers, confirming a selection, cancelling one already confirmed, toggling a
 * passenger on/off mid-charge, and telling the client when the server ended a charge on its
 * own (either cancelled due to passenger capacity, or naturally finished - see
 * OrbCancelledPayload / OrbFinishedPayload). Ability-agnostic: which glow style to apply and
 * which key/selection trigger opens the picker are the CALLER's job (see
 * client.TeleportBrowsingWatcher), not this class's.
 */
@EventBusSubscriber(modid = "duality", bus = EventBusSubscriber.Bus.MOD)
public final class TeleportNetwork {
	// TODO: point this at a real "someone is calling you" icon once you have one - calls have
	// no player-chosen icon the way waypoints do.
	private static final ResourceLocation CALL_MARKER_ICON = ResourceLocation.fromNamespaceAndPath("duality", "textures/gui/icon_call_marker.png");

	private TeleportNetwork() {
	}

	/** C->S, empty - "give me my current teleport markers." Sent by TeleportBrowsingWatcher the
	 *  moment a teleport ability becomes the selected ability. */
	public record RequestTeleportMarkersPayload() implements CustomPacketPayload {
		public static final Type<RequestTeleportMarkersPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "request_teleport_markers"));
		public static final StreamCodec<FriendlyByteBuf, RequestTeleportMarkersPayload> STREAM_CODEC = StreamCodec.of((buf, msg) -> {
		}, buf -> new RequestTeleportMarkersPayload());

		@Override
		public Type<RequestTeleportMarkersPayload> type() {
			return TYPE;
		}
	}

	/** S->C - the requesting player's current waypoints + active incoming calls, flattened into
	 *  TeleportMarkers. Ability-agnostic on purpose. */
	public record SyncTeleportMarkersPayload(List<TeleportMarker> markers) implements CustomPacketPayload {
		public static final Type<SyncTeleportMarkersPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "sync_teleport_markers"));
		public static final StreamCodec<FriendlyByteBuf, SyncTeleportMarkersPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, SyncTeleportMarkersPayload payload) -> {
			buf.writeVarInt(payload.markers().size());
			for (TeleportMarker marker : payload.markers()) {
				TeleportMarker.STREAM_CODEC.encode(buf, marker);
			}
		}, (FriendlyByteBuf buf) -> {
			int count = buf.readVarInt();
			List<TeleportMarker> markers = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				markers.add(TeleportMarker.STREAM_CODEC.decode(buf));
			}
			return new SyncTeleportMarkersPayload(markers);
		});

		@Override
		public Type<SyncTeleportMarkersPayload> type() {
			return TYPE;
		}
	}

	/** C->S - "I've picked this destination, with these passengers." selectionId matches a
	 *  TeleportMarker's own selectionId. abilityId is whichever teleport ability's picker this
	 *  came from (TeleportPickerClientState#abilityId, stamped by client.TeleportBrowsingWatcher
	 *  when the picker opened) - Orb, Shimmer, or any future OrbAbility subclass, see
	 *  TeleportAbilities. */
	public record ConfirmTeleportSelectionPayload(ResourceLocation abilityId, String selectionId, List<UUID> passengerIds) implements CustomPacketPayload {
		public static final Type<ConfirmTeleportSelectionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "confirm_teleport_selection"));
		public static final StreamCodec<FriendlyByteBuf, ConfirmTeleportSelectionPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, ConfirmTeleportSelectionPayload payload) -> {
			buf.writeResourceLocation(payload.abilityId());
			buf.writeUtf(payload.selectionId());
			buf.writeVarInt(payload.passengerIds().size());
			for (UUID id : payload.passengerIds()) {
				buf.writeUUID(id);
			}
		}, (FriendlyByteBuf buf) -> {
			ResourceLocation abilityId = buf.readResourceLocation();
			String selectionId = buf.readUtf();
			int count = buf.readVarInt();
			List<UUID> ids = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				ids.add(buf.readUUID());
			}
			return new ConfirmTeleportSelectionPayload(abilityId, selectionId, ids);
		});

		@Override
		public Type<ConfirmTeleportSelectionPayload> type() {
			return TYPE;
		}
	}

	/** C->S - "actually, never mind that destination." Sent when the player right-clicks empty
	 *  air again while LOCKED (see client.TeleportPickerInputHandler). Cancels whatever charging
	 *  instance of abilityId the earlier confirm started, so a second click reliably backs the
	 *  player back out to picking a new destination instead of leaving a stuck charge running. */
	public record CancelTeleportSelectionPayload(ResourceLocation abilityId) implements CustomPacketPayload {
		public static final Type<CancelTeleportSelectionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "cancel_teleport_selection"));
		public static final StreamCodec<FriendlyByteBuf, CancelTeleportSelectionPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, CancelTeleportSelectionPayload payload) -> buf.writeResourceLocation(payload.abilityId()),
				(FriendlyByteBuf buf) -> new CancelTeleportSelectionPayload(buf.readResourceLocation()));

		@Override
		public Type<CancelTeleportSelectionPayload> type() {
			return TYPE;
		}
	}

	/** C->S - "add or remove this entity as a passenger," sent from
	 *  client.TeleportPickerInputHandler when the player right-clicks a LivingEntity WHILE
	 *  LOCKED (i.e. abilityId is already charging). See handleTogglePassenger for what happens
	 *  if the addition would exceed the caster's passenger capacity. */
	public record ToggleTeleportPassengerPayload(ResourceLocation abilityId, UUID targetId) implements CustomPacketPayload {
		public static final Type<ToggleTeleportPassengerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "toggle_teleport_passenger"));
		public static final StreamCodec<FriendlyByteBuf, ToggleTeleportPassengerPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, ToggleTeleportPassengerPayload payload) -> {
			buf.writeResourceLocation(payload.abilityId());
			buf.writeUUID(payload.targetId());
		}, (FriendlyByteBuf buf) -> new ToggleTeleportPassengerPayload(buf.readResourceLocation(), buf.readUUID()));

		@Override
		public Type<ToggleTeleportPassengerPayload> type() {
			return TYPE;
		}
	}

	/** S->C, empty - "the orb charge you had going just got cancelled server-side because your
	 *  passenger selection stopped fitting" (fired from handleTogglePassenger's capacity-
	 *  exceeded branch). Distinct from OrbFinishedPayload below, which covers every OTHER way a
	 *  charge can end - keeping the two separate means the client's chat feedback can stay
	 *  specific about the passenger-capacity case rather than a generic "it's over" message. */
	public record OrbCancelledPayload() implements CustomPacketPayload {
		public static final Type<OrbCancelledPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "orb_cancelled"));
		public static final StreamCodec<FriendlyByteBuf, OrbCancelledPayload> STREAM_CODEC = StreamCodec.of((buf, msg) -> {
		}, buf -> new OrbCancelledPayload());

		@Override
		public Type<OrbCancelledPayload> type() {
			return TYPE;
		}
	}

	/** S->C, empty - "this Orb cast is fully over" (success OR interruption - the client's
	 *  correct response is the same either way: back to BROWSING, passengers cleared). Fired
	 *  from OrbAbility#onDeactivate (natural completion of the post-teleport particle window),
	 *  OrbAbility#onChargeInterrupted (charge cancelled mid-charge, e.g. by damage), and
	 *  OrbAbility#onActivate's two blocked-activation early returns. Without this, the client's
	 *  picker stays LOCKED forever after a successful (or blocked) teleport, since nothing else
	 *  ever tells it the attempt was over - this was the actual cause of "can't add new
	 *  passengers after teleporting," since a client stuck thinking it's still LOCKED sends
	 *  passenger toggles that handleTogglePassenger silently no-ops (no running context left to
	 *  toggle against). */
	public record OrbFinishedPayload() implements CustomPacketPayload {
		public static final Type<OrbFinishedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "orb_finished"));
		public static final StreamCodec<FriendlyByteBuf, OrbFinishedPayload> STREAM_CODEC = StreamCodec.of((buf, msg) -> {
		}, buf -> new OrbFinishedPayload());

		@Override
		public Type<OrbFinishedPayload> type() {
			return TYPE;
		}
	}

	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToServer(RequestTeleportMarkersPayload.TYPE, RequestTeleportMarkersPayload.STREAM_CODEC, TeleportNetwork::handleRequestMarkers);
		registrar.playToClient(SyncTeleportMarkersPayload.TYPE, SyncTeleportMarkersPayload.STREAM_CODEC, TeleportNetwork::handleSyncMarkers);
		registrar.playToServer(ConfirmTeleportSelectionPayload.TYPE, ConfirmTeleportSelectionPayload.STREAM_CODEC, TeleportNetwork::handleConfirm);
		registrar.playToServer(CancelTeleportSelectionPayload.TYPE, CancelTeleportSelectionPayload.STREAM_CODEC, TeleportNetwork::handleCancel);
		registrar.playToServer(ToggleTeleportPassengerPayload.TYPE, ToggleTeleportPassengerPayload.STREAM_CODEC, TeleportNetwork::handleTogglePassenger);
		registrar.playToClient(OrbCancelledPayload.TYPE, OrbCancelledPayload.STREAM_CODEC, TeleportNetwork::handleOrbCancelled);
		registrar.playToClient(OrbFinishedPayload.TYPE, OrbFinishedPayload.STREAM_CODEC, TeleportNetwork::handleOrbFinished);
	}

	private static void handleRequestMarkers(final RequestTeleportMarkersPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			PacketDistributor.sendToPlayer(player, new SyncTeleportMarkersPayload(gatherMarkersFor(player)));
		});
	}

	private static List<TeleportMarker> gatherMarkersFor(ServerPlayer player) {
		List<TeleportMarker> markers = new ArrayList<>();
		JsonObject profile = DualityDatabaseManager.getPlayerProfile(player);
		String characterId = profile != null && profile.has("active_character_id") ? profile.get("active_character_id").getAsString() : "";
		if (!characterId.isEmpty()) {
			for (CharacterWaypoint wp : DualityDatabaseManager.getWaypoints(player, characterId)) {
				markers.add(new TeleportMarker(wp.name(), TeleportMarker.MarkerKind.WAYPOINT, wp.name(), wp.icon(), wp.dimension(), wp.position()));
			}
		}
		long now = player.level().getGameTime();
		for (WhitelighterCallRegistry.Call call : WhitelighterCallRegistry.get().activeCalls(player.getUUID(), now)) {
			ServerPlayer caller = player.getServer().getPlayerList().getPlayer(call.callerId());
			if (caller == null || !(caller.level() instanceof ServerLevel callerLevel))
				continue;
			markers.add(new TeleportMarker(call.callerId().toString(), TeleportMarker.MarkerKind.CALL, call.callerName(), CALL_MARKER_ICON, callerLevel.dimension(), caller.position()));
		}
		return markers;
	}

	// Runs client-side only (playToClient handler) - safe for TeleportPickerClientState (a
	// @OnlyIn(CLIENT) class) to be referenced here despite this file itself being common code,
	// same pattern already used elsewhere in this codebase for S2C handlers.
	private static void handleSyncMarkers(final SyncTeleportMarkersPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			TeleportPickerClientState.setMarkers(payload.markers());
			TeleportPickerClientState.setActive(true);
		});
	}

	private static void handleConfirm(final ConfirmTeleportSelectionPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			// Never trust the client's abilityId at face value - only ever act on it once it's
			// confirmed to actually be a teleport ability (see TeleportAbilities). Everything
			// else in this handler already re-validates against the caster (range, passenger
			// capacity, Heaven) via that ability's own cast conditions, so this check exists
			// purely to stop an arbitrary/forged id from reaching tryActivate at all.
			if (TeleportAbilities.get(payload.abilityId()).isEmpty())
				return;
			OrbDestinationResolver.Destination destination = OrbDestinationResolver.resolve(player, payload.selectionId());
			if (destination == null)
				return; // TODO: feedback for "that destination no longer exists" (waypoint deleted, call expired)
			// Passengers are looked up in the CASTER's current level (where they actually are,
			// having not teleported yet), NOT destination.level() - those are frequently
			// different dimensions. ServerLevel#getEntity(UUID) finds any entity (not just
			// online players), matching the same lookup handleTogglePassenger uses.
			ServerLevel casterLevel = player.level() instanceof ServerLevel lvl ? lvl : null;
			AbilityManager.get().tryActivate(player, payload.abilityId(), ctx -> {
				ctx.setTargetPos(destination.position());
				ctx.set("destinationLevel", destination.level());
				if (casterLevel != null) {
					for (UUID passengerId : payload.passengerIds()) {
						Entity passenger = casterLevel.getEntity(passengerId);
						if (passenger != null) {
							ctx.addTarget(passenger);
						}
					}
				}
			});
		});
	}

	private static void handleCancel(final CancelTeleportSelectionPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			if (TeleportAbilities.get(payload.abilityId()).isEmpty())
				return;
			AbilityManager.get().cancel(player, payload.abilityId());
		});
	}

	private static void handleTogglePassenger(final ToggleTeleportPassengerPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			if (!(player.level() instanceof ServerLevel serverLevel))
				return;
			if (TeleportAbilities.get(payload.abilityId()).isEmpty())
				return;
			Optional<AbilityContext> maybeCtx = AbilityManager.get().runningContext(player, payload.abilityId());
			if (maybeCtx.isEmpty())
				return; // not currently charging/active - nothing to toggle, silently ignore
			AbilityContext ctx = maybeCtx.get();
			Entity target = serverLevel.getEntity(payload.targetId());
			if (target == null)
				return;
			if (ctx.targets().contains(target)) {
				ctx.removeTarget(target);
				return;
			}
			ServerLevel destLevel = ctx.get("destinationLevel", serverLevel);
			int prospectiveCount = ctx.targets().size() + 1;
			if (!OrbAbility.canBringPassengers(player, destLevel.dimension(), prospectiveCount)) {
				AbilityManager.get().cancel(player, payload.abilityId());
				player.displayClientMessage(Component.literal("You can't carry that many passengers - teleport cancelled.").withStyle(ChatFormatting.RED), true);
				PacketDistributor.sendToPlayer(player, new OrbCancelledPayload());
				return;
			}
			ctx.addTarget(target);
		});
	}

	private static void handleOrbCancelled(final OrbCancelledPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			TeleportPickerClientState.cancelSelection();
			ClientOrbitVisualManager.clearAll();
		});
	}

	private static void handleOrbFinished(final OrbFinishedPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			TeleportPickerClientState.cancelSelection();
			ClientOrbitVisualManager.clearAll();
		});
	}
}