package net.spidrotech.duality.procedures;

import net.spidrotech.duality.charactercreation.client.CharacterCodex;

/**
 * The codex panel's body text in the character creator - the selected race's blurb.
 *
 * !! MCREATOR WILL OVERWRITE THIS FILE unless the CodexReturnDescription element's code is locked.
 * As with the heading, the text lives in RaceCatalog and the lookup in CharacterCodex, so only this
 * forwarding call is at risk.
 *
 * Replaces the hardcoded human paragraph this used to return: it now follows whichever race is
 * actually selected, so every race in the catalog gets its own description with no work here. Note
 * the old text's "/n" markers were never going to break lines - they were forward slashes, and
 * UIGraphicsHelper has no newline handling regardless; it wraps on the split character the screen
 * hands it (a space), inside the bounding box.
 */
public class CodexReturnDescriptionProcedure {
	public static String execute() {
		return CharacterCodex.description();
	}
}
