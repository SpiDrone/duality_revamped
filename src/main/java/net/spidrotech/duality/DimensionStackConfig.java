package net.spidrotech.duality;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;

import net.neoforged.fml.loading.FMLPaths; // TODO: verify this import path against your NeoForge version

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * How dimensions "stack" vertically, for a teleport picker's destination direction (and anywhere
 * else that wants a "is this place above or below me" answer) - NOT physical Y coordinate, since
 * unrelated dimensions have unrelated coordinate spaces. A HIGHER tier always reads as "up" from
 * anywhere in a LOWER tier, and vice versa; EQUAL tiers (Overworld/Nether/End all sitting at the
 * same "mortal plane" tier here) compare as level - direction between them is purely horizontal.
 *
 * A tier of DIMENSION_EXCLUDED (-1) means teleporting into OR out of that dimension is blocked
 * entirely, regardless of level - OrbAbility#canCross will need updating to consult this (it
 * currently only knows Overworld/Heaven/Underworld by name).
 *
 * Backed by a hand-editable JSON file at config/duality/dimension_stack.json, auto-created with
 * your listed defaults the first time this is read if the file doesn't exist yet.
 */
public final class DimensionStackConfig {
	public static final int DIMENSION_EXCLUDED = -1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Integer> DEFAULTS = new LinkedHashMap<>();
	static {
		DEFAULTS.put("duality:upper_regions", 5); // heaven
		DEFAULTS.put("minecraft:overworld", 3);
		DEFAULTS.put("minecraft:the_end", 3);
		DEFAULTS.put("minecraft:the_nether", 3);
		DEFAULTS.put("duality:underworld", 2); // hell
		DEFAULTS.put("duality:wasteland", DIMENSION_EXCLUDED);
		DEFAULTS.put("duality:limbo", DIMENSION_EXCLUDED);
	}

	private static Map<String, Integer> loaded;

	private DimensionStackConfig() {
	}

	private static File configFile() {
		File dir = new File(FMLPaths.CONFIGDIR.get().toFile(), "duality");
		dir.mkdirs();
		return new File(dir, "dimension_stack.json");
	}

	private static void ensureLoaded() {
		if (loaded != null)
			return;
		File file = configFile();
		if (!file.exists()) {
			save(DEFAULTS);
			loaded = new LinkedHashMap<>(DEFAULTS);
			return;
		}
		try (FileReader reader = new FileReader(file)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			Map<String, Integer> result = new LinkedHashMap<>();
			for (String key : json.keySet()) {
				result.put(key, json.get(key).getAsInt());
			}
			loaded = result;
		} catch (Exception e) {
			e.printStackTrace();
			loaded = new LinkedHashMap<>(DEFAULTS);
		}
	}

	private static void save(Map<String, Integer> values) {
		try (FileWriter writer = new FileWriter(configFile())) {
			JsonObject json = new JsonObject();
			values.forEach(json::addProperty);
			GSON.toJson(json, writer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static int tier(ResourceKey<Level> dimension) {
		ensureLoaded();
		return loaded.getOrDefault(dimension.location().toString(), 0);
	}

	public static boolean isExcluded(ResourceKey<Level> dimension) {
		return tier(dimension) == DIMENSION_EXCLUDED;
	}
}
