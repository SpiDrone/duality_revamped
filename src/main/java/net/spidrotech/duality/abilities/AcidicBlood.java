package net.spidrotech.duality.abilities;

import net.spidrotech.duality.item.athame.AthameEvents;
import net.spidrotech.duality.charactercreation.CharacterProgress;
import net.spidrotech.duality.charactercreation.CharacterCreation;

import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;

import com.google.gson.JsonObject;

import java.util.Set;

/**
 * Acidic Blood: blood that fights back.
 *
 * <p><b>Who has it.</b> A character earns the {@link CharacterProgress#ACIDIC_BLOOD} trait by
 * chance: {@link #CHANCE_PER_STEP} each time they take on an acidic power, and again for every
 * proficiency level they gain in one (see {@link #rollOnGain}). Scabber demons, and creatures
 * tagged {@code duality:acidic_blood} (demon spiders, the spider queen), are born with it - it's
 * why their blood runs green.
 *
 * <p><b>What it does.</b>
 * <ul>
 * <li>Their blood is green, and drinking it poisons - a vampire still feeds on it, but it burns
 * going down (see BloodDrinking).
 * <li>Cutting them open up close risks a splash: a melee hit that draws their blood has a
 * {@link #SPLASH_CHANCE} chance to spatter the attacker for thorns-like damage.
 * </ul>
 * A future "blood as a weapon" should read {@link #has}.
 */
@EventBusSubscriber(modid = "duality")
public final class AcidicBlood {
	/** Powers that count as acidic, as characters store them. */
	public static final Set<String> ACIDIC_ABILITIES = Set.of("acid_spit");
	public static final double CHANCE_PER_STEP = 0.05;
	public static final double SPLASH_CHANCE = 0.5;
	/** Splash reach: further than this, the blood doesn't land on them. */
	public static final double SPLASH_RANGE = 3.5;
	public static final float SPLASH_MIN = 1f, SPLASH_MAX = 4f;
	private static final TagKey<EntityType<?>> ACIDIC_TAG = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("duality", "acidic_blood"));

	private AcidicBlood() {
	}

	/** Whether this creature's blood is acidic - see class doc. */
	public static boolean has(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			JsonObject sheet = CharacterProgress.activeSheet(player);
			return sheet != null && ("scabber_demon".equals(CharacterCreation.subspeciesIdOf(sheet)) || CharacterProgress.hasProficiency(player, CharacterProgress.ACIDIC_BLOOD));
		}
		return !(entity instanceof Player) && entity.getType().is(ACIDIC_TAG);
	}

	/**
	 * Rolls for the trait after a character takes on a power: once if it's newly theirs, plus once
	 * per proficiency level it went up. So taking in Acid Spit at proficiency 2 rolls twice; raising
	 * an Acid Spit you already have from 1 to 2 rolls once. Does nothing for a non-acidic power or a
	 * character who already has the trait.
	 */
	public static void rollOnGain(ServerPlayer player, String abilityId, boolean wasOwned, int oldLevel, int newLevel) {
		if (!ACIDIC_ABILITIES.contains(abilityId) || CharacterProgress.hasProficiency(player, CharacterProgress.ACIDIC_BLOOD))
			return;
		int rolls = (wasOwned ? 0 : 1) + Math.max(0, newLevel - (wasOwned ? oldLevel : CharacterProgress.MIN_PROFICIENCY));
		for (int i = 0; i < rolls; i++) {
			if (player.getRandom().nextDouble() < CHANCE_PER_STEP) {
				CharacterProgress.grantProficiency(player, CharacterProgress.ACIDIC_BLOOD);
				player.sendSystemMessage(Component.literal("Your blood turns thick and green, and it burns. (Acidic Blood)").withStyle(ChatFormatting.GREEN));
				return;
			}
		}
	}

	// ------------------------------------------------------------------------------------ splash
	@SubscribeEvent
	public static void onDamaged(LivingDamageEvent.Post event) {
		LivingEntity victim = event.getEntity();
		DamageSource source = event.getSource();
		if (victim.level().isClientSide() || event.getNewDamage() <= 0 || source.is(DamageTypes.THORNS))
			return; // thorns: an acid splash hitting someone acidic mustn't splash straight back
		// Close-range, hand-to-hand: the attacker is what landed the hit.
		if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || attacker == victim)
			return;
		if (attacker.distanceTo(victim) > SPLASH_RANGE || !AthameEvents.bleeds(victim) || !has(victim))
			return;
		if (victim.getRandom().nextDouble() >= SPLASH_CHANCE)
			return;
		float damage = Math.max(SPLASH_MIN, Math.min(SPLASH_MAX, event.getNewDamage() * 0.3f));
		attacker.hurt(victim.damageSources().thorns(victim), damage);
		if (victim.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ITEM_SLIME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 10, 0.25, 0.3, 0.25, 0.05);
			level.playSound(null, attacker.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.7f, 1.5f);
		}
	}
}
