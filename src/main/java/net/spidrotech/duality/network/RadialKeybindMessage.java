package net.spidrotech.duality.network;

import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;

@EventBusSubscriber
public record RadialKeybindMessage(int eventType, int pressedms) implements CustomPacketPayload {
	public static final Type<RadialKeybindMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DualityMod.MODID, "key_radial_keybind"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RadialKeybindMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, RadialKeybindMessage message) -> {
		buffer.writeInt(message.eventType);
		buffer.writeInt(message.pressedms);
	}, (RegistryFriendlyByteBuf buffer) -> new RadialKeybindMessage(buffer.readInt(), buffer.readInt()));

	@Override
	public Type<RadialKeybindMessage> type() {
		return TYPE;
	}

	public static void handleData(final RadialKeybindMessage message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
			}).exceptionally(e -> {
				context.connection().disconnect(Component.literal(e.getMessage()));
				return null;
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		DualityMod.addNetworkMessage(RadialKeybindMessage.TYPE, RadialKeybindMessage.STREAM_CODEC, RadialKeybindMessage::handleData);
	}
}