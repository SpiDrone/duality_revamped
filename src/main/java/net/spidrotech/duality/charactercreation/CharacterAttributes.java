package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.init.DualityModAttributes;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * Turns a character's stat line into attributes on the player, and back again.
 *
 * <p>This is the one file that knows what a point of a skill is actually worth. Everything else in
 * the package treats skills as numbers; retuning the game feel of a stat happens here and nowhere
 * else.
 *
 * <p>Modifiers are permanent and keyed by a stable id per skill, so re-applying is idempotent - the
 * old modifier is removed and replaced rather than stacked. That matters because this runs on every
 * login and on every character switch, not just once at creation.
 *
 * <p>Not every skill moves an attribute. PRESENCE and INSIGHT have nothing to hang on yet and live
 * on the character sheet only; read them with {@link #skillOf}. That's deliberate - a skill that
 * isn't wired to anything is still a number other systems can start using (village standing is the
 * obvious first customer for PRESENCE).
 */
public final class CharacterAttributes {
	/** Where the stat line is stored on a character sheet. */
	public static final String SHEET_SKILLS = "skills";
	/** Points earned at creation but not spent, for the in-game stat screen to hand out later. */
	public static final String SHEET_UNSPENT = "unspent_skill_points";
	/** Powers the character started with, chosen and granted together. */
	public static final String SHEET_ABILITIES = "starting_abilities";

	private CharacterAttributes() {
	}

	// ------------------------------------------------------------------------------ what a point is worth
	/** Extra attack damage per point of Strength above the floor. */
	private static final double STRENGTH_DAMAGE = 0.25;
	/** Fractional movement speed per point of Agility. */
	private static final double AGILITY_SPEED = 0.02;
	/** Extra hearts (in half-hearts) per point of Endurance. */
	private static final double ENDURANCE_HEALTH = 1.0;
	/** Orbing proficiency per point of Attunement; the attribute itself is capped 1-10. */
	private static final double ATTUNEMENT_PROFICIENCY = 1.0;
	/** Orb charge time cut per point of Attunement; the attribute is capped 0-1. */
	private static final double ATTUNEMENT_CHARGE = 0.04;
	/** Vanilla luck per point of Fortune. */
	private static final double FORTUNE_LUCK = 0.5;

	/**
	 * Applies a stat line to a player, replacing whatever was there.
	 *
	 * <p>Call after creating a character, after switching to one, and on login. Safe to call as
	 * often as you like.
	 */
	public static void apply(ServerPlayer player, Map<SkillType, Integer> skills) {
		if (player == null || skills == null)
			return;
		double previousMaxHealth = player.getMaxHealth();
		set(player, Attributes.ATTACK_DAMAGE, SkillType.STRENGTH, above(skills, SkillType.STRENGTH) * STRENGTH_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.MOVEMENT_SPEED, SkillType.AGILITY, above(skills, SkillType.AGILITY) * AGILITY_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.MAX_HEALTH, SkillType.ENDURANCE, above(skills, SkillType.ENDURANCE) * ENDURANCE_HEALTH, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.LUCK, SkillType.FORTUNE, above(skills, SkillType.FORTUNE) * FORTUNE_LUCK, AttributeModifier.Operation.ADD_VALUE);
		set(player, DualityModAttributes.ORBING_PROFICIENCY, SkillType.ATTUNEMENT, above(skills, SkillType.ATTUNEMENT) * ATTUNEMENT_PROFICIENCY,
				AttributeModifier.Operation.ADD_VALUE);
		set(player, DualityModAttributes.ORBCHARGEREDUCTION, SkillType.ATTUNEMENT, above(skills, SkillType.ATTUNEMENT) * ATTUNEMENT_CHARGE,
				AttributeModifier.Operation.ADD_VALUE);
		// Raising max health leaves the player on their old total; lowering it can leave them above
		// the new one, which the client renders as an overfull bar until something else touches it.
		if (player.getMaxHealth() > previousMaxHealth)
			player.setHealth(player.getHealth() + (float) (player.getMaxHealth() - previousMaxHealth));
		else if (player.getHealth() > player.getMaxHealth())
			player.setHealth(player.getMaxHealth());
	}

