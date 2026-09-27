package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.skin.SkinPartTarget;
import net.spidrotech.duality.skin.SkinPartCatalog;
import net.spidrotech.duality.skin.SkinPart;
import net.spidrotech.duality.skin.SkinLoadout;

import java.util.ArrayList;
import java.util.List;

/**
 * What a new character has to be wearing before it can be confirmed: a skin, eyes and pants.
 * Lips, hair, eyebrows and a shirt are the player's call.
 *
 * <p>One rule used by both sides - the creator's confirm button (client, against the synced
 * loadout) and CharacterCreation#commit (server, against the real one) - so the button is never
 * live for something the server would refuse.
 */
public final class AppearanceRequirements {
	/** A slot that must be filled, and how to name it to the player. */
	public record Required(SkinPartTarget target, String category, String label) {
	}

	public static final List<Required> REQUIRED = List.of(new Required(SkinPartTarget.SKIN_TONE, "skins", "a skin"), new Required(SkinPartTarget.EYES, "eyes", "eyes"),
			new Required(SkinPartTarget.PANTS, "pants", "pants"));

	private AppearanceRequirements() {
	}

	/** Whether the creator should leave out the "None" option for this category. */
	public static boolean isRequiredCategory(String category) {
		for (Required required : REQUIRED) {
			if (required.category().equals(category))
				return true;
		}
		return false;
	}

	/** The labels of every required slot this loadout leaves empty, in REQUIRED order. */
	public static List<String> missing(SkinLoadout loadout) {
		List<String> missing = new ArrayList<>();
		for (Required required : REQUIRED) {
			if (!wears(loadout, required.target()))
				missing.add(required.label());
		}
		return missing;
	}

	private static boolean wears(SkinLoadout loadout, SkinPartTarget target) {
		for (SkinLoadout.Equipped equipped : loadout.parts()) {
			SkinPart part = SkinPartCatalog.get(equipped.partId());
			if (part != null && part.target() == target)
				return true;
		}
		return false;
	}
}
