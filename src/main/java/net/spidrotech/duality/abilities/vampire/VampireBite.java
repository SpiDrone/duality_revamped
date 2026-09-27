package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.village.entity.DualityNpcEntity;
import net.spidrotech.duality.item.athame.BloodSample;
import net.spidrotech.duality.item.athame.AthameEvents;
import net.spidrotech.duality.charactercreation.CharacterProgress;

import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.ChatFormatting;

/**
 * A vampire with the vampire out (VampireMode) bites instead of punching: a bare-handed hit drinks
 * from whatever it lands on.
 *
 * <p>How much is drunk follows how hard the bite landed - {@link #DAMAGE_PER_DOSE} damage is one
 * dose, the same measure a vial uses - and some of the target's mana comes with it, the harder the
 * bite the more (see AthameEvents#sapMana). Everything else is BloodDrinking's rules, exactly as for
 * a vial: a person's blood is worth the most, animal blood is refused unless the vampire is Vegan
 * or desperate (the hit still lands, they just don't drink), tainted blood poisons them.
 *
 * <p>A bite that kills a village NPC is killing a person to feed, and makes the vampire
 * {@link CharacterProgress#SOULLESS}.
 */
@EventBusSubscriber(modid = "duality")
public final class VampireBite {
	/** Damage per dose drunk: a 4-damage bite drinks as much as one dose from a vial. */
	public static final double DAMAGE_PER_DOSE = 4.0;

	private VampireBite() {
	}

	/** The vampire behind this damage if it was a bite, else null. */
	private static ServerPlayer biter(DamageSource source) {
		// A feeding sip hurts its donor too, but VampireFeeding handles its own blood and kills.
		if (VampireFeeding.isSipping() || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player)
			return null;
		if (!player.getMainHandItem().isEmpty() || !VampireRank.isVampire(player) || !VampireMode.isActive(player))
			return null;
		return player;
	}

	@SubscribeEvent
	public static void onDamaged(LivingDamageEvent.Post event) {
		ServerPlayer vampire = biter(event.getSource());
		LivingEntity target = event.getEntity();
		if (vampire == null || target == vampire || event.getNewDamage() <= 0 || !AthameEvents.bleeds(target))
			return;
		BloodSample blood = AthameEvents.sampleOf(target);
		String refusal = BloodDrinking.refusal(vampire, blood);
		if (refusal != null) {
			vampire.displayClientMessage(Component.literal(refusal).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		double doses = event.getNewDamage() / DAMAGE_PER_DOSE;
		double mana = AthameEvents.sapMana(target, doses);
		BloodDrinking.drink(vampire, blood, doses, mana, false); // a bite is never given freely
		Vampirism.tryInfect(vampire, target, Vampirism.Via.BITE);
		vampire.level().playSound(null, target.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8f, 0.6f);
		if (vampire.level() instanceof ServerLevel level)
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), target.getX(), target.getY() + target.getBbHeight() * 0.75,
					target.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		ServerPlayer vampire = biter(event.getSource());
		if (vampire != null && BloodDrinking.isPerson(event.getEntity()))
			BloodDrinking.becomeSoulless(vampire, "You drank a life away. Something in you goes quiet for good. (Soulless)");
	}
}
