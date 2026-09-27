package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.DualityDatabaseManager;

import net.minecraft.server.level.ServerPlayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What a character has grown into since creation, kept on their character sheet so it swaps with
 * the character like everything else there:
 *
 * <ul>
 * <li><b>Ability proficiency</b> - 1 to {@link #MAX_PROFICIENCY} per power, under
 * {@value #SHEET_ABILITY_PROFICIENCY}. Every power starts at 1. It's the "how good are you with it"
 * number that makes a power stolen from a master stronger than one from a novice (see the athame),
 * and what each ability can scale off as it's tuned - Orb already has its own version.
 * <li><b>Proficiencies</b> - named traits a character has earned, under {@value #SHEET_PROFICIENCIES}:
 * {@link #VEGAN} and {@link #SOULLESS} for now.
 * <li><b>Counters</b> towards earning one, like {@value #SHEET_VEGAN_PROGRESS}.
 * </ul>
 *
 * <p>The powers themselves stay where creation put them (CharacterAttributes.SHEET_ABILITIES).
 * {@link #grantAbility}/{@link #takeAbility} edit that list and re-apply the character, so the
 * radial and everything else that reads the player follows at once.
 */
public final class CharacterProgress {
	public static final String SHEET_ABILITY_PROFICIENCY = "ability_proficiency";
	public static final String SHEET_PROFICIENCIES = "proficiencies";
	public static final String SHEET_VEGAN_PROGRESS = "vegan_progress";
	public static final int MIN_PROFICIENCY = 1, MAX_PROFICIENCY = 10;

	/** A vampire who can live on animal blood without it being a desperate act. */
	public static final String VEGAN = "vegan";
	/** A vampire who has killed a person to feed. */
	public static final String SOULLESS = "soulless";

	private CharacterProgress() {
	}

	/** The active character's sheet, or null if the player isn't anyone right now. */
	public static JsonObject activeSheet(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		return characterId.isEmpty() ? null : DualityDatabaseManager.getCharacterSheet(player, characterId);
	}

	public static void saveActive(ServerPlayer player, JsonObject sheet) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (!characterId.isEmpty() && sheet != null)
			DualityDatabaseManager.saveCharacterSheet(player, characterId, sheet);
	}

	// --------------------------------------------------------------------------- abilities
	public static List<String> abilities(JsonObject sheet) {
		List<String> out = new ArrayList<>();
		if (sheet != null && sheet.has(CharacterAttributes.SHEET_ABILITIES)) {
			for (JsonElement element : sheet.getAsJsonArray(CharacterAttributes.SHEET_ABILITIES)) {
				out.add(element.getAsString());
			}
		}
		return out;
	}

	public static boolean hasAbility(JsonObject sheet, String abilityId) {
		return abilities(sheet).contains(abilityId);
	}

	public static int proficiency(JsonObject sheet, String abilityId) {
		if (sheet == null || !sheet.has(SHEET_ABILITY_PROFICIENCY))
			return MIN_PROFICIENCY;
		JsonObject levels = sheet.getAsJsonObject(SHEET_ABILITY_PROFICIENCY);
		return levels.has(abilityId) ? clamp(levels.get(abilityId).getAsInt()) : MIN_PROFICIENCY;
	}

	public static void setProficiency(JsonObject sheet, String abilityId, int level) {
		JsonObject levels = sheet.has(SHEET_ABILITY_PROFICIENCY) ? sheet.getAsJsonObject(SHEET_ABILITY_PROFICIENCY) : new JsonObject();
		levels.addProperty(abilityId, clamp(level));
		sheet.add(SHEET_ABILITY_PROFICIENCY, levels);
	}

	/**
	 * Gives the active character a power at (at least) the given proficiency and re-applies them.
	 * Returns false - changing nothing - if they already have it at that level or better.
	 */
	public static boolean grantAbility(ServerPlayer player, String abilityId, int level) {
		JsonObject sheet = activeSheet(player);
		if (sheet == null)
			return false;
		boolean owned = hasAbility(sheet, abilityId);
		if (owned && proficiency(sheet, abilityId) >= clamp(level))
			return false;
		if (!owned) {
			JsonArray list = sheet.has(CharacterAttributes.SHEET_ABILITIES) ? sheet.getAsJsonArray(CharacterAttributes.SHEET_ABILITIES) : new JsonArray();
			list.add(abilityId);
			sheet.add(CharacterAttributes.SHEET_ABILITIES, list);
		}
		setProficiency(sheet, abilityId, Math.max(level, proficiency(sheet, abilityId)));
		saveActive(player, sheet);
		CharacterCreation.applyActiveCharacter(player);
		return true;
	}

	/**
	 * Removes a power from a character's sheet (whoever's it is - the athame takes from the dead).
	 * Doesn't re-apply: a dead victim re-applies on respawn, from this very sheet.
	 */
	public static void takeAbility(JsonObject sheet, String abilityId) {
		if (sheet == null || !sheet.has(CharacterAttributes.SHEET_ABILITIES))
			return;
		JsonArray kept = new JsonArray();
		for (JsonElement element : sheet.getAsJsonArray(CharacterAttributes.SHEET_ABILITIES)) {
			if (!element.getAsString().equals(abilityId))
				kept.add(element);
		}
		sheet.add(CharacterAttributes.SHEET_ABILITIES, kept);
		if (sheet.has(SHEET_ABILITY_PROFICIENCY))
			sheet.getAsJsonObject(SHEET_ABILITY_PROFICIENCY).remove(abilityId);
	}

	// ------------------------------------------------------------------------ proficiencies
	public static boolean hasProficiency(ServerPlayer player, String proficiency) {
		JsonObject sheet = activeSheet(player);
		if (sheet == null || !sheet.has(SHEET_PROFICIENCIES))
			return false;
		for (JsonElement element : sheet.getAsJsonArray(SHEET_PROFICIENCIES)) {
			if (element.getAsString().equals(proficiency))
				return true;
		}
		return false;
	}

	/** Returns false if the character already had it. */
	public static boolean grantProficiency(ServerPlayer player, String proficiency) {
		JsonObject sheet = activeSheet(player);
		if (sheet == null || hasProficiency(player, proficiency))
			return false;
		JsonArray list = sheet.has(SHEET_PROFICIENCIES) ? sheet.getAsJsonArray(SHEET_PROFICIENCIES) : new JsonArray();
		list.add(proficiency);
		sheet.add(SHEET_PROFICIENCIES, list);
		saveActive(player, sheet);
		return true;
	}

	/** Adds to a named counter on the active sheet and returns the new total (0 with no character).
	 *  Fractional, so a sip from a bite counts for part of a dose. */
	public static double addProgress(ServerPlayer player, String key, double amount) {
		JsonObject sheet = activeSheet(player);
		if (sheet == null)
			return 0;
		double total = (sheet.has(key) ? sheet.get(key).getAsDouble() : 0) + amount;
		sheet.addProperty(key, total);
		saveActive(player, sheet);
		return total;
	}

	public static int clamp(int level) {
		return Math.max(MIN_PROFICIENCY, Math.min(MAX_PROFICIENCY, level));
	}
}
