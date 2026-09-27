package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.mana.Mana;
import net.spidrotech.duality.faction.Factions;
import net.spidrotech.duality.faction.FactionRole;
import net.spidrotech.duality.faction.FactionRecord;
import net.spidrotech.duality.faction.FactionKind;
import net.spidrotech.duality.charactercreation.SubraceDefinition;
import net.spidrotech.duality.charactercreation.RaceDefinition;
import net.spidrotech.duality.charactercreation.RaceCatalog;
import net.spidrotech.duality.charactercreation.CharacterProgress;
import net.spidrotech.duality.charactercreation.CharacterCreation;
import net.spidrotech.duality.charactercreation.CharacterAttributes;
import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

/**
 * Vampirism as a disease: caught, carried for {@link #INCUBATION_DAYS} in-game days, then it turns you.
 *
 * <p><b>Catching it.</b> Only a human character can - demons and angels are what they are. A
 * vampire passes it on through:
 * <ul>
 * <li>a feeding sip (VampireFeeding): {@link #FEED_CHANCE} by the sire's rank;
 * <li>a bite (VampireBite): {@link #BITE_CHANCE}, lower still against armour for a Fledgling;
 * <li>their blood, drunk by someone who isn't one (BloodDrinking): always. That's how a Queen turns
 * someone - she never passes it on by accident.
 * </ul>
 * Blood with anti-magic in it (a Hemovoid's) only takes the disease from a Lord or a Queen.
 *
 * <p><b>What it makes of you</b> is rolled from the sire's rank when it's caught, and not told to
 * the infected until it happens: a Queen always makes a Lord; a Lord usually a Fledgling, a Lord
 * {@value #LORD_MAKES_LORD_PERCENT}% of the time; a Fledgling a Thrall
 * {@value #FLEDGLING_MAKES_THRALL_PERCENT}% of the time, otherwise a Fledgling; a Thrall only ever
 * more Thralls. All of it is kept on the character sheet under {@value #SHEET_KEY}.
 *
 * <p><b>Turning.</b> A day in, the fever starts; a day after, their blood slows. On the third day:
 * <ul>
 * <li>a Fledgling or Lord wakes as a vampire of that rank - a Vampire lineage demon, with its
 * powers - in their sire's bloodline;
 * <li>a Thrall is a mindless slave, and for the player that's a canon death: the character is bound
 * to the sire's bloodline as a member, and the player makes someone new.
 * </ul>
 *
 * <p>{@link #cure} ends it before then - the hook for whatever cure comes later.
 */
@EventBusSubscriber(modid = "duality")
public final class Vampirism {
	public static final String SHEET_KEY = "vampirism";
	public static final int INCUBATION_DAYS = 3;
	public static final int LORD_MAKES_LORD_PERCENT = 10;
	public static final int FLEDGLING_MAKES_THRALL_PERCENT = 25;

	/** How the disease was passed on - kept on the sheet, for the story of it. */
	public enum Via {
		BITE, FEEDING, BLOOD
	}

	private Vampirism() {
	}

	/** Chance a feeding sip infects, by the sire's rank (index = rank level; 0 = not a vampire). */
	private static final double[] FEED_CHANCE = { 0, 0.05, 0.10, 0.10, 0 };
	/** Chance a bite infects, by rank. A Fledgling's bite rarely gets through armour ({@link #FLEDGLING_BITE_ARMORED}). */
	private static final double[] BITE_CHANCE = { 0, 0.01, 0.05, 0.05, 0 };
	private static final double FLEDGLING_BITE_ARMORED = 0.01;

	// -------------------------------------------------------------------------------- catching it
	/** Rolls whether this bite or sip passes the disease on, and passes it on if so. */
	public static void tryInfect(ServerPlayer vampire, LivingEntity victim, Via via) {
		if (!(victim instanceof ServerPlayer target))
			return;
		int rank = VampireRank.fromAttribute(vampire).level();
		double chance = via == Via.FEEDING ? FEED_CHANCE[rank] : BITE_CHANCE[rank];
		if (via == Via.BITE && rank == VampireRank.FLEDGLING.level() && target.getArmorValue() > 0)
			chance = FLEDGLING_BITE_ARMORED;
		if (chance <= 0 || vampire.getRandom().nextDouble() >= chance)
			return;
		String sireId = DualityDatabaseManager.getActiveCharacterId(vampire);
		JsonObject sireSheet = CharacterProgress.activeSheet(vampire);
		String sireName = sireSheet != null && sireSheet.has("name") ? sireSheet.get("name").getAsString() : vampire.getGameProfile().getName();
		infect(target, sireId, sireName, rank, via);
	}

