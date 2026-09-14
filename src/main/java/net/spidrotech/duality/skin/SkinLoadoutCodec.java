package net.spidrotech.duality.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

/**
 * Converts a SkinLoadout to and from the JSON shape stored under a character sheet's
 * "char_creator" object (see DualityDatabaseManager). Kept out of SkinLoadout itself because
 * that record is common code shared with the client, and this JSON shape is a server-side
 * storage detail - the client only ever sees loadouts via the StreamCodec, never this.
 *
 * STORED SHAPE (inside a character sheet):
 *   "char_creator": {
 *     "base": "skins_skin",              // omitted/absent = use the player's real Mojang skin
 *     "parts": [
 *       { "id": "hair_ponytail", "tints": [] },
 *       { "id": "eyes_blue", "tints": [-1] }    // -1 = that subpart's catalog default
 *     ],
 *     "unlocks": [ "duality:royalty" ]   // read by SkinUnlocks, written here for locality
 *   }
 *
 * Unknown part ids are kept on read, not dropped - a catalog reload can temporarily retire a
 * part underneath a saved character, and silently deleting it from their sheet would lose the
 * choice permanently. SkinLoadout#sortedForRender already skips ids the catalog doesn't know,
 * so a stale id sits harmlessly in the file until the part comes back.
 */
public final class SkinLoadoutCodec {
	public static final String SECTION = "char_creator";
	private static final String KEY_BASE = "base";
	private static final String KEY_PARTS = "parts";
	private static final String KEY_TINTS = "tints";
	private static final String KEY_ID = "id";

	private SkinLoadoutCodec() {
	}

	public static SkinLoadout fromSection(JsonObject charCreator) {
		if (charCreator == null)
			return SkinLoadout.EMPTY;
		Optional<String> base = charCreator.has(KEY_BASE) && !charCreator.get(KEY_BASE).getAsString().isEmpty() ? Optional.of(charCreator.get(KEY_BASE).getAsString()) : Optional.empty();
		List<SkinLoadout.Equipped> parts = new ArrayList<>();
		if (charCreator.has(KEY_PARTS)) {
			for (JsonElement element : charCreator.getAsJsonArray(KEY_PARTS)) {
				JsonObject entry = element.getAsJsonObject();
				List<Integer> tints = new ArrayList<>();
				if (entry.has(KEY_TINTS)) {
					for (JsonElement tint : entry.getAsJsonArray(KEY_TINTS)) {
						tints.add(tint.getAsInt());
					}
				}
				parts.add(new SkinLoadout.Equipped(entry.get(KEY_ID).getAsString(), List.copyOf(tints)));
			}
		}
		return new SkinLoadout(base, List.copyOf(parts));
	}

	/** Writes the loadout's own fields into the given section object, leaving anything else
	 *  already in it (e.g. the "unlocks" array SkinUnlocks manages) untouched. */
	public static void writeInto(JsonObject charCreator, SkinLoadout loadout) {
		charCreator.addProperty(KEY_BASE, loadout.baseTexturePartId().orElse(""));
		JsonArray parts = new JsonArray();
		for (SkinLoadout.Equipped equipped : loadout.parts()) {
			JsonObject entry = new JsonObject();
			entry.addProperty(KEY_ID, equipped.partId());
			JsonArray tints = new JsonArray();
			for (int tint : equipped.tints()) {
				tints.add(tint);
			}
			entry.add(KEY_TINTS, tints);
			parts.add(entry);
		}
		charCreator.add(KEY_PARTS, parts);
	}
}
