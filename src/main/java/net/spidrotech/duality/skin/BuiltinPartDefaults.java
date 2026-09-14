package net.spidrotech.duality.skin;

import java.util.regex.Pattern;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

/**
 * THE MOD AUTHOR'S DEFAULT ANSWER for which built-in parts are free and which are locked. This
 * is a Java class, shipped inside the mod jar, not a generated file - it exists so the decision
 * "this cosmetic is earned, not given" lives in source control next to the parts it governs,
 * and so it survives forever regardless of what any server's config directory contains.
 *
 * EDIT register() BELOW to change what's locked. That's the entire workflow: add a texture to
 * assets/duality/charcreator/, then (if it shouldn't be free) add one line here naming it or
 * its category. Nothing else needs touching - BuiltinSkinParts still auto-generates the part's
 * json at "free": true, and this class corrects that at load time, the same way
 * SkinOwnershipRules corrects it from a server's own file.
 *
 * PRECEDENCE (see SkinOwnershipRules#apply, which is what actually calls this):
 *   1. A part json marked "pin_ownership": true - always wins, ignores everything below.
 *   2. The SERVER's config/duality/skin_parts_rules.json - a server owner's override of your
 *      decision. If it has a matching rule, that rule wins.
 *   3. THIS CLASS - your shipped decision. Applies whenever the server hasn't overridden it.
 *   4. The generated part json's own "free" value - always true, the fallback of last resort.
 *
 * That order is what makes "a player downloads the mod and gets what I said should be free, and
 * unlocks what I said isn't, unless their server owner deliberately changed it" true without
 * either side's file needing to know the other exists.
 *
 * MATCHING is identical to the server rules file - id glob, category, and/or target, ANDed
 * together, first match in registration order wins. See SkinOwnershipRules' own doc for the
 * glob syntax; it's the same Pattern compiler.
 */
public final class BuiltinPartDefaults {
	private static final List<Entry> ENTRIES = new ArrayList<>();

	private record Entry(Optional<Pattern> idPattern, Optional<String> category, Optional<SkinPartTarget> target, boolean free, Optional<String> unlockId) {
		boolean matches(SkinPart part) {
			if (idPattern.isPresent() && !idPattern.get().matcher(part.id()).matches())
				return false;
			if (category.isPresent() && !category.get().equalsIgnoreCase(part.category()))
				return false;
			return target.isEmpty() || target.get() == part.target();
		}
	}

	private BuiltinPartDefaults() {
	}

	static {
		register();
	}

	/**
	 * ============================================================================
	 *  EDIT THIS METHOD. This is the one place in the whole mod that decides which
	 *  built-in parts are locked behind an unlock and which are free out of the box.
	 * ============================================================================
	 *
	 * Earlier calls win over later ones for anything they both match - put specific
	 * exceptions before broader rules, same as a firewall or .gitignore.
	 *
	 * Examples:
	 *   lock("hair_crown_gold", "duality:royalty");        one specific part
	 *   lock("hair_crown_*", "duality:royalty");            every part matching a glob
	 *   lockCategory("shirts", "duality:wardrobe");         a whole charcreator folder
	 *   lockTarget(SkinPartTarget.SHOES, "duality:kicks");  a whole slot, any category
	 *   free("shirts_basic_white");                         carve out an exception above a lock
	 *
	 * Nothing you don't mention here is affected - it falls through to whatever the server rules
	 * file or the part's own generated json says (which defaults to free).
	 */
	private static void register() {
		// ---- example entries - replace these with your actual defaults ----
		// lock("hair_crown_gold", "duality:royalty");
		// lockCategory("shirts", "duality:wardrobe");
		// free("shirts_basic_white");
	}

	// ================================================================== registration helpers
	public static void lock(String idGlob, String unlockId) {
		ENTRIES.add(new Entry(Optional.of(SkinOwnershipRules.globToPattern(idGlob)), Optional.empty(), Optional.empty(), false, Optional.of(unlockId)));
	}

	public static void lockCategory(String category, String unlockId) {
		ENTRIES.add(new Entry(Optional.empty(), Optional.of(category), Optional.empty(), false, Optional.of(unlockId)));
	}

	public static void lockTarget(SkinPartTarget target, String unlockId) {
		ENTRIES.add(new Entry(Optional.empty(), Optional.empty(), Optional.of(target), false, Optional.of(unlockId)));
	}

	/** For carving a free exception out of an otherwise-locked category/target/glob - put it
	 *  BEFORE the broader lock() call it's meant to override, since first match wins. */
	public static void free(String idGlob) {
		ENTRIES.add(new Entry(Optional.of(SkinOwnershipRules.globToPattern(idGlob)), Optional.empty(), Optional.empty(), true, Optional.empty()));
	}

	// ================================================================== lookup
	/** Called by SkinOwnershipRules once no server rule has matched. Empty means "this class has
	 *  no opinion, fall through to the part's own generated default." */
	public static Optional<SkinPart> resolve(SkinPart part) {
		for (Entry entry : ENTRIES) {
			if (entry.matches(part)) {
				return Optional.of(new SkinPart(part.id(), part.target(), entry.free(), entry.unlockId(), part.category(), part.icon(), part.subParts()));
			}
		}
		return Optional.empty();
	}
}
