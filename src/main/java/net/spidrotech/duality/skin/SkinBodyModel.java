package net.spidrotech.duality.skin;

/**
 * Which player body a character wears - classic "thick" Steve proportions (4px arms) or "slim"
 * Alex ones (3px arms).
 *
 * This is deliberately the mod's own enum rather than vanilla's PlayerSkin.Model: that type is
 * client-only, and this value rides inside SkinLoadout, which is common code the SERVER reads,
 * writes and persists. The two are mapped to each other exactly once, on the client, where
 * touching PlayerSkin is legal - see client.ClientSkinCache#bodyModelFor.
 *
 * WIDE IS ALWAYS THE DEFAULT: a character sheet saved before this field existed, a brand new
 * character, an unparseable hand-edited value, and a null all read as WIDE. That is the whole
 * point of storing this per character - the body is a property OF THE CHARACTER, so it must never
 * silently inherit whichever model the player's Mojang account happens to have. A player on a slim
 * account still starts every character as Steve until that character says otherwise.
 */
public enum SkinBodyModel {
	/** Steve proportions. The default for every character. */
	WIDE("wide"),
	/** Alex proportions. */
	SLIM("slim");

	public static final SkinBodyModel DEFAULT = WIDE;

	private final String id;

	SkinBodyModel(String id) {
		this.id = id;
	}

	/** Stable id for json and commands - see SkinLoadoutCodec's stored shape. */
	public String id() {
		return id;
	}

	/** Absent or unrecognised reads as DEFAULT rather than throwing: a character sheet is a file
	 *  someone can hand-edit, and a typo in it shouldn't be able to break loading the character. */
	public static SkinBodyModel parse(String raw) {
		if (raw != null) {
			for (SkinBodyModel model : values()) {
				if (model.id.equalsIgnoreCase(raw.trim()) || model.name().equalsIgnoreCase(raw.trim()))
					return model;
			}
		}
		return DEFAULT;
	}
}
