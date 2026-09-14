package net.spidrotech.duality.skin;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.stream.Stream;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.Reader;

/**
 * Every part that exists, by id. Populated from plain JSON files on the SERVER
 * (config/duality/skin_parts/*.json) and pushed to clients on join, rather than being read from
 * assets client-side.
 *
 * WHY SERVER-OWNED DEFINITIONS, RESOURCEPACK-OWNED TEXTURES: free()/unlock rules have to be
 * server-authoritative or every client can grant itself every part, so the definition file lives
 * with the server. The actual PNGs are just ResourceLocations that a companion resourcepack
 * provides. Adding a part is therefore two drops: the json into the server's config folder, the
 * textures into a resourcepack every player has. A client missing the texture for a part logs
 * once and skips that layer rather than failing the whole composite - see SkinCompositor.
 *
 * SCALE: this is built to hold thousands of entries. Nothing here loads a texture; the catalog
 * is pure metadata (a few hundred bytes per part), and image data is loaded lazily and evicted
 * by the client cache. The sync payload for ~2000 parts lands around 250KB.
 * TODO: if you go far past that, chunk SyncSkinCatalogPayload across several packets - a single
 * payload over ~1MB will trip the vanilla packet size limit.
 *
 * FILE FORMAT (config/duality/skin_parts/vampire_eyes.json):
 * {
 *   "id": "vampire_eyes",
 *   "target": "EYES",
 *   "free": true,
 *   "pin_ownership": false,
 *   "category": "eyes",
 *   "icon": "duality:textures/gui/skin/vampire_eyes_icon.png",
 *   "subparts": [
 *     { "id": "sclera", "texture": "duality:textures/skin/eyes/vampire_sclera.png" },
 *     { "id": "pupil",  "texture": "duality:textures/skin/eyes/vampire_pupil.png",
 *       "tintable": true, "default_color": "FFCC0000" }
 *   ]
 * }
 */
public final class SkinPartCatalog {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Map<String, SkinPart> PARTS = new LinkedHashMap<>();

	private SkinPartCatalog() {
	}

	public static SkinPart get(String id) {
		return PARTS.get(id);
	}

	public static boolean has(String id) {
		return PARTS.containsKey(id);
	}

	public static Collection<SkinPart> all() {
		return PARTS.values();
	}

	/** For the builder GUI's tab strip - parts grouped by their declared category, insertion
	 *  ordered so the on-disk file order is the order players see. */
	public static Map<String, List<SkinPart>> byCategory() {
		Map<String, List<SkinPart>> out = new LinkedHashMap<>();
		for (SkinPart part : PARTS.values()) {
			out.computeIfAbsent(part.category(), k -> new ArrayList<>()).add(part);
		}
		return out;
	}

	/** Wholesale replace - used by the server after a config load and by the client on receiving
	 *  SyncSkinCatalogPayload. Both sides then hold an identical catalog. */
	public static void replaceAll(Collection<SkinPart> parts) {
		PARTS.clear();
		for (SkinPart part : parts) {
			PARTS.put(part.id(), part);
		}
		LOGGER.info("[duality] Skin catalog now holds {} part(s)", PARTS.size());
	}

	/** Server-side load. Call from ServerAboutToStartEvent (and again from a /reload hook if you
	 *  want live editing). Directory is created if absent so a fresh server shows admins where
	 *  parts go. */
	public static void loadFromConfig(Path configDir) {
		Path dir = configDir.resolve("duality").resolve("skin_parts");
		List<SkinPart> loaded = new ArrayList<>();
		try {
			Files.createDirectories(dir);
			try (Stream<Path> files = Files.list(dir)) {
				for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
					try (Reader reader = Files.newBufferedReader(file)) {
						JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
						// Ownership is resolved by pattern, not per file - see
						// SkinOwnershipRules. The json's own free/unlock values are only
						// consulted when no rule matches, or when the part opts out.
						boolean pinned = json.has("pin_ownership") && json.get("pin_ownership").getAsBoolean();
						SkinPart part = SkinOwnershipRules.apply(parse(json), pinned);
						if (part != null)
							loaded.add(part);
					} catch (Exception badFile) {
						// One malformed part must never take the whole catalog down with it -
						// an admin's typo should cost them that one part, not everyone's skins.
						LOGGER.error("[duality] Skipping unreadable skin part file {}", file, badFile);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("[duality] Could not read skin part directory {}", dir, e);
		}
		replaceAll(loaded);
	}

	private static SkinPart parse(JsonObject json) {
		String id = json.get("id").getAsString();
		SkinPartTarget target = SkinPartTarget.valueOf(json.get("target").getAsString().toUpperCase());
		boolean free = !json.has("free") || json.get("free").getAsBoolean();
		java.util.Optional<String> unlock = json.has("unlock") ? java.util.Optional.of(json.get("unlock").getAsString()) : java.util.Optional.empty();
		String category = json.has("category") ? json.get("category").getAsString() : target.name().toLowerCase();
		ResourceLocation icon = ResourceLocation.parse(json.has("icon") ? json.get("icon").getAsString() : "duality:textures/gui/skin/missing_icon.png");
		List<SkinPart.SubPart> subs = new ArrayList<>();
		JsonArray array = json.getAsJsonArray("subparts");
		for (JsonElement element : array) {
			JsonObject sub = element.getAsJsonObject();
			subs.add(new SkinPart.SubPart(sub.get("id").getAsString(), ResourceLocation.parse(sub.get("texture").getAsString()), sub.has("tintable") && sub.get("tintable").getAsBoolean(),
					sub.has("default_color") ? (int) Long.parseLong(sub.get("default_color").getAsString(), 16) : 0xFFFFFFFF));
		}
		return new SkinPart(id, target, free, unlock, category, icon, List.copyOf(subs));
	}
}
