package net.spidrotech.duality.charactercreation;

/**
 * Everything a screen can ask the server to do, as one enum.
 *
 * <p>One action packet rather than five keeps the wire small and means adding a screen doesn't mean
 * adding a payload type. Each constant documents which of its arguments it reads; the rest are
 * ignored.
 */
public enum CreationAction {
	/** Screen one. arg = race id. Resets the lineage, powers and points below it. */
	SELECT_RACE,
	/** Screen two. arg = subrace id. */
	SELECT_SUBRACE,
	/** Screen three. arg = ability id. Adds it, or removes it if already taken. */
	TOGGLE_ABILITY,
	/** Screen four. arg = skill name, amount = points to spend (negative refunds). */
	ALLOCATE_SKILL,
	/** Screen four. Hands every spent point back. */
	RESET_SKILLS,
	/** Screen five. arg = the name. */
	SET_NAME,
	/** Move the player's place in the flow. arg = step name; blank means "the next one". */
	GOTO_STEP,
	/** Finish: build the character, link it to the player, and apply what they chose. */
	COMMIT,
	/** Throw the draft away and start over at screen one. */
	RESTART;

	public static CreationAction parse(String raw) {
		if (raw == null)
			return null;
		for (CreationAction action : values()) {
			if (action.name().equalsIgnoreCase(raw.trim()))
				return action;
		}
		return null;
	}
}