	/**
	 * Gives this player's character the disease, from this sire. Returns false - changing nothing -
	 * if they can't catch it (not human, anti-magic blood against a lesser vampire, already carrying
	 * it). Silent: the infected aren't told.
	 */
	public static boolean infect(ServerPlayer victim, String sireCharacterId, String sireName, int sireRank, Via via) {
		JsonObject sheet = CharacterProgress.activeSheet(victim);
		if (sheet == null || sheet.has(SHEET_KEY) || !"human".equals(CharacterCreation.raceIdOf(sheet)))
			return false;
		if ("hemovoid".equals(CharacterCreation.subspeciesIdOf(sheet)) && sireRank < VampireRank.LORD.level())
			return false;
		JsonObject disease = new JsonObject();
		disease.addProperty("sire_character", sireCharacterId == null ? "" : sireCharacterId);
		disease.addProperty("sire_name", sireName);
		disease.addProperty("sire_rank", sireRank);
		disease.addProperty("turns_into", rollTurnRank(sireRank, victim.getRandom()));
		disease.addProperty("infected_day", today(victim));
		disease.addProperty("last_symptom_day", today(victim));
		disease.addProperty("via", via.name());
		sheet.add(SHEET_KEY, disease);
		CharacterProgress.saveActive(victim, sheet);
		DualityMod.LOGGER.info("[duality] {} caught vampirism from {} ({})", victim.getScoreboardName(), sireName, via);
		return true;
	}

	/** What the infected will turn into, from their sire's rank - see class doc. */
	public static int rollTurnRank(int sireRank, RandomSource random) {
		if (sireRank >= VampireRank.QUEEN.level())
			return VampireRank.LORD.level();
		if (sireRank == VampireRank.LORD.level())
			return random.nextInt(100) < LORD_MAKES_LORD_PERCENT ? VampireRank.LORD.level() : VampireRank.FLEDGLING.level();
		if (sireRank == VampireRank.FLEDGLING.level())
			return random.nextInt(100) < FLEDGLING_MAKES_THRALL_PERCENT ? VampireRank.THRALL.level() : VampireRank.FLEDGLING.level();
		return VampireRank.THRALL.level();
	}

	public static boolean isInfected(ServerPlayer player) {
		JsonObject sheet = CharacterProgress.activeSheet(player);
		return sheet != null && sheet.has(SHEET_KEY);
	}

	/** Ends the disease before it turns them. For a future cure. */
	public static boolean cure(ServerPlayer player) {
		JsonObject sheet = CharacterProgress.activeSheet(player);
		if (sheet == null || !sheet.has(SHEET_KEY))
			return false;
		sheet.remove(SHEET_KEY);
		CharacterProgress.saveActive(player, sheet);
		return true;
	}

	private static long today(Player player) {
		return player.level().getDayTime() / 24000L;
	}

