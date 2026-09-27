package net.spidrotech.duality.vanquish;

import net.spidrotech.duality.vanquish.client.VanquishClient;
import net.spidrotech.duality.skin.SkinTempModify;
import net.spidrotech.duality.skin.SkinPartTarget;
import net.spidrotech.duality.skin.SkinEffects;
import net.spidrotech.duality.init.DualityModItems;
import net.spidrotech.duality.charactercreation.CharacterProgress;
import net.spidrotech.duality.charactercreation.CharacterCreation;
import net.spidrotech.duality.DualityDatabaseManager;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;

import com.google.gson.JsonObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.Map;

/**
 * A demon's vanquish: their canon death.
 *
 * <p>When a demon character takes a killing blow from magic - any power (they all hit with magic
 * damage, see {@link #isVanquishingBlow}), a witch's potion, or an athame - they don't just fall
 * over. The death is held off and this plays instead, over {@link #DURATION_TICKS}:
 * <ol>
 * <li>they freeze in place, untouchable, and fire cracks split across their whole skin
 * (SkinEffects#VANQUISH), with flames swirling round them;
 * <li>from {@link #SINK_START} they're dragged down through the ground (drawn client side - see
 * VanquishClient - while the player themselves is pinned where they stood);
 * <li>then the character's death becomes canon ({@link DualityDatabaseManager#handleCanonDeath},
 * recorded as sent to the wasteland) and the player dies for real. The character is gone; on
 * respawn they have no one, and the character creator opens.
 * </ol>
 *
 * <p>Everything else that reacts to the killing blow still gets to - this listens last, so an
 * athame still takes its power from the demon before the death is held off.
 *
 * <p>WASTELAND: when that dimension exists, {@link #finish} is where the vanquished should be sent
 * there instead of dying.
 */
@EventBusSubscriber(modid = "duality")
public final class Vanquish {
	public static final int DURATION_TICKS = 70;
	/** When the sinking starts - the cracks (45 ticks, see SkinEffects#VANQUISH) are nearly done. */
	public static final int SINK_START = 40;
	private static final String SKIN_KEY = "vanquish";
	public static final String AFTERLIFE = "WASTELAND";

	/** Players being vanquished, with when it started and where they're held. */
	private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();
	/** Players whose final, real death is in progress - so it isn't caught and held again. */
	private static final Map<UUID, Boolean> FINISHING = new ConcurrentHashMap<>();

	private record State(long startTick, Vec3 anchor, String cause) {
	}

	private Vanquish() {
	}

