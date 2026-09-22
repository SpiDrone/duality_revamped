package net.spidrotech.duality.charactercreation.client;

import net.spidrotech.duality.charactercreation.RaceDefinition;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

/**
 * Text for the creator's codex panel - the race's name and blurb, for a screen to draw.
 *
 * READS THE DRAFT, NOT THE BUTTON HIGHLIGHT. Both are available on the client, and it matters which
 * one this uses: UIButtonElement.getSelected tells you which row is lit up, which is only ever the
 * client's own guess and stays lit even when the server REFUSED the pick (a race the player hasn't
 * unlocked, say). ClientCharacterCreation holds the DraftView the server sends back after every
 * action, so reading that means the codex describes the race the player actually has, and goes blank
 * again if a pick was rejected - the panel can't disagree with the character being built.
 *
 * Every string comes from RaceCatalog, so adding a race to that catalog gives it a codex entry for
 * free - there is deliberately no per-race text here to keep in sync. To change what a race's panel
 * says, edit its displayName/description in RaceCatalog, which is already the file meant for that.
 *
 * Empty string, never null and never the word "null", is the "nothing picked yet" answer - it's what
 * a text helper can draw harmlessly.
 */
@OnlyIn(Dist.CLIENT)
public final class CharacterCodex {
	private CharacterCodex() {
	}

	/** The selected race's display name ("Human", "Whitelighter"), or "" if none is picked. Note this
	 *  is the presentable name, not the internal id the button carries ("human"). */
	public static String title() {
		RaceDefinition race = ClientCharacterCreation.race();
		return race == null ? "" : race.displayName();
	}

	/** The selected race's blurb, or "" if none is picked. Returned as one run of prose on purpose:
	 *  UIGraphicsHelper's bound-text drawing wraps on the split character the screen passes it and has
	 *  no newline handling at all, so line breaks belong to the layout, not to this string. */
	public static String description() {
		RaceDefinition race = ClientCharacterCreation.race();
		return race == null ? "" : race.description();
	}
}
