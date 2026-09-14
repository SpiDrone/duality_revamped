package net.spidrotech.duality.skin;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.regex.Pattern;
import java.util.Optional;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.Reader;

/**
 * Decides whether a part is free or locked, WITHOUT anybody editing per-part files.
 *
 * THE PROBLEM THIS SOLVES: BuiltinSkinParts generates one json per texture, so a mod shipping a
 * few hundred cosmetics generates a few hundred files. Setting "free": false by hand in the ones
 * that should be earned is unworkable - it's a fresh afternoon of editing on every server, and
 * it silently resets the moment you ship new textures, because a newly generated file always
 * lands at defaults. Ownership needs to be expressed once, in patterns, and applied to whatever
 * exists.
 *
 * HOW IT WORKS: config/duality/skin_parts_rules.json holds an ordered list of rules, giving a
 * SERVER OWNER a way to override BuiltinPartDefaults (the mod author's shipped decisions - see
 * that class) without editing any code. The full precedence, checked in this order:
 *   1. A part json marked "pin_ownership": true - wins outright, skips everything below.
 *   2. A matching rule in THIS file - the server owner's override.
 *   3. A matching entry in BuiltinPartDefaults - the mod author's shipped decision.
 *   4. The part's own generated json "free" value - always true, last resort.
 * Each rule can match on id glob, category, and/or target; a rule with several of those set
 * matches only if ALL of them do. The FIRST matching rule wins within each of steps 2 and 3
 * independently.
 *
 * That ordering is the useful part: a server that's happy with the mod author's defaults ships
 * an empty rules file and step 3 quietly does all the work. A server that wants something
 * different adds one rule here and it wins over step 3 without touching a single java file.
 *
 *   { "match": "hair_crown_*",  "free": false, "unlock": "duality:royalty" }
 *   { "category": "skins",      "free": true }
 *   { "match": "*",             "free": true }
 *
 * UNLOCK vs FREE: free means everyone has it and no bookkeeping happens. Otherwise the part
 * names an unlock id, and a player owns it if they've been granted that id (see SkinUnlocks).
 * Unlock ids are deliberately shared rather than per-part - granting "duality:vampire_line"
 * once hands over every part tagged with it, so a progression step is one grant instead of
 * forty.
 *
 * ESCAPE HATCH: a part json with "pin_ownership": true is exempt from all rules, for the
 * handful of cases where a single part genuinely needs its own answer. Use it sparingly; if
 * you're pinning a lot of parts, the rules list wants restructuring instead.
 */
public final class SkinOwnershipRules {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String FILE_NAME = "skin_parts_rules.json";
	private static final List<Rule> RULES = new ArrayList<>();

	/** All three matchers are optional; an omitted one matches everything. A rule with none set
	 *  therefore matches every part, which is exactly what you want for a trailing catch-all. */
	private record Rule(Optional<Pattern> idPattern, Optional<String> category, Optional<SkinPartTarget> target, boolean free, Optional<String> unlock) {
		boolean matches(SkinPart part) {
			if (idPattern.isPresent() && !idPattern.get().matcher(part.id()).matches())
				return false;
			if (category.isPresent() && !category.get().equalsIgnoreCase(part.category()))
				return false;
			return target.isEmpty() || target.get() == part.target();
		}
	}

	private SkinOwnershipRules() {
	}

	/** Returns the part with ownership resolved. Pinned parts come back untouched. A matching
	 *  server rule wins outright; otherwise falls through to BuiltinPartDefaults (the mod
	 *  author's shipped decision); otherwise the part's own json value stands. */
	public static SkinPart apply(SkinPart part, boolean pinned) {
		if (pinned)
			return part;
		for (Rule rule : RULES) {
			if (!rule.matches(part))
				continue;
			return new SkinPart(part.id(), part.target(), rule.free(), rule.unlock(), part.category(), part.icon(), part.subParts());
		}
		return BuiltinPartDefaults.resolve(part).orElse(part);
	}

