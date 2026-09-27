package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.item.athame.AthameEvents;
import net.spidrotech.duality.abilities.vampire.client.BiteLean;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The look of a bite (see VampireBite): the vampire lunges forward instead of swinging an arm.
 *
 * <p>The biter's own client suppresses the swing and starts its own lean the moment they click
 * (BiteLean), so it feels immediate. Everyone else watching hears about it from the server here,
 * when the attack arrives, and plays the same lean on that player's model.
 */
@EventBusSubscriber(modid = "duality")
public final class BiteNetwork {
	private BiteNetwork() {
	}

	/** Whether {@code player} attacking {@code target} right now is a bite rather than a punch -
	 *  the same test on both sides, so the client's hidden swing and the server's lean agree. */
	public static boolean isBite(Player player, Entity target) {
		return target instanceof LivingEntity living && player.getMainHandItem().isEmpty() && VampireRank.isVampire(player) && VampireMode.isActive(player)
				&& AthameEvents.bleeds(living);
	}

	public record BiteLeanPayload(int entityId) implements CustomPacketPayload {
		public static final Type<BiteLeanPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "bite_lean"));
		public static final StreamCodec<FriendlyByteBuf, BiteLeanPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, BiteLeanPayload payload) -> buf.writeVarInt(payload.entityId()),
				(FriendlyByteBuf buf) -> new BiteLeanPayload(buf.readVarInt()));

		@Override
		public Type<BiteLeanPayload> type() {
			return TYPE;
		}
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToClient(BiteLeanPayload.TYPE, BiteLeanPayload.STREAM_CODEC, BiteNetwork::handleLean);
	}

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && isBite(player, event.getTarget()))
			PacketDistributor.sendToPlayersTrackingEntity(player, new BiteLeanPayload(player.getId()));
	}

	private static void handleLean(BiteLeanPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> BiteLean.start(payload.entityId()));
	}
}
