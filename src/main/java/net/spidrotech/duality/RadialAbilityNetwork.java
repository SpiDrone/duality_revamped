package net.spidrotech.duality;

import net.spidrotech.duality.network.DualityModVariables;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;

import io.netty.buffer.ByteBuf;

@EventBusSubscriber(modid = "duality", bus = EventBusSubscriber.Bus.MOD)
public class RadialAbilityNetwork {
	// 1. Define the Payload (The data being sent across the network)
	public record RadialMenuSelectionPayload(String selectionId) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<RadialMenuSelectionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("duality", "radial_ability_selector"));
		public static final StreamCodec<ByteBuf, RadialMenuSelectionPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RadialMenuSelectionPayload::selectionId, RadialMenuSelectionPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// 2. Register the Payload to the network channel
	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("duality");
		// playToServer means the Client is sending this TO the Server
		registrar.playToServer(RadialMenuSelectionPayload.TYPE, RadialMenuSelectionPayload.STREAM_CODEC, RadialAbilityNetwork::handleDataOnMain);
	}

	// 3. Handle the Payload when it arrives on the Server
	public static void handleDataOnMain(final RadialMenuSelectionPayload data, final IPayloadContext context) {
		// We must enqueue work to the main server thread to safely modify player data
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) {
				return;
			}
			// Grab the string sent by the client
			String id = data.selectionId();
			// Set the global variable to whatever the passed string is
			DualityModVariables.PlayerVariables _vars = player.getData(DualityModVariables.PLAYER_VARIABLES);
			_vars.selected_ability = id;
			_vars.markSyncDirty();
		});
	}
}