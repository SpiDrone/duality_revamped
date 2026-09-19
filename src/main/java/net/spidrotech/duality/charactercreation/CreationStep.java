package net.spidrotech.duality.charactercreation;

/**
 * The five screens, in order, plus the terminal state.
 *
 * <p>The screens are yours to draw; this is the part that says which one the player is allowed to
 * be on and whether the "next" button should be live. {@link #isSatisfiedBy} is the check behind
 * that button, and the same check runs again server-side on commit - a client that skips ahead
 * gets a refused commit rather than a half-built character.
 */
public enum CreationStep {
	/** Screen one. Pick a race. */
	RACE("Choose your race"),
	/** Screen two. Pick a subrace within it. */
	SUBRACE("Choose your lineage"),
	/** Screen three. Pick starting powers out of the pool the race and subrace allow. */
	ABILITIES("Choose your powers"),
	/** Screen four. Spend the point budget across the stat line. */
	SKILLS("Spend your points"),
	/** Screen five. Appearance and name. The skin editor lives here. */
	APPEARANCE("Make your mark"),
	/** Everything is answered and the draft can be committed. */
	READY("Ready");

	private final String title;

	CreationStep(String title) {
		this.title = title;
	}

	/** A default heading for the screen, if you want one. */
	public String title() {
		return title;
	}

	public CreationStep next() {
		return this == READY ? READY : values()[ordinal() + 1];
	}

	public CreationStep previous() {
		return this == RACE ? RACE : values()[ordinal() - 1];
	}

	public boolean isAfter(CreationStep other) {
		return ordinal() > other.ordinal();
	}

	/**
	 * Whether this step's question has been answered.
	 *
	 * <p>Note what isn't required: unspent skill points are fine (they carry over to the stat
	 * screen), and so is picking fewer powers than the pool allows. Only a race, a subrace where
	 * the race has any, and a usable name are actually mandatory.
	 */
	public boolean isSatisfiedBy(CharacterDraft draft, RaceDefinition race, SubraceDefinition subrace) {
		return switch (this) {
			case RACE -> race != null;
			case SUBRACE -> race != null && (race.subraces().isEmpty() || subrace != null);
			case ABILITIES -> race != null && draft.abilityIds().size() <= draft.abilityPickLimit(race, subrace);
			case SKILLS -> draft.pointsSpent() <= CharacterDraft.STARTING_POINTS;
			case APPEARANCE -> CharacterDraft.isNameUsable(draft.name());
			case READY -> true;
		};
	}
}