	// ----------------------------------------------------------------------------------- carrying it
	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 100 != 0 || !player.isAlive())
			return;
		JsonObject sheet = CharacterProgress.activeSheet(player);
		if (sheet == null || !sheet.has(SHEET_KEY))
			return;
		JsonObject disease = sheet.getAsJsonObject(SHEET_KEY);
		long today = today(player);
		long days = today - disease.get("infected_day").getAsLong();
		if (days < 0) {
			disease.addProperty("infected_day", today); // time wound back - start the count again
			CharacterProgress.saveActive(player, sheet);
			return;
		}
		if (days >= INCUBATION_DAYS) {
			sheet.remove(SHEET_KEY);
			CharacterProgress.saveActive(player, sheet);
			turn(player, disease);
			return;
		}
		if (disease.get("last_symptom_day").getAsLong() < today) {
			disease.addProperty("last_symptom_day", today);
			CharacterProgress.saveActive(player, sheet);
			symptoms(player, days);
		}
	}

	/** The only warning they get. */
	private static void symptoms(ServerPlayer player, long day) {
		if (day <= 1) {
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 60 * 3));
			player.displayClientMessage(Component.literal("You're burning up, and your throat is so dry.").withStyle(ChatFormatting.GRAY), false);
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60 * 3));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 60));
			player.displayClientMessage(Component.literal("Daylight stings. Your heartbeat has slowed to almost nothing.").withStyle(ChatFormatting.GRAY), false);
		}
	}

	// ------------------------------------------------------------------------------------- turning
	private static void turn(ServerPlayer player, JsonObject disease) {
		int rank = disease.get("turns_into").getAsInt();
		String sireName = disease.get("sire_name").getAsString();
		FactionRecord bloodline = bloodlineOf(disease.get("sire_character").getAsString());
		if (rank <= VampireRank.THRALL.level())
			becomeThrall(player, sireName, bloodline);
		else
			becomeVampire(player, rank, sireName, bloodline);
	}

	/** The sire's vampire faction, if they're in one. */
	private static FactionRecord bloodlineOf(String sireCharacterId) {
		FactionRecord faction = sireCharacterId.isEmpty() ? null : Factions.factionOf(sireCharacterId);
		return faction != null && faction.kind() == FactionKind.VAMPIRE ? faction : null;
	}

	/** Makes the sheet a Vampire-lineage demon of this rank, with the lineage's powers. */
	private static void makeVampireSheet(JsonObject sheet, int rank) {
		RaceDefinition demon = RaceCatalog.get("demon");
		SubraceDefinition vampire = demon == null ? null : demon.subrace("vampire");
		JsonObject profile = new JsonObject();
		profile.addProperty("class", "demon");
		profile.addProperty("subspecies", "vampire");
		profile.addProperty("subtier", "Baseline");
		profile.addProperty("level", 1);
		profile.addProperty("xp", 0);
		profile.add("species_bound_unlocks", new JsonArray());
		JsonArray profiles = new JsonArray();
		profiles.add(profile);
		sheet.add("species_profiles", profiles);
		if (vampire != null) {
			JsonArray abilities = sheet.has(CharacterAttributes.SHEET_ABILITIES) ? sheet.getAsJsonArray(CharacterAttributes.SHEET_ABILITIES) : new JsonArray();
			for (String granted : vampire.grantedAbilities()) {
				if (!CharacterProgress.hasAbility(sheet, granted))
					abilities.add(granted);
			}
			sheet.add(CharacterAttributes.SHEET_ABILITIES, abilities);
		}
		sheet.addProperty(CharacterCreation.SHEET_VAMPIRE_RANK, rank);
	}

	private static void becomeVampire(ServerPlayer player, int rank, String sireName, FactionRecord bloodline) {
		JsonObject sheet = CharacterProgress.activeSheet(player);
		if (sheet == null)
			return;
		makeVampireSheet(sheet, rank);
		CharacterProgress.saveActive(player, sheet);
		CharacterCreation.applyActiveCharacter(player); // race tag, powers, rank - see SHEET_VAMPIRE_RANK
		Mana.set(player, 40); // they wake hungry
		if (bloodline != null)
			Factions.addPlayerMember(bloodline, player, FactionRole.MEMBER, "");
		VampireBloodlines.onVampireSpawned(player);
		String rankName = rank >= VampireRank.LORD.level() ? "Lord" : "Fledgling";
		player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1f, 0.6f);
		player.sendSystemMessage(Component.literal("The fever breaks, and you wake starving. " + sireName + "'s blood runs in you now. You are a vampire - a " + rankName + ".")
				.withStyle(ChatFormatting.DARK_RED));
	}

	/** Mindless and bound: the character goes to the sire's bloodline, and the player loses them. */
	private static void becomeThrall(ServerPlayer player, String sireName, FactionRecord bloodline) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		JsonObject sheet = CharacterProgress.activeSheet(player);
		if (sheet == null)
			return;
		String name = sheet.has("name") ? sheet.get("name").getAsString() : player.getGameProfile().getName();
		makeVampireSheet(sheet, VampireRank.THRALL.level());
		sheet.addProperty("thrall", true);
		CharacterProgress.saveActive(player, sheet);
		DualityDatabaseManager.handleCanonDeath(player, "Turned into a thrall by " + sireName);
		JsonObject dead = DualityDatabaseManager.getCharacterSheet(player, characterId);
		if (dead != null) {
			dead.addProperty("afterlife_realm", "THRALL");
			DualityDatabaseManager.saveCharacterSheet(player, characterId, dead);
		}
		if (bloodline != null)
			Factions.addMember(bloodline, characterId, FactionRole.MEMBER, "Thrall");
		player.sendSystemMessage(Component.literal("The fever breaks - and so does your will. " + name + " belongs to " + sireName + "'s bloodline now, a mindless Thrall.")
				.withStyle(ChatFormatting.DARK_RED));
		if (player.getServer() != null)
			player.getServer().getPlayerList().broadcastSystemMessage(Component.literal(name + " has been turned into a thrall.").withStyle(ChatFormatting.DARK_RED), false);
		player.kill();
	}
}
