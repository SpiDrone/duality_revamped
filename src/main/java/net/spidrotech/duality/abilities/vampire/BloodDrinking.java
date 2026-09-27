package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.item.athame.BloodSample;
import net.spidrotech.duality.charactercreation.CharacterProgress;

import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;

import com.mojang.serialization.Codec;

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
@EventBusSubscriber(modid = "duality")
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

	/** How much reserve a dose of this blood is worth to a vampire who drinks it - which is also what
	 *  it costs a vampire to draw a dose of their own (AthameEvents#cutSelf), so that's an even trade. */
	public static double bloodPerDose(BloodSample sample) {
		return RESERVE_PER_DOSE * Math.max(0.25, sample.strength()) * (sample.kind().sapient() ? PERSON : ANIMAL_VEGAN);
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

	/** A whole vial: every dose and all the mana in it. Willing only if the source cut themselves for
	 *  it (see AthameEvents#cutSelf) - blood the athame took from someone is never given. */
	public static void drink(ServerPlayer player, BloodSample sample) {
		player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1f, 0.8f);
		drink(player, sample, sample.doses(), sample.mana(), sample.willing());
	}

	/**
	 * Drinks {@code doses} (fractional - a light bite is a sip) of this blood, with {@code mana}
	 * riding along in it. The one place the rules live, for vials, bites and feeding alike. Plays no
	 * sound; callers pick their own.
	 *
	 * @param willing whether a person gave this blood freely (see VampireFeeding). Drinking a
	 *                person's blood any other way costs a vampire Vegan. Meaningless for animal blood.
	 */
	public static void drink(ServerPlayer player, BloodSample sample, double doses, double mana, boolean willing) {
		if (doses <= 0)
			return;
		if (sample.toxic()) {
			player.addEffect(new MobEffectInstance(MobEffects.POISON, (int) Math.max(40, 100 * doses)));
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200));
			player.displayClientMessage(Component.literal("That blood was tainted.").withStyle(ChatFormatting.DARK_GREEN), true);
			return;
		}
		// Acidic blood (green) burns going down - vampire or not; a vampire still feeds on it below.
		if (sample.color() == BloodSample.GREEN) {
			player.addEffect(new MobEffectInstance(MobEffects.POISON, (int) Math.max(60, 120 * doses)));
			player.displayClientMessage(Component.literal("The blood is acid - it burns all the way down.").withStyle(ChatFormatting.GREEN), true);
		}
		if (!VampireRank.isVampire(player)) {
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200));
			player.displayClientMessage(Component.literal("You gag on the blood.").withStyle(ChatFormatting.GRAY), true);
			// Vampire blood always takes: the disease is in them now (see Vampirism).
			if (sample.isVampireBlood())
				Vampirism.infect(player, sample.sourceId(), sample.sourceName(), sample.vampireRank(), Vampirism.Via.BLOOD);
			return;
		}

		boolean vegan = CharacterProgress.hasProficiency(player, CharacterProgress.VEGAN);
		double multiplier = sample.kind().sapient() ? PERSON : vegan ? ANIMAL_VEGAN : ANIMAL_DESPERATE;
		double potency = Math.max(0.25, sample.strength()) * multiplier * doses;
		double before = reserve(player);
		setReserve(player, before + RESERVE_PER_DOSE * potency);
		player.heal((float) (HEAL_PER_DOSE * potency));
		// The mana sapped along with the blood - all of it, whatever kind of blood it rode in on.
		if (mana > 0)
			net.spidrotech.duality.mana.Mana.add(player, mana);

		if (sample.kind().sapient()) {
			if (sample.quality() >= 35) {
				int ticks = (int) Math.round(400 * doses);
				// Topped up rather than restarted, so a string of bites keeps it going without stacking.
				extend(player, MobEffects.DAMAGE_BOOST, ticks);
				extend(player, MobEffects.MOVEMENT_SPEED, ticks);
			}
			// Everything this drink put in the reserve is a person's blood, until it's burned off - except
			// a Vegan vampire's, which has none of it in them to pass on.
			if (!(sample.isVampireBlood() && sample.vegan()))
				addPersonBlood(player, reserve(player) - before);
			if (!willing && vegan)
				loseVegan(player);
			VampireMode.refreshEyes(player);
			return;
		}

		if (!vegan) {
			// The strain of drinking what a vampire's body rejects.
			player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160));
			if (CharacterProgress.hasProficiency(player, CharacterProgress.SOULLESS))
				return; // no way back to Vegan once Soulless - the strain is all it gets them
			double progress = CharacterProgress.addProgress(player, CharacterProgress.SHEET_VEGAN_PROGRESS, doses);
			if (progress >= VEGAN_THRESHOLD && CharacterProgress.grantProficiency(player, CharacterProgress.VEGAN)) {
				player.sendSystemMessage(Component.literal("Your body has stopped fighting animal blood. (Vegan)").withStyle(ChatFormatting.GREEN));
				VampireMode.refreshEyes(player);
				VampireStatus.sync(player);
			}
		}
	}

	// ------------------------------------------------------------------------------ vegan & soulless
	/** A person, rather than an animal: another player, or a village NPC. Killing one to feed makes a vampire Soulless. */
	public static boolean isPerson(net.minecraft.world.entity.LivingEntity entity) {
		return entity instanceof net.minecraft.world.entity.player.Player || entity instanceof net.spidrotech.duality.village.entity.DualityNpcEntity;
	}

	/** Drinking from someone who didn't give it: Vegan is gone, and has to be earned again from the start. */
	public static void loseVegan(ServerPlayer player) {
		if (!CharacterProgress.revokeProficiency(player, CharacterProgress.VEGAN))
			return;
		CharacterProgress.setProgress(player, CharacterProgress.SHEET_VEGAN_PROGRESS, 0);
		player.sendSystemMessage(Component.literal("You took what wasn't given. Your body remembers the taste of people. (Vegan lost)").withStyle(ChatFormatting.RED));
		VampireMode.refreshEyes(player);
		VampireStatus.sync(player);
	}

	/**
	 * A vampire who has killed a person to feed. Permanent, and it closes the Vegan road for good:
	 * the trait is taken if they had it, and can never be earned again.
	 */
	public static void becomeSoulless(ServerPlayer player, String message) {
		if (!CharacterProgress.grantProficiency(player, CharacterProgress.SOULLESS))
			return;
		CharacterProgress.revokeProficiency(player, CharacterProgress.VEGAN);
		player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.DARK_RED));
		VampireMode.refreshEyes(player);
		VampireStatus.sync(player);
	}

	// ------------------------------------------------------------------------- person's blood
	/**
	 * How much of a vampire's blood reserve came from people - what keeps a Vegan's eyes red. It's
	 * the first to be spent: every point of blood used burns off a point of it (see Mana#set), so it
	 * clears once they've used as much blood as they drank from people.
	 */
	public static final AttachmentType<Double> PERSON_BLOOD = AttachmentType.builder(() -> 0.0).serialize(Codec.DOUBLE).copyOnDeath().build();

	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
		event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, helper -> helper.register(ResourceLocation.fromNamespaceAndPath("duality", "person_blood"), PERSON_BLOOD));
	}

	public static double personBlood(ServerPlayer player) {
		return player.getData(PERSON_BLOOD);
	}

	private static void addPersonBlood(ServerPlayer player, double amount) {
		if (amount > 0)
			player.setData(PERSON_BLOOD, Math.min(RESERVE_MAX, personBlood(player) + amount));
	}

	/** Burns off person's blood as blood is spent. Called by Mana when a vampire's blood falls. */
	public static void metabolize(ServerPlayer player, double spent) {
		double had = personBlood(player);
		if (had <= 0 || spent <= 0)
			return;
		double left = Math.max(0, had - spent);
		player.setData(PERSON_BLOOD, left);
		if (left <= 0)
			VampireMode.refreshEyes(player); // gold again, if they're Vegan
	}

	private static void extend(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks) {
		MobEffectInstance current = player.getEffect(effect);
		int remaining = current == null ? 0 : current.getDuration();
		player.addEffect(new MobEffectInstance(effect, Math.min(remaining + ticks, 20 * 120)));
	}
}
