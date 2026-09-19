package net.spidrotech.duality.charactercreation;

/**
 * The stat line a character is built on - the S.P.E.C.I.A.L.-shaped screen of the creator.
 *
 * <p>Deliberately free of any Minecraft type: this is the definition of what a skill IS, and the
 * question of which attribute it moves lives in {@link CharacterAttributes}. That split is what
 * lets the whole allocation and validation layer be exercised without a server running, and it
 * means retuning what a point is worth touches one file that isn't this one.
 *
 * <p>Every skill starts at {@link #MIN} and a character gets {@link CharacterDraft#STARTING_POINTS}
 * to spend at creation. Points left over aren't lost - they're carried onto the character sheet and
 * spendable later from the in-game stat screen.
 */
public enum SkillType {
	/** Carrying, hitting, and holding a door shut. Attack damage. */
	STRENGTH("Strength", "How hard you hit and how much you can shift."),
	/** Speed and reflexes. Movement speed. */
	AGILITY("Agility", "How fast you move and how quickly you react."),
	/** Staying up. Maximum health. */
	ENDURANCE("Endurance", "How much you can take before you go down."),
	/** The channel powers run through. Mana pool and how fast orbing charges. */
	ATTUNEMENT("Attunement", "How much power you can hold and how fast you can draw on it."),
	/** How the living react to you. Feeds village standing and NPC disposition. */
	PRESENCE("Presence", "How people take to you, and whether a village trusts you."),
	/** Noticing things - wards, lies, what a place has been through. */
	INSIGHT("Insight", "What you notice that others miss."),
	/** Luck, in the vanilla sense and the other one. */
	FORTUNE("Fortune", "Whether things happen to go your way.");

	/** Where every skill starts before a single point is spent. */
	public static final int MIN = 1;
	/** Ceiling at character creation. Growth past this is the stat screen's business, not the
	 *  creator's. */
	public static final int MAX = 10;

	private final String displayName;
	private final String description;

	SkillType(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public String displayName() {
		return displayName;
	}

	/** One line for the screen's hover text. */
	public String description() {
		return description;
	}

	/** Stable id for json and packets. Lower case so it reads well in a file. */
	public String id() {
		return name().toLowerCase();
	}

	public static SkillType parse(String raw) {
		if (raw == null)
			return null;
		for (SkillType skill : values()) {
			if (skill.name().equalsIgnoreCase(raw.trim()) || skill.displayName.equalsIgnoreCase(raw.trim()))
				return skill;
		}
		return null;
	}
}
