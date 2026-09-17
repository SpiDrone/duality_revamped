package net.spidrotech.duality.abilities.shapeshift;

import net.spidrotech.duality.ModAttachments;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.List;
import java.util.HashSet;

import io.netty.buffer.ByteBuf;

/** Mirrors the current form ("" = none) and the unlocked forms to the owning client, so radial
 *  display conditions and FOV can read them locally. Resent whenever the client gets a fresh
 *  player entity (login, respawn, dimension change). */
@EventBusSubscriber(modid = "duality")
public final class ShapeshiftNetwork {
	public record SyncFormPayload(String formId) implements CustomPacketPayload {
		public static final Type<SyncFormPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "shapeshift_form_sync"));
		public static final StreamCodec<ByteBuf, SyncFormPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SyncFormPayload::formId, SyncFormPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SyncUnlockedFormsPayload(List<ResourceLocation> unlocked) implements CustomPacketPayload {
		public static final Type<SyncUnlockedFormsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "shapeshift_unlocked_sync"));
		public static final StreamCodec<ByteBuf, SyncUnlockedFormsPayload> STREAM_CODEC = StreamCodec.composite(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncUnlockedFormsPayload::unlocked,
				SyncUnlockedFormsPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private ShapeshiftNetwork() {
	}

	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToClient(SyncFormPayload.TYPE, SyncFormPayload.STREAM_CODEC,
				(data, context) -> context.enqueueWork(() -> context.player().setData(ModAttachments.SHAPESHIFT_FORM, data.formId())));
		registrar.playToClient(SyncUnlockedFormsPayload.TYPE, SyncUnlockedFormsPayload.STREAM_CODEC,
				(data, context) -> context.enqueueWork(() -> context.player().setData(ModAttachments.UNLOCKED_FORMS, new HashSet<>(data.unlocked()))));
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			Shapeshift.syncAll(player);
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			Shapeshift.syncAll(player);
	}

	@SubscribeEvent
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			Shapeshift.syncAll(player);
	}
}