	/** Strips every skill modifier. Used when a character dies or is swapped away from, so the next
	 *  one doesn't inherit the last one's body. */
	public static void clear(ServerPlayer player) {
		if (player == null)
			return;
		for (SkillType skill : SkillType.values()) {
			remove(player, Attributes.ATTACK_DAMAGE, skill);
			remove(player, Attributes.MOVEMENT_SPEED, skill);
			remove(player, Attributes.MAX_HEALTH, skill);
			remove(player, Attributes.LUCK, skill);
			remove(player, DualityModAttributes.ORBING_PROFICIENCY, skill);
			remove(player, DualityModAttributes.ORBCHARGEREDUCTION, skill);
		}
		if (player.getHealth() > player.getMaxHealth())
			player.setHealth(player.getMaxHealth());
	}

	// ----------------------------------------------------------------------------- the sheet
	/** Reads a character's stat line off their sheet. Missing skills come back at the floor. */
	public static Map<SkillType, Integer> readSkills(JsonObject sheet) {
		Map<SkillType, Integer> skills = new EnumMap<>(SkillType.class);
		JsonObject stored = sheet != null && sheet.has(SHEET_SKILLS) ? sheet.getAsJsonObject(SHEET_SKILLS) : null;
		for (SkillType skill : SkillType.values()) {
			skills.put(skill, stored != null && stored.has(skill.id()) ? stored.get(skill.id()).getAsInt() : SkillType.MIN);
		}
		return skills;
	}

	public static void writeSkills(JsonObject sheet, Map<SkillType, Integer> skills) {
		JsonObject stored = new JsonObject();
		for (SkillType skill : SkillType.values()) {
			stored.addProperty(skill.id(), skills.getOrDefault(skill, SkillType.MIN));
		}
		sheet.add(SHEET_SKILLS, stored);
	}

	/**
	 * One skill of whatever character this player currently is.
	 *
	 * <p>This is the hook for anything that wants to ask "how charming is this player" without
	 * knowing the creator exists - village standing, dialogue checks, trade prices.
	 */
	public static int skillOf(Player player, SkillType skill) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId.isEmpty())
			return SkillType.MIN;
		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, characterId);
		return readSkills(sheet).getOrDefault(skill, SkillType.MIN);
	}

	/** Re-applies the active character's stat line. Call on login and after a character switch. */
	public static void applyActiveCharacter(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId.isEmpty()) {
			clear(player);
			return;
		}
		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, characterId);
		if (sheet == null) {
			clear(player);
			return;
		}
		apply(player, readSkills(sheet));
	}

	/** Points this character earned at creation and hasn't spent, for the stat screen to offer. */
	public static int unspentPoints(Player player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId.isEmpty())
			return 0;
		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, characterId);
		return sheet != null && sheet.has(SHEET_UNSPENT) ? sheet.get(SHEET_UNSPENT).getAsInt() : 0;
	}

	/**
	 * Spends one of those points from the in-game stat screen. Returns false without changing
	 * anything if there are none left or the skill is already at its ceiling for this character.
	 */
	public static boolean spendPoint(ServerPlayer player, SkillType skill) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId.isEmpty() || skill == null)
			return false;
		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, characterId);
		if (sheet == null)
			return false;
		int unspent = sheet.has(SHEET_UNSPENT) ? sheet.get(SHEET_UNSPENT).getAsInt() : 0;
		if (unspent <= 0)
			return false;
		Map<SkillType, Integer> skills = readSkills(sheet);
		if (skills.get(skill) >= SkillType.MAX)
			return false;
		skills.put(skill, skills.get(skill) + 1);
		writeSkills(sheet, skills);
		sheet.addProperty(SHEET_UNSPENT, unspent - 1);
		DualityDatabaseManager.saveCharacterSheet(player, characterId, sheet);
		apply(player, skills);
		return true;
	}

	// -------------------------------------------------------------------------------- helpers
	private static int above(Map<SkillType, Integer> skills, SkillType skill) {
		return Math.max(0, skills.getOrDefault(skill, SkillType.MIN) - SkillType.MIN);
	}

	private static ResourceLocation modifierId(SkillType skill) {
		return ResourceLocation.fromNamespaceAndPath("duality", "skill_" + skill.id());
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, SkillType skill, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null)
			return;
		ResourceLocation id = modifierId(skill);
		instance.removeModifier(id);
		if (amount != 0)
			instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
	}

	private static void remove(ServerPlayer player, Holder<Attribute> attribute, SkillType skill) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null)
			instance.removeModifier(modifierId(skill));
	}
}
