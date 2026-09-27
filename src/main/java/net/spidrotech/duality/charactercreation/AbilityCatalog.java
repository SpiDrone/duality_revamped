package net.spidrotech.duality.charactercreation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every power screen three can offer, and what buying one costs.
 *
 * <p><b>This is the file to edit</b> alongside {@link AbilityBracket#forSubrace}, the same way
 * {@link RaceCatalog} is the file for races: the content is {@link #registerDefaults()}, and the
 * entries in it are a starting point built from whatever ability ids already existed, not a balance
 * pass. Add an entry there and any subrace whose bracket includes it offers it for free, with no
 * other change.
 *
 * <p>Nothing here depends on Minecraft, same as RaceCatalog - see {@link CharacterDraft}'s class doc
 * for why that matters. Unlike RaceCatalog, this class is NOT synced over the network: it's static
 * data compiled into the mod, identical on both sides already, so there's nothing a client needs
 * telling it doesn't already have.
 */
public final class AbilityCatalog {
	private static final Map<String, AbilityDefinition> ABILITIES = new LinkedHashMap<>();

	static {
		registerDefaults();
	}

	private AbilityCatalog() {
	}

	public static Collection<AbilityDefinition> all() {
		return ABILITIES.values();
	}

	public static AbilityDefinition get(String id) {
		return id == null ? null : ABILITIES.get(id.toLowerCase());
	}

	/** The first catalog entry that grants this ability id, or null - used to find something to show
	 *  in a "what you get for free" list from a subrace's raw {@code grantedAbilities()} strings,
	 *  which aren't catalog ids. Null just means that grant has no catalog entry to describe it; the
	 *  character still gets it regardless, since granting never goes through this catalog at all. */
	public static AbilityDefinition byAbilityId(String abilityId) {
		if (abilityId == null)
			return null;
		for (AbilityDefinition ability : ABILITIES.values()) {
			if (ability.abilityId().equals(abilityId))
				return ability;
		}
		return null;
	}

	public static void register(AbilityDefinition ability) {
		ABILITIES.put(ability.id().toLowerCase(), ability);
	}

	public static void replaceAll(Collection<AbilityDefinition> abilities) {
		ABILITIES.clear();
		for (AbilityDefinition ability : abilities) {
			register(ability);
		}
	}

	/** Every ability a character in this bracket can see - its own bracket, plus every ancestor.
	 *  Doesn't know or care what's already granted for free; see
	 *  {@link CharacterDraft#abilityChoices} for the caller that also excludes those. */
	public static List<AbilityDefinition> selectableFor(AbilityBracket bracket) {
		List<AbilityDefinition> result = new ArrayList<>();
		if (bracket == null)
			return result;
		for (AbilityDefinition ability : ABILITIES.values()) {
			if (bracket.includes(ability.bracket()))
				result.add(ability);
		}
		return result;
	}

	// ============================================================================ starting data
	/**
	 * Placeholder content built from the ability ids the mod already has working procedures for.
	 * Retune freely, same license RaceCatalog's own doc gives - add, remove, recost, reword, or move
	 * an entry to a different bracket, all with no other change needed anywhere else.
	 */
	public static void registerDefaults() {
		ABILITIES.clear();

		// All() - every character sees these, whatever race or lineage they end up on.
		
		register(AbilityDefinition.of("dash_basic", "Dash", 1, "dash", "Dash", "A short, sudden burst of speed in whichever way you're already looking.", AbilityBracket.ALL));
		register(AbilityDefinition.of("deflect_basic", "Deflect", 1, "deflect", "Deflect", "Knock an incoming arrow off course, if you're quick enough.", AbilityBracket.ALL));
		

		// Demonic( ... ) - every demon, whatever lineage.
		register(AbilityDefinition.of("shimmer", "Shimmer", 2, "shimmer", "Shimmer", "Fade out here, fade back in somewhere you've seen.", AbilityBracket.DEMONIC));
		register(AbilityDefinition.of("blink", "Blink", 1, "blink", "Blink", "A short, instant step - there and gone.", AbilityBracket.DEMONIC));
		register(AbilityDefinition.of("flame", "Flame", 1, "flame", "Flame", "Fire that answers to you, not the wind.", AbilityBracket.DEMONIC));
		register(AbilityDefinition.of("screech", "Screech", 2, "screech", "Screech", "A sound that hurts everyone in earshot but you.", AbilityBracket.DEMONIC));
		register(AbilityDefinition.of("vanish", "Vanish", 2, "vanish", "Vanish", "Step out of sight entirely, for a while.", AbilityBracket.DEMONIC));
		register(AbilityDefinition.of("levitate", "Levitate", 1, "levitate", "Levitate", "Hang in the air like it owes you nothing.", AbilityBracket.DEMONIC));
		register(AbilityDefinition.of("anti_gravity", "Anti-Gravity", 1, "anti_gravity", "Anti-Gravity", "Fall slower, jump further - gravity's grip loosens.", AbilityBracket.DEMONIC));
		register(AbilityDefinition.of("flight", "Flight", 4, "flight", "Flight", "Full, sustained flight - the sky is just more ground.", AbilityBracket.DEMONIC));
		register(AbilityDefinition.of("lightning_hands_demonic", "Lightning Hands", 2, "lightning_hands_demonic", "Lightning Hands", "Bare-handed lightning, demon-flavored.", AbilityBracket.DEMONIC));
		register(AbilityDefinition.of("acid_spit", "Acid Spit", 1, "acid_spit", "Acid Spit", "Corrosive, ranged, and impossible to take back once it lands.", AbilityBracket.DEMONIC));

		// Vampiric( ... ) - a Vampire or a Vampiric Queen, on top of everything Demonic.
		// leap/dash/deflect are also what Vampire and Vampiric Queen get GRANTED for free (see
		// RaceCatalog) - registered here too so the "what you get for free" list on screen three has
		// something to show; the granted check is what keeps them off the purchasable list itself.
		register(AbilityDefinition.of("leap", "Leap", 2, "leap", "Leap", "Charge a jump and launch - the longer the hold, the further it carries.", AbilityBracket.VAMPIRIC));
		register(AbilityDefinition.of("dash_vampiric", "Dash", 1, "dash", "Dash", "The same dash, with a bloodline's weight behind it.", AbilityBracket.VAMPIRIC));
		register(AbilityDefinition.of("deflect_vampiric", "Deflect", 1, "deflect", "Deflect", "The same deflect, faster than it has any right to be.", AbilityBracket.VAMPIRIC));

		// VampireQueen( ... ) - the crown itself, nothing else.
		register(AbilityDefinition.of("vampire_mode", "Vampiric", 2, "vampire_mode", "Vampiric", "Hide your vampire side, or let it loose.\nBlend in with your victims, and suppress your powers..\nOr let them breathe..", AbilityBracket.VAMPIRE_QUEEN));

		// Angelic() - Whitelighter and whatever else ends up in this bracket.
		register(AbilityDefinition.of("orb", "Orb", 1, "orb_normal", "Orb", "Become the light you represent, and move at its speed", AbilityBracket.ANGELIC));
	}
}
