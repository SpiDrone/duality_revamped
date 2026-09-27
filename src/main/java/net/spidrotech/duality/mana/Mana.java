package net.spidrotech.duality.mana;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.abilities.vampire.VampireRank;
import net.spidrotech.duality.abilities.vampire.BloodDrinking;

import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import com.mojang.serialization.Codec;

/**
 * Every character's pool of magical energy.
 *
 * <p>How big the pool is comes from the {@link #MAX_MANA} attribute. It sits at {@link #BASE_MAX}
 * and grows with Attunement (see CharacterAttributes), so any other system can add to it with an
 * ordinary attribute modifier. What's in it right now is the {@link #CURRENT} attachment, which
 * refills slowly on its own and is pushed to the owner's HUD whenever it changes (see ManaNetwork).
 *
 * <p>Nothing spends mana yet except the athame, which saps it from whoever it cuts. Abilities
 * drawing on it is a later pass: the API below ({@link #spend}, {@link #drain}, {@link #add}) is
 * what they'll call.
 *
 * <p>Registered from RegisterEvent rather than a DeferredRegister, for the same reason as
 * ModAttachments: the mod constructor that would hand over the event bus is MCreator-generated.
 */
@EventBusSubscriber(modid = "duality")
public final class Mana {
	public static final ResourceLocation MAX_MANA_ID = ResourceLocation.fromNamespaceAndPath("duality", "max_mana");
	public static final double BASE_MAX = 60;
	/** Mana regained per second while below max. */
	public static final double REGEN_PER_SECOND = 2;

	public static final DeferredHolder<Attribute, Attribute> MAX_MANA = DeferredHolder.create(Registries.ATTRIBUTE, MAX_MANA_ID);
	/** Stored with the player; not copied on death - a respawn starts full (see onRespawn). */
	public static final AttachmentType<Double> CURRENT = AttachmentType.builder(() -> -1.0).serialize(Codec.DOUBLE).build();

	private Mana() {
	}

	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
		event.register(Registries.ATTRIBUTE, MAX_MANA_ID, () -> new RangedAttribute("attribute.duality.max_mana", BASE_MAX, 0, 10000).setSyncable(true));
		event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, helper -> helper.register(ResourceLocation.fromNamespaceAndPath("duality", "mana"), CURRENT));
	}

	@SubscribeEvent
	public static void addAttributes(EntityAttributeModificationEvent event) {
		event.add(EntityType.PLAYER, MAX_MANA);
	}

	// ----------------------------------------------------------------------------------- API
	/** Only players have mana. Everything else reads as an empty, zero-size pool. */
	public static boolean hasMana(LivingEntity entity) {
		return entity instanceof Player;
	}

	/**
	 * A vampire's powers run on blood, not mana: for them this whole API reads and writes their
	 * blood reserve ({@code PlayerVariables.blood}, 0 to BloodDrinking.RESERVE_MAX) instead. It
	 * doesn't refill on its own - it comes from feeding - and it drains like hunger (see
	 * VampireHunger). So an ability's cost, an athame's sap and a vial's mana all land on the
	 * blood of a vampire without any of them having to ask.
	 */
	public static boolean usesBlood(LivingEntity entity) {
		return entity instanceof Player && VampireRank.isVampire(entity);
	}

	public static double max(LivingEntity entity) {
		if (!hasMana(entity))
			return 0;
		return usesBlood(entity) ? BloodDrinking.RESERVE_MAX : entity.getAttributeValue(MAX_MANA);
	}

	/** -1 (never set) reads as full, so a brand-new player doesn't start empty. */
	public static double current(LivingEntity entity) {
		if (!hasMana(entity))
			return 0;
		if (usesBlood(entity))
			return entity.getData(DualityModVariables.PLAYER_VARIABLES).blood;
		double stored = entity.getData(CURRENT);
		return stored < 0 ? max(entity) : Math.min(stored, max(entity));
	}

	public static void set(LivingEntity entity, double value) {
		if (!hasMana(entity))
			return;
		double clamped = Math.max(0, Math.min(max(entity), value));
		if (usesBlood(entity)) {
			// Synced to the owner by MCreator's variable sync, which the blood bar reads.
			DualityModVariables.PlayerVariables vars = entity.getData(DualityModVariables.PLAYER_VARIABLES);
			vars.blood = clamped;
			vars.markSyncDirty();
			return;
		}
		entity.setData(CURRENT, clamped);
		if (entity instanceof ServerPlayer player)
			ManaNetwork.sync(player);
	}

	/** Adds up to what fits; returns how much actually went in. */
	public static double add(LivingEntity entity, double amount) {
		if (!hasMana(entity) || amount <= 0)
			return 0;
		double before = current(entity);
		set(entity, before + amount);
		return current(entity) - before;
	}

	/** Takes up to {@code amount} from the target; returns how much was actually taken. */
	public static double drain(LivingEntity target, double amount) {
		if (!hasMana(target) || amount <= 0)
			return 0;
		double before = current(target);
		set(target, before - amount);
		return before - current(target);
	}

	/** All or nothing: spends {@code amount} if there's that much, else changes nothing. */
	public static boolean spend(LivingEntity entity, double amount) {
		if (current(entity) < amount)
			return false;
		set(entity, current(entity) - amount);
		return true;
	}

	// ------------------------------------------------------------------------------ upkeep
	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || usesBlood(player))
			return; // blood doesn't refill by itself - see usesBlood
		double current = current(player);
		if (current < max(player))
			set(player, current + REGEN_PER_SECOND);
		else if (player.getData(CURRENT) != current)
			set(player, current); // max shrank under the pool (a stat change) - settle and re-sync
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			ManaNetwork.sync(player);
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && !usesBlood(player))
			set(player, max(player)); // a vampire's blood carries over death instead (PlayerVariables is cloned)
	}

	@SubscribeEvent
	public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			ManaNetwork.sync(player);
	}
}
