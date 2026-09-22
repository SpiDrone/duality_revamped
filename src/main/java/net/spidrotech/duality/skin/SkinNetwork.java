package net.spidrotech.duality.skin;

import net.spidrotech.duality.skin.client.ClientSkinState;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/**
 * All skin-system networking in one place, same shape as TeleportNetwork.
 *
 *   SyncSkinCatalogPayload   S->C, once on join - the whole part catalog (metadata only).
 *   SyncAppearancePayload    S->C - one player's rendered loadout + live augmentations +
 *                                   version. Sent on any change, on join, and on start-tracking.
 *   EquipPartPayload         C->S - builder GUI equipping/unequipping a part. VALIDATED: the
 *                                   server re-checks ownership itself and silently ignores a
 *                                   part the player doesn't own, because a client asserting it
 *                                   owns something is exactly the claim you can't trust.
 *
 * There is deliberately NO client-to-server packet for temporary modifications. Augmentations
 * are applied by ability code on the server (see SkinTempModify) and only ever travel outward.
 */
@EventBusSubscriber(modid = "duality")
public final class SkinNetwork {
	private SkinNetwork() {
	}

	public record SyncSkinCatalogPayload(List<SkinPart> parts) implements CustomPacketPayload {
		public static final Type<SyncSkinCatalogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "sync_skin_catalog"));
		public static final StreamCodec<FriendlyByteBuf, SyncSkinCatalogPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, SyncSkinCatalogPayload payload) -> {
			buf.writeVarInt(payload.parts().size());
			for (SkinPart part : payload.parts()) {
				SkinPart.STREAM_CODEC.encode(buf, part);
			}
		}, (FriendlyByteBuf buf) -> {
			int count = buf.readVarInt();
			List<SkinPart> parts = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				parts.add(SkinPart.STREAM_CODEC.decode(buf));
			}
			return new SyncSkinCatalogPayload(parts);
		});

		@Override
		public Type<SyncSkinCatalogPayload> type() {
			return TYPE;
		}
	}

	public record SyncAppearancePayload(UUID playerId, int version, SkinLoadout loadout, List<TempSkinModification> temps) implements CustomPacketPayload {
		public static final Type<SyncAppearancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "sync_skin_appearance"));
		public static final StreamCodec<FriendlyByteBuf, SyncAppearancePayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, SyncAppearancePayload payload) -> {
			buf.writeUUID(payload.playerId());
			buf.writeVarInt(payload.version());
			SkinLoadout.STREAM_CODEC.encode(buf, payload.loadout());
			buf.writeVarInt(payload.temps().size());
			for (TempSkinModification mod : payload.temps()) {
				TempSkinModification.STREAM_CODEC.encode(buf, mod);
			}
		}, (FriendlyByteBuf buf) -> {
			UUID id = buf.readUUID();
			int version = buf.readVarInt();
			SkinLoadout loadout = SkinLoadout.STREAM_CODEC.decode(buf);
			int count = buf.readVarInt();
			List<TempSkinModification> temps = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				temps.add(TempSkinModification.STREAM_CODEC.decode(buf));
			}
			return new SyncAppearancePayload(id, version, loadout, List.copyOf(temps));
		});

		@Override
		public Type<SyncAppearancePayload> type() {
			return TYPE;
		}
	}

	/** equip=false unequips instead. tints are ignored when unequipping. */
	public record EquipPartPayload(String partId, boolean equip, List<Integer> tints) implements CustomPacketPayload {
		public static final Type<EquipPartPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "equip_skin_part"));
		public static final StreamCodec<FriendlyByteBuf, EquipPartPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, EquipPartPayload payload) -> {
			buf.writeUtf(payload.partId());
			buf.writeBoolean(payload.equip());
			buf.writeVarInt(payload.tints().size());
			for (int tint : payload.tints()) {
				buf.writeInt(tint);
			}
		}, (FriendlyByteBuf buf) -> {
			String partId = buf.readUtf();
			boolean equip = buf.readBoolean();
			int count = buf.readVarInt();
			List<Integer> tints = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				tints.add(buf.readInt());
			}
			return new EquipPartPayload(partId, equip, List.copyOf(tints));
		});

		@Override
		public Type<EquipPartPayload> type() {
			return TYPE;
		}
	}

	/** C->S - "my character's body is this build." Send from the builder GUI's thick/slim toggle.
	 *  Needs no ownership check the way parts do: a body isn't an unlockable, every character may
	 *  be either build, and the enum itself is the whole validation (an unknown value can't survive
	 *  readEnum). */
	public record SetBodyModelPayload(SkinBodyModel bodyModel) implements CustomPacketPayload {
		public static final Type<SetBodyModelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "set_skin_body_model"));
		public static final StreamCodec<FriendlyByteBuf, SetBodyModelPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, SetBodyModelPayload payload) -> buf.writeEnum(payload.bodyModel()),
				(FriendlyByteBuf buf) -> new SetBodyModelPayload(buf.readEnum(SkinBodyModel.class)));

		@Override
		public Type<SetBodyModelPayload> type() {
			return TYPE;
		}
	}

	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToClient(SyncSkinCatalogPayload.TYPE, SyncSkinCatalogPayload.STREAM_CODEC, SkinNetwork::handleSyncCatalog);
		registrar.playToClient(SyncAppearancePayload.TYPE, SyncAppearancePayload.STREAM_CODEC, SkinNetwork::handleSyncAppearance);
		registrar.playToServer(EquipPartPayload.TYPE, EquipPartPayload.STREAM_CODEC, SkinNetwork::handleEquip);
		registrar.playToServer(SetBodyModelPayload.TYPE, SetBodyModelPayload.STREAM_CODEC, SkinNetwork::handleSetBodyModel);
	}

	// Client-only handlers - same pattern as TeleportNetwork's S2C handlers referencing
	// @OnlyIn(CLIENT) state from common code.
	private static void handleSyncCatalog(final SyncSkinCatalogPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> SkinPartCatalog.replaceAll(payload.parts()));
	}

	private static void handleSyncAppearance(final SyncAppearancePayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> ClientSkinState.accept(payload.playerId(), payload.version(), payload.loadout(), payload.temps()));
	}

	private static void handleEquip(final EquipPartPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			SkinPart part = SkinPartCatalog.get(payload.partId());
			if (part == null)
				return;
			if (!payload.equip()) {
				SkinManager.get().setLoadout(player, SkinManager.get().loadoutOf(player.getUUID()).without(payload.partId()));
				return;
			}
			if (!ownsPart(player, part))
				return; // client asked for something it hasn't unlocked - ignore, don't trust
			SkinManager.get().setLoadout(player, SkinManager.get().loadoutOf(player.getUUID()).with(new SkinLoadout.Equipped(part.id(), payload.tints())));
		});
	}

	private static void handleSetBodyModel(final SetBodyModelPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			SkinManager.get().setLoadout(player, SkinManager.get().loadoutOf(player.getUUID()).withBodyModel(payload.bodyModel()));
		});
	}

	private static boolean ownsPart(ServerPlayer player, SkinPart part) {
		return SkinUnlocks.get().owns(player, part);
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			SkinManager.get().onPlayerJoin(player);
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			SkinManager.get().onPlayerLeave(player);
		}
	}

	/** A player coming into view needs the appearance of whoever they just started tracking -
	 *  without this, someone who walks over the hill renders with a stale/empty skin until that
	 *  player's next change happens to fire a broadcast. */
	@SubscribeEvent
	public static void onStartTracking(PlayerEvent.StartTracking event) {
		// getEntity() is the WATCHER, getTarget() is who they just started seeing.
		if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof ServerPlayer watched) {
			PacketDistributor.sendToPlayer(watcher, SkinManager.get().appearancePacketFor(watched));
		}
	}
}
