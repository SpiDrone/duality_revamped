package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.abilities.vampire.client.DashScreenEffect;
import net.spidrotech.duality.abilities.AbilityToggles;
import net.spidrotech.duality.AbilityManager;

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

import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;

/** Space-bar input from JumpInputClient, plus the dash confirmation back to it. The server re-checks
 *  the toggles itself rather than trusting the client's view of them. */
@EventBusSubscriber(modid = "duality")
public final class JumpInputNetwork {
	public record StartLeapChargePayload() implements CustomPacketPayload {
		public static final Type<StartLeapChargePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "leap_charge_start"));
		public static final StreamCodec<ByteBuf, StartLeapChargePayload> STREAM_CODEC = StreamCodec.unit(new StartLeapChargePayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ReleaseLeapChargePayload() implements CustomPacketPayload {
		public static final Type<ReleaseLeapChargePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "leap_charge_release"));
		public static final StreamCodec<ByteBuf, ReleaseLeapChargePayload> STREAM_CODEC = StreamCodec.unit(new ReleaseLeapChargePayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** backwards = shift was held when the double tap completed. */
	public record DashPayload(boolean backwards) implements CustomPacketPayload {
		public static final Type<DashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "dash"));
		public static final StreamCodec<ByteBuf, DashPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, DashPayload::backwards, DashPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server -> client: a dash actually went off, play the screen effect. */
	public record DashFeedbackPayload(boolean backwards) implements CustomPacketPayload {
		public static final Type<DashFeedbackPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "dash_feedback"));
		public static final StreamCodec<ByteBuf, DashFeedbackPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, DashFeedbackPayload::backwards, DashFeedbackPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private JumpInputNetwork() {
	}

	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToServer(StartLeapChargePayload.TYPE, StartLeapChargePayload.STREAM_CODEC, (data, context) -> onServer(context, player -> {
			if (AbilityToggles.isToggled(player, LeapAbility.ID))
				AbilityManager.get().tryActivate(player, LeapAbility.ID);
		}));
		registrar.playToServer(ReleaseLeapChargePayload.TYPE, ReleaseLeapChargePayload.STREAM_CODEC, (data, context) -> onServer(context, player -> AbilityManager.get().cancel(player, LeapAbility.ID)));
		registrar.playToServer(DashPayload.TYPE, DashPayload.STREAM_CODEC, (data, context) -> onServer(context, player -> {
			if (AbilityToggles.isToggled(player, DashAbility.ID))
				AbilityManager.get().tryActivate(player, DashAbility.ID, ctx -> ctx.set(DashAbility.BACKWARDS_KEY, data.backwards()));
		}));
		// The handler body only runs on the client, so DashScreenEffect never class-loads on a server.
		registrar.playToClient(DashFeedbackPayload.TYPE, DashFeedbackPayload.STREAM_CODEC, (data, context) -> context.enqueueWork(() -> DashScreenEffect.play(data.backwards())));
	}

	private static void onServer(IPayloadContext context, Consumer<ServerPlayer> action) {
		context.enqueueWork(() -> {
			if (context.player() instanceof ServerPlayer player)
				action.accept(player);
		});
	}
}