	public static boolean isVanquishing(Player player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	/** Whether this killing blow was magic: any power, a potion, or an athame's cut. */
	public static boolean isVanquishingBlow(DamageSource source) {
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.SONIC_BOOM))
			return true;
		return source.getEntity() instanceof Player attacker && source.getDirectEntity() == attacker && attacker.getMainHandItem().is(DualityModItems.ATHAME.get());
	}

	private static boolean isDemon(ServerPlayer player) {
		JsonObject sheet = CharacterProgress.activeSheet(player);
		return sheet != null && "demon".equals(CharacterCreation.raceIdOf(sheet));
	}

	// ------------------------------------------------------------------------------ trigger
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || FINISHING.containsKey(player.getUUID()))
			return;
		if (isVanquishing(player)) {
			// Something got through mid-sequence: hold it, the sequence will finish them.
			event.setCanceled(true);
			player.setHealth(1f);
			return;
		}
		if (!isVanquishingBlow(event.getSource()) || !isDemon(player))
			return;
		event.setCanceled(true);
		begin(player, event.getSource());
	}

	/** Starts the sequence. Public for a future admin command or scripted vanquish. */
	public static void begin(ServerPlayer player, DamageSource cause) {
		player.setHealth(1f);
		player.clearFire();
		player.removeAllEffects();
		long now = player.level().getGameTime();
		String causeText = cause.getEntity() != null ? "Vanquished by " + cause.getEntity().getName().getString() : "Vanquished";
		ACTIVE.put(player.getUUID(), new State(now, player.position(), causeText));
		SkinTempModify.add(player).part(SkinPartTarget.OVERLAY).effect(SkinEffects.VANQUISH).key(SKIN_KEY);
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new VanquishPayload(player.getId(), now));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.5f);
		player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.6f);
	}

	// ------------------------------------------------------------------------------- sequence
	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		State state = ACTIVE.get(player.getUUID());
		if (state == null)
			return;
		long elapsed = player.level().getGameTime() - state.startTick();
		// Pinned where the blow landed; the sinking is drawn, not simulated.
		player.setDeltaMovement(Vec3.ZERO);
		if (player.position().distanceToSqr(state.anchor()) > 0.0025)
			player.teleportTo(state.anchor().x, state.anchor().y, state.anchor().z);
		if (player.level() instanceof ServerLevel level)
			flames(level, player, elapsed);
		if (elapsed == SINK_START)
			player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
		if (elapsed >= DURATION_TICKS)
			finish(player, state);
	}

	/** A ring of flame turning round them, climbing as the fire takes hold, plus embers and smoke;
	 *  once they start sinking it pours down into the hole instead. */
	private static void flames(ServerLevel level, ServerPlayer player, long elapsed) {
		Vec3 at = state(player) == null ? player.position() : state(player).anchor();
		float intensity = Math.min(1f, elapsed / (float) SINK_START);
		int points = 6 + Math.round(10 * intensity);
		double radius = 0.8 - 0.3 * intensity;
		for (int i = 0; i < points; i++) {
			double angle = (elapsed * 0.35) + i * (Math.PI * 2 / points);
			double height = elapsed < SINK_START ? player.getRandom().nextDouble() * 2.0 * intensity : player.getRandom().nextDouble() * 0.4;
			level.sendParticles(ParticleTypes.FLAME, at.x + Math.cos(angle) * radius, at.y + height, at.z + Math.sin(angle) * radius, 1, 0, 0.02, 0, 0.01);
		}
		if (elapsed % 3 == 0)
			level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1.0, at.z, 1, 0.3, 0.5, 0.3, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, elapsed >= SINK_START ? 4 : 1, 0.4, 0.1, 0.4, 0.01);
		if (elapsed % 10 == 0)
			level.playSound(null, player.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.8f);
	}

	private static State state(ServerPlayer player) {
		return ACTIVE.get(player.getUUID());
	}

	/** The death becomes canon, then real. */
	private static void finish(ServerPlayer player, State state) {
		ACTIVE.remove(player.getUUID());
		SkinTempModify.removeKey(player, SKIN_KEY);
		makeCanon(player, state.cause());
		player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 0.5f);
		if (player.getServer() != null)
			player.getServer().getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().getName() + " was vanquished.").withStyle(ChatFormatting.DARK_RED), false);
		FINISHING.put(player.getUUID(), true);
		try {
			player.kill();
		} finally {
			FINISHING.remove(player.getUUID());
		}
	}

	/** Records the canon death, with where they went. */
	private static void makeCanon(ServerPlayer player, String cause) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		DualityDatabaseManager.handleCanonDeath(player, cause);
		JsonObject sheet = characterId.isEmpty() ? null : DualityDatabaseManager.getCharacterSheet(player, characterId);
		if (sheet != null) {
			sheet.addProperty("afterlife_realm", AFTERLIFE);
			DualityDatabaseManager.saveCharacterSheet(player, characterId, sheet);
		}
		// The ones worth remembering go on the Wasteland's list (and lose their powers there).
		if (!characterId.isEmpty() && player.getServer() != null)
			Wasteland.admit(player.getServer(), characterId, player.getStringUUID(), cause);
	}

	// ----------------------------------------------------------------------------- safeguards
	/** Nothing hurts them mid-sequence. */
	@SubscribeEvent
	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && isVanquishing(player) && !FINISHING.containsKey(player.getUUID()))
			event.setCanceled(true);
	}

	/** Leaving mid-sequence doesn't escape it: the death is canon either way. */
	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		State state = ACTIVE.remove(player.getUUID());
		if (state != null) {
			SkinTempModify.removeKey(player, SKIN_KEY);
			makeCanon(player, state.cause());
		}
	}

	// -------------------------------------------------------------------------------- network
	/** "This player is being vanquished, starting at this game tick" - to them and everyone watching. */
	public record VanquishPayload(int entityId, long startTick) implements CustomPacketPayload {
		public static final Type<VanquishPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "vanquish"));
		public static final StreamCodec<FriendlyByteBuf, VanquishPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, VanquishPayload payload) -> {
			buf.writeVarInt(payload.entityId());
			buf.writeVarLong(payload.startTick());
		}, (FriendlyByteBuf buf) -> new VanquishPayload(buf.readVarInt(), buf.readVarLong()));

		@Override
		public Type<VanquishPayload> type() {
			return TYPE;
		}
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToClient(VanquishPayload.TYPE, VanquishPayload.STREAM_CODEC, Vanquish::handle);
	}

	private static void handle(VanquishPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> VanquishClient.start(payload.entityId(), payload.startTick()));
	}
}
