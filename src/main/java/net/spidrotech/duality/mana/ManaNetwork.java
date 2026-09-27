package net.spidrotech.duality.mana;

import net.spidrotech.duality.mana.client.ClientMana;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** Server to owner: "your mana is now X of Y". Only the owner ever sees their own pool. */
@EventBusSubscriber(modid = "duality")
public final class ManaNetwork {
	private ManaNetwork() {
	}

	public record SyncManaPayload(float current, float max) implements CustomPacketPayload {
		public static final Type<SyncManaPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "sync_mana"));
		public static final StreamCodec<FriendlyByteBuf, SyncManaPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, SyncManaPayload payload) -> {
			buf.writeFloat(payload.current());
			buf.writeFloat(payload.max());
		}, (FriendlyByteBuf buf) -> new SyncManaPayload(buf.readFloat(), buf.readFloat()));

		@Override
		public Type<SyncManaPayload> type() {
			return TYPE;
		}
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToClient(SyncManaPayload.TYPE, SyncManaPayload.STREAM_CODEC, ManaNetwork::handleSync);
	}

	public static void sync(ServerPlayer player) {
		if (player.connection == null)
			return;
		PacketDistributor.sendToPlayer(player, new SyncManaPayload((float) Mana.current(player), (float) Mana.max(player)));
	}

	private static void handleSync(SyncManaPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientMana.accept(payload.current(), payload.max()));
	}
}
