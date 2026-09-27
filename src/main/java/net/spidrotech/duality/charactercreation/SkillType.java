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
	STRENGTH("Strength", "Some say the Strength of man lies in his wit\nI say it lies in their muscles."),
	/** Speed and reflexes. Movement speed. */
	AGILITY("Agility", "Speed and Reaction\nControl the battlefield\nBe the slightly fastest man alive"),
	/** Staying up. Maximum health. */
	ENDURANCE("Endurance", "Not everyone in this world can take a beating\nMost will fail to get up\n\nWill you?"),
	/** The channel powers run through. Mana pool and how fast orbing charges. */
	ATTUNEMENT("Attunement", "A body can only hold so much Energy\nPerhaps yours can hold more than others.."),
	/** How the living react to you. Feeds village standing and NPC disposition. */
	CHARISMA("Charisma", "How people take to you, and whether a village blindly trusts you."),
	/** Noticing things - wards, lies, what a place has been through. */
	INSIGHT("Insight", "How well you see what others might miss\nAnd how fast youre able to discover new things");

	/** Where every skill starts before a single point is spent. */
	public static final int MIN = 1;
	/** Ceiling at character creation - one per chunk of the creator's stat bar art. Growth past this
	 *  is the stat screen's business, not the creator's; see {@link #CEILING}. */
	public static final int MAX = 5;
	/** Ceiling for points spent after creation, from the in-game stat screen. */
	public static final int CEILING = 10;

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
