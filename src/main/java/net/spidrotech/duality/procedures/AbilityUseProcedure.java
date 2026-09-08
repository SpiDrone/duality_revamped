package net.spidrotech.duality.procedures;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.abilities.teleportation.client.TeleportPickerClientState;
import net.spidrotech.duality.DualityMod;
import net.spidrotech.duality.AbilitySublet;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.Event;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;

import javax.annotation.Nullable;

@EventBusSubscriber(Dist.CLIENT)
public class AbilityUseProcedure {
	@SubscribeEvent
	public static void onLeftClick(PlayerInteractEvent.LeftClickEmpty event) {
		// While the teleport picker is browsing/locked, it owns interaction entirely - a
		// left-click here would otherwise call AbilitySublet.use(player, "orb") with no
		// destination set, which previously fell back to teleporting wherever the player was
		// looking. OrbAbility's own cast condition now refuses that regardless (see
		// OrbAbility#HAS_DESTINATION), so this guard isn't load-bearing for correctness anymore
		// - but skipping the packet entirely avoids a doomed activation attempt (and its log
		// line) firing every time someone left-clicks with the picker open.
		if (TeleportPickerClientState.isActive()) {
			return;
		}
		PacketDistributor.sendToServer(new AbilityUseMessage());
		execute(event.getEntity());
	}

	@EventBusSubscriber
	public record AbilityUseMessage() implements CustomPacketPayload {
		public static final Type<AbilityUseMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DualityMod.MODID, "procedure_ability_use"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AbilityUseMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, AbilityUseMessage message) -> {
		}, (RegistryFriendlyByteBuf buffer) -> new AbilityUseMessage());

		@Override
		public Type<AbilityUseMessage> type() {
			return TYPE;
		}

		public static void handleData(final AbilityUseMessage message, final IPayloadContext context) {
			if (context.flow() == PacketFlow.SERVERBOUND) {
				context.enqueueWork(() -> {
					if (!context.player().level().hasChunkAt(context.player().blockPosition()))
						return;
					execute(context.player());
				}).exceptionally(e -> {
					context.connection().disconnect(Component.literal(e.getMessage()));
					return null;
				});
			}
		}

		@SubscribeEvent
		public static void registerMessage(FMLCommonSetupEvent event) {
			DualityMod.addNetworkMessage(AbilityUseMessage.TYPE, AbilityUseMessage.STREAM_CODEC, AbilityUseMessage::handleData);
		}
	}

	public static void execute(Entity entity) {
		execute(null, entity);
	}

	private static void execute(@Nullable Event event, Entity entity) {
		if (entity == null)
			return;
		String projectileId = "";
		if ((entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getItem() == ItemStack.EMPTY.getItem()) {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			projectileId = entity.getData(DualityModVariables.PLAYER_VARIABLES).selected_ability;
			if (!(projectileId).isEmpty()) {
				AbilitySublet.use(player, projectileId);
			}
		}
	}
}