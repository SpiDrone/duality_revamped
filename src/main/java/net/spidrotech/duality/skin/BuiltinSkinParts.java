package net.spidrotech.duality.skin;

import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.ModList;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;

import java.util.stream.Stream;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.Writer;

/**
 * Turns the textures you actually shipped into catalog entries, so there is no hand-maintained
 * list of built-in parts anywhere to fall out of sync with the folder.
 *
 * WHERE THE PARTS COME FROM: assets/duality/charcreator/&lt;category&gt;/&lt;name&gt;.png inside the mod's
 * own file. One PNG becomes one part, its id is "&lt;category&gt;_&lt;name&gt;", and its target is looked up
 * from the folder name (see CATEGORY_TARGETS). Adding a hairstyle is therefore dropping a PNG in
 * assets/duality/charcreator/hair/ - no code, no json, no registration.
 *
 * WHY SCAN THE MOD FILE rather than the resource manager: a dedicated server has no assets/
 * resource manager at all, but it does have the mod jar. ModList's file lookup reads straight
 * out of the jar (or, in a dev workspace, out of src/main/resources), so this works identically
 * on both sides and in the IDE.
 *
 * WRITE-ONCE, NEVER OVERWRITE: on every server start this scans for parts that have no json in
 * config/duality/skin_parts/ yet and writes one for each. Files that already exist are left
 * completely alone - which is the point. An admin who edits vampire_eyes.json to make it
 * unlockable, retint it, or split it into subparts keeps those edits forever, while a new PNG
 * you ship in the next update appears automatically on the next restart. Deleting a json makes
 * that part regenerate at defaults, which is the "reset this one" workflow.
 *
 * The generated defaults are deliberately plain: one subpart, not tintable, free. That's the
 * shape you want for a texture that already has its colors painted in. Turn on tinting by
 * editing the json - see SkinPartCatalog's format doc.
 *
 * OWNERSHIP IS NOT DECIDED HERE. Generated files all say "free": true - that's just a harmless
 * placeholder. The real decision is BuiltinPartDefaults.java (a plain Java class, hand-edited,
 * shipped in the jar), with a server's own skin_parts_rules.json able to override it. See
 * SkinOwnershipRules#apply for exactly how those two are combined. Nothing about locking a part
 * involves touching the file this class generates.
 */
public final class BuiltinSkinParts {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String ASSET_ROOT = "assets/duality/charcreator";

	/**
	 * Folder name -> slot. Each of these is an exclusive slot, so a player wears one hairstyle,
	 * one shirt, one pair of shoes. A folder not listed here lands in ACCESSORY, which is
	 * non-exclusive and stacks - so dropping in a "wings" folder gives you stackable wings for
	 * free without touching this map.
	 */
	private static final Map<String, SkinPartTarget> CATEGORY_TARGETS = Map.of("skins", SkinPartTarget.SKIN_TONE, "eyes", SkinPartTarget.EYES, "pupils", SkinPartTarget.PUPIL, "eyebrows", SkinPartTarget.EYEBROWS, "lips", SkinPartTarget.LIPS, "hair", SkinPartTarget.HAIR, "shirts",
			SkinPartTarget.SHIRT, "pants", SkinPartTarget.PANTS, "shoes", SkinPartTarget.SHOES);

	/** The base body everyone starts on - assets/duality/charcreator/skins/skin.png. Referenced
	 *  by the admin command's reset, and a sane default for the builder GUI to preselect. */
	public static final String DEFAULT_BASE_PART = "skins_skin";

	private BuiltinSkinParts() {
	}

	/** Call before SkinPartCatalog#loadFromConfig on server start. */
	public static void generateMissingConfigs(Path configDir) {
		Path outputDir = configDir.resolve("duality").resolve("skin_parts");
		Path assetRoot = locateAssetRoot();
		if (assetRoot == null)
			return;
		int written = 0;
		try {
			Files.createDirectories(outputDir);
			for (String category : listCategories(assetRoot)) {
				SkinPartTarget target = CATEGORY_TARGETS.getOrDefault(category.toLowerCase(Locale.ROOT), SkinPartTarget.ACCESSORY);
				for (Path png : listPngs(assetRoot.resolve(category))) {
					String name = stripExtension(png.getFileName().toString());
					String id = category + "_" + name;
					Path target_ = outputDir.resolve(id + ".json");
					if (Files.exists(target_))
						continue; // admin-owned from here on - see class doc WRITE-ONCE
					writeDefault(target_, id, category, name, target);
					written++;
				}
			}
		} catch (Exception failure) {
			LOGGER.error("[duality] Could not generate default skin part configs", failure);
			return;
		}
		if (written > 0) {
			LOGGER.info("[duality] Generated {} default skin part config(s) in {}", written, outputDir);
		}
	}

	private static void writeDefault(Path file, String id, String category, String name, SkinPartTarget target) throws Exception {
		String texture = "duality:charcreator/" + category + "/" + name + ".png";
		JsonObject subPart = new JsonObject();
		subPart.addProperty("id", "main");
		subPart.addProperty("texture", texture);
		subPart.addProperty("tintable", false);
		subPart.addProperty("default_color", "FFFFFFFF");
		JsonArray subParts = new JsonArray();
		subParts.add(subPart);
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("target", target.name());
		// Generated at "free" purely as a placeholder - SkinOwnershipRules overrides this at
		// load time for any part its patterns match, which is how a server expresses its
		// unlock scheme once instead of once per file. Set "pin_ownership": true here to make
		// a single part ignore the rules and keep whatever this file says.
		json.addProperty("free", true);
		json.addProperty("pin_ownership", false);
		json.addProperty("category", category);
		// Using the part texture itself as the GUI icon - it's a 64x64 skin overlay rather than
		// a proper icon, so it reads as a flat sheet in a grid. Fine for testing; swap these for
		// real icons in the json when you build the picker.
		json.addProperty("icon", texture);
		json.add("subparts", subParts);
		try (Writer writer = Files.newBufferedWriter(file)) {
			GSON.toJson(json, writer);
		}
	}

	private static Path locateAssetRoot() {
		try {
			ModFileInfo info = (ModFileInfo) ModList.get().getModFileById("duality");
			if (info == null) {
				LOGGER.error("[duality] Could not find own mod file - no default skin parts generated");
				return null;
			}
			Path root = info.getFile().findResource(ASSET_ROOT);
			if (!Files.isDirectory(root)) {
				LOGGER.warn("[duality] {} not found in the mod file - no default skin parts generated", ASSET_ROOT);
				return null;
			}
			return root;
		} catch (Exception failure) {
			LOGGER.error("[duality] Could not open {} for scanning", ASSET_ROOT, failure);
			return null;
		}
	}

	private static List<String> listCategories(Path assetRoot) throws Exception {
		try (Stream<Path> stream = Files.list(assetRoot)) {
			List<String> categories = new ArrayList<>();
			for (Path path : stream.filter(Files::isDirectory).toList()) {
				categories.add(path.getFileName().toString().replace("/", ""));
			}
			return categories;
		}
	}

	private static List<Path> listPngs(Path categoryDir) throws Exception {
		try (Stream<Path> stream = Files.list(categoryDir)) {
			return new ArrayList<>(stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).toList());
		}
	}

	private static String stripExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? fileName : fileName.substring(0, dot);
	}
}
