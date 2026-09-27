package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.item.athame.BloodSample;
import net.spidrotech.duality.charactercreation.CharacterProgress;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * What drinking a vial of blood does, and who can stomach it.
 *
 * <p>For a vampire, blood tops up their reserve ({@code PlayerVariables.blood}, 0 to
 * {@link #RESERVE_MAX}) and heals them. How much depends on how strong the blood was and whose it
 * was: a person's is worth the most, and potent blood from a person also gives a burst of
 * strength and speed. Any mana the athame sapped along with the blood goes to the vampire too.
 *
 * <p>Animal blood is the exception. A vampire can't bring themselves to drink it unless they're
 * desperate - badly hurt, or nearly out of blood - and then it does little and turns their stomach.
 * Every dose drunk that way counts towards {@link CharacterProgress#VEGAN}. Once earned, animal
 * blood goes down without a fight and is worth a good deal more: the one road to power that doesn't
 * run through killing people (which earns {@link CharacterProgress#SOULLESS} instead - see the
 * athame).
 *
 * <p>Anyone who isn't a vampire just gets sick. Tainted blood (negative quality, like a zombie's)
 * poisons whoever drinks it.
 */
public final class BloodDrinking {
	public static final double RESERVE_MAX = 100;
	/** Below this share of max health a vampire counts as desperate. */
	public static final double DESPERATE_HEALTH = 0.35;
	/** ...or below this share of their blood reserve. */
	public static final double DESPERATE_RESERVE = 0.2;
	/** Doses of animal blood drunk in desperation before a vampire earns Vegan. */
	public static final int VEGAN_THRESHOLD = 24;

	/** Reserve and healing per dose, scaled by strength and the multipliers below. */
	private static final double RESERVE_PER_DOSE = 8, HEAL_PER_DOSE = 2;
	private static final double PERSON = 1.0, ANIMAL_VEGAN = 0.6, ANIMAL_DESPERATE = 0.25;

	private BloodDrinking() {
	}

	public static double reserve(ServerPlayer player) {
		return player.getData(DualityModVariables.PLAYER_VARIABLES).blood;
	}

	private static void setReserve(ServerPlayer player, double value) {
		DualityModVariables.PlayerVariables vars = player.getData(DualityModVariables.PLAYER_VARIABLES);
		vars.blood = Math.max(0, Math.min(RESERVE_MAX, value));
		vars.markSyncDirty();
	}

	public static boolean desperate(ServerPlayer player) {
		return player.getHealth() <= player.getMaxHealth() * DESPERATE_HEALTH || reserve(player) <= RESERVE_MAX * DESPERATE_RESERVE;
	}

	/** Why this player won't drink this, or null if they will. The vial isn't used up on a refusal. */
	public static String refusal(ServerPlayer player, BloodSample sample) {
		if (!VampireRank.isVampire(player) || sample.kind().sapient() || sample.toxic())
			return null;
		if (CharacterProgress.hasProficiency(player, CharacterProgress.VEGAN) || desperate(player))
			return null;
		return "Animal blood turns your stomach. You'd have to be desperate.";
	}

	/** A whole vial: every dose and all the mana in it. */
	public static void drink(ServerPlayer player, BloodSample sample) {
		player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1f, 0.8f);
		drink(player, sample, sample.doses(), sample.mana());
	}

	/**
	 * Drinks {@code doses} (fractional - a light bite is a sip) of this blood, with {@code mana}
	 * riding along in it. The one place the rules live, for vials and bites alike. Plays no sound;
	 * callers pick their own.
	 */
	public static void drink(ServerPlayer player, BloodSample sample, double doses, double mana) {
		if (doses <= 0)
			return;
		if (sample.toxic()) {
			player.addEffect(new MobEffectInstance(MobEffects.POISON, (int) Math.max(40, 100 * doses)));
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200));
			player.displayClientMessage(Component.literal("That blood was tainted.").withStyle(ChatFormatting.DARK_GREEN), true);
			return;
		}
		if (!VampireRank.isVampire(player)) {
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200));
			player.displayClientMessage(Component.literal("You gag on the blood.").withStyle(ChatFormatting.GRAY), true);
			return;
		}

		boolean vegan = CharacterProgress.hasProficiency(player, CharacterProgress.VEGAN);
		double multiplier = sample.kind().sapient() ? PERSON : vegan ? ANIMAL_VEGAN : ANIMAL_DESPERATE;
		double potency = Math.max(0.25, sample.strength()) * multiplier * doses;
		setReserve(player, reserve(player) + RESERVE_PER_DOSE * potency);
		player.heal((float) (HEAL_PER_DOSE * potency));
		// The mana sapped along with the blood - all of it, whatever kind of blood it rode in on.
		if (mana > 0)
			net.spidrotech.duality.mana.Mana.add(player, mana);

		if (sample.kind().sapient() && sample.quality() >= 35) {
			int ticks = (int) Math.round(400 * doses);
			// Topped up rather than restarted, so a string of bites keeps it going without stacking.
			extend(player, MobEffects.DAMAGE_BOOST, ticks);
			extend(player, MobEffects.MOVEMENT_SPEED, ticks);
		}

		if (!sample.kind().sapient() && !vegan) {
			// The strain of drinking what a vampire's body rejects.
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
			double progress = CharacterProgress.addProgress(player, CharacterProgress.SHEET_VEGAN_PROGRESS, doses);
			if (progress >= VEGAN_THRESHOLD && CharacterProgress.grantProficiency(player, CharacterProgress.VEGAN))
				player.sendSystemMessage(Component.literal("Your body has stopped fighting animal blood. (Vegan)").withStyle(ChatFormatting.GREEN));
		}
	}

	private static void extend(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks) {
		MobEffectInstance current = player.getEffect(effect);
		int remaining = current == null ? 0 : current.getDuration();
		player.addEffect(new MobEffectInstance(effect, Math.min(remaining + ticks, 20 * 120)));
	}
}
