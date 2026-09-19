package net.spidrotech.duality;

import net.spidrotech.duality.abilities.shapeshift.client.SpiderFormAnimator;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import io.netty.buffer.ByteBuf;

/**
 * Tells clients "this player just cast that projectile", so whatever they currently look like can
 * react - today, a player in spider form plays the spider's attack when they spit web.
 *
 * <p>An explicit packet rather than clients noticing the projectile appear, because a point-blank
 * shot can hit and be discarded on the server's very first tick, before any client ever sees it -
 * and point blank is exactly when the animation matters most.
 *
 * <p>Players only. Mobs that cast projectiles already drive their own animation from their own
 * synced state (DemonSpiderEntity's SPIT), so announcing their casts would only double it up.
 */
@EventBusSubscriber(modid = "duality")
public final class ProjectileCastNetwork {
	public record ProjectileCastPayload(int casterId, ResourceLocation projectileId) implements CustomPacketPayload {
		public static final Type<ProjectileCastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "projectile_cast"));
		public static final StreamCodec<ByteBuf, ProjectileCastPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, ProjectileCastPayload::casterId,
				ResourceLocation.STREAM_CODEC, ProjectileCastPayload::projectileId, ProjectileCastPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private ProjectileCastNetwork() {
	}

	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("duality");
		// The handler body only ever runs on a client, so SpiderFormAnimator never class-loads on a
		// dedicated server - same arrangement as JumpInputNetwork's dash feedback.
		registrar.playToClient(ProjectileCastPayload.TYPE, ProjectileCastPayload.STREAM_CODEC, (data, context) -> context.enqueueWork(() -> {
			Entity caster = context.player().level().getEntity(data.casterId());
			if (caster instanceof Player player)
				SpiderFormAnimator.onProjectileCast(player, data.projectileId());
		}));
	}

	/** Sent to the caster and everyone who can currently see them. */
	static void announce(LivingEntity caster, ResourceLocation projectileId) {
		if (caster instanceof ServerPlayer player)
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new ProjectileCastPayload(player.getId(), projectileId));
	}
}
