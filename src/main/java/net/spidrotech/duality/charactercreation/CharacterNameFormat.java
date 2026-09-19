package net.spidrotech.duality.charactercreation;

/**
 * How a character's name and the account behind it get combined into one label.
 *
 * <p>Pure string work on purpose: what a name looks like is a decision worth being able to change
 * and test without a client running, and it's the same decision in three places (the tag over a
 * head, chat, the tab list) with possibly different answers in each.
 *
 * <p>It also sanitises. A name typed into the creator has already been checked, but a character
 * sheet is a json file somebody can edit, and this string ends up in every other player's chat and
 * over their world - so section signs are stripped here rather than trusted upstream.
 */
public final class CharacterNameFormat {
	/** Formatting-code marker. Never allowed through, wherever the name came from. */
	private static final char SECTION = '§';
	/** Longest label this will produce, so a silly name can't push a tab list around. */
	public static final int MAX_LABEL = 48;

	/** Which name wins, and whether the other one is shown in brackets after it. */
	public enum Style {
		/** "Mera Holt" - the character and nothing else. */
		CHARACTER_ONLY,
		/** "Mera Holt (Steve)" - in character, but you can still tell who you're talking to. */
		CHARACTER_THEN_ACCOUNT,
		/** "Steve (Mera Holt)" - account first, for moderation-facing places. */
		ACCOUNT_THEN_CHARACTER,
		/** Vanilla. Useful for turning a surface off without deleting the wiring. */
		ACCOUNT_ONLY
	}

	private CharacterNameFormat() {
	}

	/**
	 * Builds the label.
	 *
	 * <p>A player with no character - mid-creation, or freshly dead - falls back to their account
	 * name in every style. There's nothing else honest to show, and showing an empty name tag would
	 * be worse than showing the wrong kind of name.
	 */
	public static String format(Style style, String characterName, String accountName) {
		String account = clean(accountName);
		String character = clean(characterName);
		if (character.isEmpty())
			return account;
		if (account.isEmpty())
			return character;
		String label = switch (style == null ? Style.CHARACTER_ONLY : style) {
			case CHARACTER_ONLY -> character;
			case CHARACTER_THEN_ACCOUNT -> character + " (" + account + ")";
			case ACCOUNT_THEN_CHARACTER -> account + " (" + character + ")";
			case ACCOUNT_ONLY -> account;
		};
		return label.length() <= MAX_LABEL ? label : label.substring(0, MAX_LABEL);
	}

	/** Strips formatting codes and control characters, and trims. */
	public static String clean(String raw) {
		if (raw == null)
			return "";
		StringBuilder out = new StringBuilder(raw.length());
		for (char c : raw.toCharArray()) {
			if (c == SECTION || Character.isISOControl(c))
				continue;
			out.append(c);
		}
		return out.toString().trim();
	}
}
