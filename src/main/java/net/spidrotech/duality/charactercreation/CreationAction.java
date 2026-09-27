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
	/** The "back" button: undoes whatever the current step answered (the lineage, the powers, the
	 *  spent points - whichever belongs to the step being left) and steps back one, rather than just
	 *  moving the cursor and leaving a stale answer behind it. A no-op on screen one. */
	GO_BACK,
	/** Finish: build the character, link it to the player, and apply what they chose. */
	COMMIT,
	/** Throw the draft away and start over at screen one. */
	RESTART,
	/** Sent by the client whenever the creator is off screen mid-creation (closed, or back from the
	 *  pause menu) - puts the current step's screen back up. See CharacterCreatorNavigation#reopen. */
	REOPEN,
	/** Page five's "show me as a vampire" preview, on or off. Only does anything while the draft is a
	 *  vampire lineage; the look comes off by itself if it stops being one. */
	TOGGLE_VAMPIRE_PREVIEW;

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
