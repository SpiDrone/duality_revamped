package net.spidrotech.duality.abilities;

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

/** Mirrors AbilityToggles to the owning client - on every change, and again whenever the client
 *  gets a fresh player entity (login, respawn, dimension change), which starts with empty data. */
@EventBusSubscriber(modid = "duality")
public final class AbilityToggleNetwork {
	public record SyncTogglesPayload(List<ResourceLocation> toggled) implements CustomPacketPayload {
		public static final Type<SyncTogglesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "ability_toggles_sync"));
		public static final StreamCodec<ByteBuf, SyncTogglesPayload> STREAM_CODEC = StreamCodec.composite(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncTogglesPayload::toggled,
				SyncTogglesPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private AbilityToggleNetwork() {
	}

	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToClient(SyncTogglesPayload.TYPE, SyncTogglesPayload.STREAM_CODEC, AbilityToggleNetwork::handleSync);
	}

	private static void handleSync(final SyncTogglesPayload data, final IPayloadContext context) {
		context.enqueueWork(() -> context.player().setData(ModAttachments.TOGGLED_ABILITIES, new HashSet<>(data.toggled())));
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AbilityToggles.sync(player);
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AbilityToggles.sync(player);
	}

	@SubscribeEvent
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AbilityToggles.sync(player);
	}
}
