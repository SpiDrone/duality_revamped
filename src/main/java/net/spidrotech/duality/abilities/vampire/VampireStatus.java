package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.charactercreation.CharacterProgress;

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

/**
 * The bits of a vampire's state that live on the character sheet (server only) but that their own
 * client needs to draw things - today just whether they're Vegan, for the gold eye icons on the
 * ability radial. Sent to the owner on login, whenever a character is applied, and whenever Vegan
 * is gained or lost.
 */
@EventBusSubscriber(modid = "duality")
public final class VampireStatus {
	/** The owner's own status, as last sent. Client side only ever reads this. */
	private static volatile boolean clientVegan;

	private VampireStatus() {
	}

	public static boolean clientIsVegan() {
		return clientVegan;
	}

	public record StatusPayload(boolean vegan) implements CustomPacketPayload {
		public static final Type<StatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "vampire_status"));
		public static final StreamCodec<FriendlyByteBuf, StatusPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, StatusPayload payload) -> buf.writeBoolean(payload.vegan()),
				(FriendlyByteBuf buf) -> new StatusPayload(buf.readBoolean()));

		@Override
		public Type<StatusPayload> type() {
			return TYPE;
		}
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToClient(StatusPayload.TYPE, StatusPayload.STREAM_CODEC, VampireStatus::handle);
	}

	public static void sync(ServerPlayer player) {
		if (player.connection != null)
			PacketDistributor.sendToPlayer(player, new StatusPayload(CharacterProgress.hasProficiency(player, CharacterProgress.VEGAN)));
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			sync(player);
	}

	private static void handle(StatusPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> clientVegan = payload.vegan());
	}
}