	/** Load before generating or loading parts - see SkinServerSetup for the ordering. Writes a
	 *  commented starter file the first time so admins have something to edit rather than a
	 *  blank page. */
	public static void load(Path configDir) {
		Path file = configDir.resolve("duality").resolve(FILE_NAME);
		RULES.clear();
		try {
			Files.createDirectories(file.getParent());
			if (!Files.exists(file)) {
				Files.writeString(file, STARTER_FILE);
				LOGGER.info("[duality] Wrote starter skin ownership rules to {}", file);
			}
			try (Reader reader = Files.newBufferedReader(file)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				JsonArray array = root.has("rules") ? root.getAsJsonArray("rules") : new JsonArray();
				for (JsonElement element : array) {
					RULES.add(parseRule(element.getAsJsonObject()));
				}
			}
		} catch (Exception failure) {
			// An unparseable rules file must not lock everyone out of every cosmetic, nor hand
			// everything out for free by accident. Empty rules means "every part's own json
			// stands," which is the least surprising of the three.
			LOGGER.error("[duality] Could not read {} - falling back to each part's own ownership setting", file, failure);
			RULES.clear();
			return;
		}
		LOGGER.info("[duality] Loaded {} skin ownership rule(s)", RULES.size());
	}

	private static Rule parseRule(JsonObject json) {
		Optional<Pattern> idPattern = json.has("match") ? Optional.of(globToPattern(json.get("match").getAsString())) : Optional.empty();
		Optional<String> category = json.has("category") ? Optional.of(json.get("category").getAsString()) : Optional.empty();
		Optional<SkinPartTarget> target = json.has("target") ? Optional.of(SkinPartTarget.valueOf(json.get("target").getAsString().toUpperCase(Locale.ROOT))) : Optional.empty();
		boolean free = !json.has("free") || json.get("free").getAsBoolean();
		Optional<String> unlock = json.has("unlock") ? Optional.of(json.get("unlock").getAsString()) : Optional.empty();
		return new Rule(idPattern, category, target, free, unlock);
	}

	/** Glob, not regex, on purpose - "hair_crown_*" is something a server owner can write
	 *  correctly on the first try, and part ids never contain regex metacharacters anyway.
	 *  Everything outside * and ? is quoted literally. */
	/** Package-visible so BuiltinPartDefaults can use the identical matching syntax - one glob
	 *  dialect for both the shipped class and the server's override file, so a pattern that
	 *  works in one works in the other with no translation. */
	static Pattern globToPattern(String glob) {
		StringBuilder regex = new StringBuilder();
		StringBuilder literal = new StringBuilder();
		for (char c : glob.toCharArray()) {
			if (c == '*' || c == '?') {
				if (!literal.isEmpty()) {
					regex.append(Pattern.quote(literal.toString()));
					literal.setLength(0);
				}
				regex.append(c == '*' ? ".*" : ".");
			} else {
				literal.append(c);
			}
		}
		if (!literal.isEmpty()) {
			regex.append(Pattern.quote(literal.toString()));
		}
		return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
	}

	private static final String STARTER_FILE = """
			{
			  "_comment": [
			    "Server-owner OVERRIDES of the mod's own default ownership (see BuiltinPartDefaults.java",
			    "in the mod's source - that class is the mod author's shipped decision on what's locked).",
			    "This file only needs entries for parts you want to change FROM that shipped default.",
			    "An empty rules list here means: trust the mod's own defaults completely.",
			    "",
			    "FIRST match wins. Each rule may set: match (id glob), category, target - all present ones",
			    "must match. free=true means everyone owns it. free=false requires the named unlock id",
			    "(grant with /dualityadmin skineditor unlock grant <player> <unlockId>).",
			    "A part json with \\"pin_ownership\\": true ignores this file AND the mod's defaults."
			  ],
			  "rules": []
			}
			""";
}
