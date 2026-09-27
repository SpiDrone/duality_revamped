package net.spidrotech.duality.charactercreation;

/**
 * One purchasable power on screen three.
 *
 * @param id              stable registry key ("vampiric_leap") - what {@link CharacterDraft} tracks
 *                         as chosen and what a screen sends back on a click. Never shown to a player.
 * @param displayName     the short label a scroll-list row shows ("Leap").
 * @param pointCost       what buying this costs, out of the same pool spending a skill point does -
 *                         see {@link CharacterDraft#pointsRemaining}. 0 is a legal cost for something
 *                         you'd still rather a player chose than had forced on them.
 * @param abilityId       the actual power id this grants the character (an EquippedAbilities/
 *                         UNLOCKED_ABILITIES entry, e.g. "leap") - deliberately separate from
 *                         {@link #id} so two catalog entries can offer the same underlying power
 *                         under different names, costs or brackets without colliding.
 * @param codexTitle      heading the codex panel shows once this row is clicked.
 * @param codexDescription blurb the codex panel shows once this row is clicked.
 * @param bracket         which {@link AbilityBracket} this belongs to - who can even see it, before
 *                         cost is a factor. See that enum's class doc for the tree.
 */
public record AbilityDefinition(String id, String displayName, int pointCost, String abilityId, String codexTitle, String codexDescription, AbilityBracket bracket) {

	public AbilityDefinition {
		pointCost = Math.max(0, pointCost);
	}

	public static AbilityDefinition of(String id, String displayName, int pointCost, String abilityId, String codexTitle, String codexDescription, AbilityBracket bracket) {
		return new AbilityDefinition(id, displayName, pointCost, abilityId, codexTitle, codexDescription, bracket);
	}
}
