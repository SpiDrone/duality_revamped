package net.spidrotech.duality;

import net.spidrotech.duality.skin.SkinLoadoutCodec;
import net.spidrotech.duality.skin.SkinLoadout;

import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.ArrayList;

import java.io.IOException;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.File;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;

public class DualityDatabaseManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static File getDataDirectory(Player player) {
		if (player.level() instanceof ServerLevel serverLevel) {
			MinecraftServer server = serverLevel.getServer();
			File worldDir = server.getWorldPath(LevelResource.ROOT).toFile();
			File dualityDir = new File(worldDir, "duality_data");
			new File(dualityDir, "players").mkdirs();
			new File(dualityDir, "characters").mkdirs();
			new File(dualityDir, "npcs").mkdirs();
			return dualityDir;
		}
		return null;
	}

	private static JsonObject loadOrCreateJson(File file) {
		if (file.exists()) {
			try (FileReader reader = new FileReader(file)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return new JsonObject();
	}

	private static void saveJson(File file, JsonObject json) {
		try (FileWriter writer = new FileWriter(file)) {
			GSON.toJson(json, writer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static JsonObject getPlayerProfile(Player player) {
		File dataDir = getDataDirectory(player);
		if (dataDir == null)
			return null;
		String uuid = player.getUUID().toString();
		File playerFile = new File(new File(dataDir, "players"), uuid + ".json");
		JsonObject json = loadOrCreateJson(playerFile);
		if (!json.has("player_uuid")) {
			json.addProperty("player_uuid", uuid);
			json.addProperty("last_known_name", player.getScoreboardName());
			json.addProperty("active_character_id", "");
			JsonObject history = new JsonObject();
			history.add("alive", new JsonArray());
			history.add("dead", new JsonArray());
			json.add("character_history", history);
			JsonArray unlockedSpecies = new JsonArray();
			unlockedSpecies.add("Human");
			json.add("unlocked_species", unlockedSpecies);
			saveJson(playerFile, json);
		}
		return json;
	}

	public static void savePlayerProfile(Player player, JsonObject profileJson) {
		File dataDir = getDataDirectory(player);
		if (dataDir == null)
			return;
		String uuid = player.getUUID().toString();
		File playerFile = new File(new File(dataDir, "players"), uuid + ".json");
		saveJson(playerFile, profileJson);
	}

	public static JsonObject getCharacterSheet(Player player, String characterId) {
		if (characterId == null || characterId.isEmpty())
			return null;
		File dataDir = getDataDirectory(player);
		if (dataDir == null)
			return null;
		File charFile = new File(new File(dataDir, "characters"), characterId + ".json");
		return loadOrCreateJson(charFile);
	}

	public static void saveCharacterSheet(Player player, String characterId, JsonObject charJson) {
		if (characterId == null || characterId.isEmpty())
			return;
		File dataDir = getDataDirectory(player);
		if (dataDir == null)
			return;
		File charFile = new File(new File(dataDir, "characters"), characterId + ".json");
		saveJson(charFile, charJson);
	}

	/** The active character's id, or "" if none/unset. Central so skin code doesn't reimplement
	 *  the profile lookup. */
	public static String getActiveCharacterId(Player player) {
		JsonObject profile = getPlayerProfile(player);
		if (profile == null || !profile.has("active_character_id"))
			return "";
		return profile.get("active_character_id").getAsString();
	}

	public static String createNewCharacter(Player player, String charName, String startingSpecies) {
		File dataDir = getDataDirectory(player);
		if (dataDir == null)
			return "";
		String charId = "char_" + UUID.randomUUID().toString().substring(0, 8);
		File charFile = new File(new File(dataDir, "characters"), charId + ".json");
		JsonObject charJson = new JsonObject();
		charJson.addProperty("character_id", charId);
		charJson.addProperty("owner_uuid", player.getUUID().toString());
		charJson.addProperty("name", charName);
		charJson.addProperty("generation", 1);
		charJson.add("parent_ids", new JsonArray());
		charJson.addProperty("status", "ALIVE");
		charJson.addProperty("afterlife_realm", "OVERWORLD");
		charJson.addProperty("corpse_entity_uuid", "none");
		charJson.add("clans", new JsonArray());
		charJson.add("npc_relationships", new JsonArray());
		JsonObject stats = new JsonObject();
		stats.addProperty("duality_score", 0.0);
		stats.addProperty("max_health", 20.0);
		stats.addProperty("max_mana", 100.0);
		stats.addProperty("strength_affinity", 1.0);
		stats.addProperty("dexterity_affinity", 1.0);
		charJson.add("stats", stats);
		JsonArray profiles = new JsonArray();
		if (!startingSpecies.equalsIgnoreCase("Human")) {
			JsonObject initialSpecies = new JsonObject();
			initialSpecies.addProperty("class", startingSpecies);
			initialSpecies.addProperty("subspecies", startingSpecies);
			initialSpecies.addProperty("subtier", "Baseline");
			initialSpecies.addProperty("level", 1);
			initialSpecies.addProperty("xp", 0);
			initialSpecies.add("species_bound_unlocks", new JsonArray());
			profiles.add(initialSpecies);
		}
		charJson.add("species_profiles", profiles);
		charJson.add("persistent_unlocks", new JsonArray());
		charJson.add("children", new JsonArray());
		JsonObject deathMeta = new JsonObject();
		deathMeta.addProperty("is_canon_dead", false);
		deathMeta.addProperty("timestamp", 0);
		deathMeta.addProperty("death_cause", "n/a");
		charJson.add("death_metadata", deathMeta);
		// Char-creator appearance for this character - a fresh character starts on the default
		// base body with no parts and no cosmetic unlocks. This is the section the skin system
		// (SkinManager / SkinUnlocks, via SkinLoadoutCodec) reads and writes; keeping it on the
		// CHARACTER sheet rather than the player profile is what makes appearance swap when the
		// player swaps characters, exactly like waypoints and stats already do.
		JsonObject charCreator = new JsonObject();
		charCreator.addProperty("base", "skins_skin");
		charCreator.add("parts", new JsonArray());
		charCreator.add("unlocks", new JsonArray());
		charJson.add(SkinLoadoutCodec.SECTION, charCreator);
		saveJson(charFile, charJson);
		JsonObject playerProfile = getPlayerProfile(player);
		if (playerProfile != null) {
			playerProfile.addProperty("active_character_id", charId);
			JsonObject history = playerProfile.getAsJsonObject("character_history");
			JsonArray aliveList = history.getAsJsonArray("alive");
			aliveList.add(charId);
			savePlayerProfile(player, playerProfile);
		}
		return charId;
	}

	public static boolean shouldStartCreateCharacter(Player player) {
		JsonObject profile = getPlayerProfile(player);
		if (profile == null)
			return true;
		String activeCharId = profile.has("active_character_id") ? profile.get("active_character_id").getAsString() : "";
		if (activeCharId.isEmpty()) {
			return true;
		}
		JsonObject charSheet = getCharacterSheet(player, activeCharId);
		if (charSheet == null || !charSheet.has("status") || "DEAD".equalsIgnoreCase(charSheet.get("status").getAsString())) {
			return true;
		}
		return false;
	}

	public static void startCreateCharacter(Player player) {
		if (shouldStartCreateCharacter(player)) {
			JsonObject profile = getPlayerProfile(player);
			if (profile != null) {
				profile.addProperty("active_character_id", "");
				savePlayerProfile(player, profile);
			}
		}
	}

	/**
	 * Switches which character is active. Returns false (changing nothing) if the target id
	 * isn't in this player's own alive list - so a mistyped or someone-else's id can't be set
	 * active, and a dead character can't be resurrected by reactivation. On success the caller
	 * is responsible for re-syncing anything that keys off the active character (the skin
	 * system does this in SkinManager#onActiveCharacterChanged).
	 */
	public static boolean setActiveCharacter(Player player, String characterId) {
		JsonObject profile = getPlayerProfile(player);
		if (profile == null || characterId == null || characterId.isEmpty())
			return false;
		JsonObject history = profile.getAsJsonObject("character_history");
		JsonArray aliveList = history.getAsJsonArray("alive");
		boolean owns = false;
		for (int i = 0; i < aliveList.size(); i++) {
			if (aliveList.get(i).getAsString().equals(characterId)) {
				owns = true;
				break;
			}
		}
		if (!owns)
			return false;
		profile.addProperty("active_character_id", characterId);
		savePlayerProfile(player, profile);
		return true;
	}

	/** This player's alive character ids, in order - for a "switch character" picker/command. */
	public static List<String> getAliveCharacterIds(Player player) {
		JsonObject profile = getPlayerProfile(player);
		List<String> ids = new ArrayList<>();
		if (profile == null)
			return ids;
		JsonArray aliveList = profile.getAsJsonObject("character_history").getAsJsonArray("alive");
		for (int i = 0; i < aliveList.size(); i++) {
			ids.add(aliveList.get(i).getAsString());
		}
		return ids;
	}

	public static void handleCanonDeath(Player player, String deathCause) {
		JsonObject playerProfile = getPlayerProfile(player);
		if (playerProfile == null)
			return;
		String activeCharId = playerProfile.get("active_character_id").getAsString();
		if (activeCharId.isEmpty())
			return;
		JsonObject charSheet = getCharacterSheet(player, activeCharId);
		if (charSheet != null) {
			charSheet.addProperty("status", "DEAD");
			charSheet.addProperty("afterlife_realm", "LIMBO");
			JsonObject deathMeta = charSheet.getAsJsonObject("death_metadata");
			deathMeta.addProperty("is_canon_dead", true);
			deathMeta.addProperty("timestamp", System.currentTimeMillis());
			deathMeta.addProperty("death_cause", deathCause);
			saveCharacterSheet(player, activeCharId, charSheet);
			JsonObject history = playerProfile.getAsJsonObject("character_history");
			JsonArray aliveList = history.getAsJsonArray("alive");
			JsonArray deadList = history.getAsJsonArray("dead");
			JsonArray newAlive = new JsonArray();
			for (int i = 0; i < aliveList.size(); i++) {
				String id = aliveList.get(i).getAsString();
				if (!id.equals(activeCharId)) {
					newAlive.add(id);
				}
			}
			history.add("alive", newAlive);
			deadList.add(activeCharId);
			playerProfile.addProperty("active_character_id", "");
			savePlayerProfile(player, playerProfile);
		}
	}

	// ================================================================== char-creator / skin
	/** The active character's saved appearance, or EMPTY if there's no active character or it has
	 *  no char_creator section yet (older sheet from before this feature - handled gracefully by
	 *  the codec rather than needing a migration pass). */
	public static SkinLoadout getSkinLoadout(Player player, String characterId) {
		JsonObject charSheet = getCharacterSheet(player, characterId);
		if (charSheet == null)
			return SkinLoadout.EMPTY;
		JsonObject section = charSheet.has(SkinLoadoutCodec.SECTION) ? charSheet.getAsJsonObject(SkinLoadoutCodec.SECTION) : null;
		return SkinLoadoutCodec.fromSection(section);
	}

	public static void setSkinLoadout(Player player, String characterId, SkinLoadout loadout) {
		JsonObject charSheet = getCharacterSheet(player, characterId);
		if (charSheet == null)
			return;
		JsonObject section = charSheet.has(SkinLoadoutCodec.SECTION) ? charSheet.getAsJsonObject(SkinLoadoutCodec.SECTION) : new JsonObject();
		SkinLoadoutCodec.writeInto(section, loadout); // leaves the "unlocks" array intact
		charSheet.add(SkinLoadoutCodec.SECTION, section);
		saveCharacterSheet(player, characterId, charSheet);
	}

	/** Cosmetic unlock ids earned by this character. Stored inside the char_creator section next
	 *  to the loadout so appearance-related data stays in one place; distinct from the gameplay
	 *  "persistent_unlocks" array. */
	public static Set<String> getSkinUnlocks(Player player, String characterId) {
		JsonObject charSheet = getCharacterSheet(player, characterId);
		Set<String> unlocks = new LinkedHashSet<>();
		if (charSheet == null || !charSheet.has(SkinLoadoutCodec.SECTION))
			return unlocks;
		JsonObject section = charSheet.getAsJsonObject(SkinLoadoutCodec.SECTION);
		if (!section.has("unlocks"))
			return unlocks;
		JsonArray array = section.getAsJsonArray("unlocks");
		for (int i = 0; i < array.size(); i++) {
			unlocks.add(array.get(i).getAsString());
		}
		return unlocks;
	}

	public static void setSkinUnlocks(Player player, String characterId, Set<String> unlockIds) {
		JsonObject charSheet = getCharacterSheet(player, characterId);
		if (charSheet == null)
			return;
		JsonObject section = charSheet.has(SkinLoadoutCodec.SECTION) ? charSheet.getAsJsonObject(SkinLoadoutCodec.SECTION) : new JsonObject();
		JsonArray array = new JsonArray();
		for (String id : unlockIds) {
			array.add(id);
		}
		section.add("unlocks", array);
		charSheet.add(SkinLoadoutCodec.SECTION, section);
		saveCharacterSheet(player, characterId, charSheet);
	}

	// ================================================================== waypoints
	public static List<CharacterWaypoint> getWaypoints(Player player, String characterId) {
		JsonObject charSheet = getCharacterSheet(player, characterId);
		if (charSheet == null || !charSheet.has("waypoints"))
			return new ArrayList<>();
		JsonArray array = charSheet.getAsJsonArray("waypoints");
		List<CharacterWaypoint> waypoints = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			try {
				waypoints.add(CharacterWaypoint.fromJson(array.get(i).getAsJsonObject()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return waypoints;
	}

	public static void addWaypoint(Player player, String characterId, CharacterWaypoint waypoint) {
		JsonObject charSheet = getCharacterSheet(player, characterId);
		if (charSheet == null)
			return;
		JsonArray existing = charSheet.has("waypoints") ? charSheet.getAsJsonArray("waypoints") : new JsonArray();
		JsonArray updated = new JsonArray();
		for (int i = 0; i < existing.size(); i++) {
			JsonObject entry = existing.get(i).getAsJsonObject();
			if (!entry.get("name").getAsString().equalsIgnoreCase(waypoint.name())) {
				updated.add(entry);
			}
		}
		updated.add(waypoint.toJson());
		charSheet.add("waypoints", updated);
		saveCharacterSheet(player, characterId, charSheet);
	}

	public static boolean removeWaypoint(Player player, String characterId, String name) {
		JsonObject charSheet = getCharacterSheet(player, characterId);
		if (charSheet == null || !charSheet.has("waypoints"))
			return false;
		JsonArray existing = charSheet.getAsJsonArray("waypoints");
		JsonArray updated = new JsonArray();
		boolean removed = false;
		for (int i = 0; i < existing.size(); i++) {
			JsonObject entry = existing.get(i).getAsJsonObject();
			if (entry.get("name").getAsString().equalsIgnoreCase(name)) {
				removed = true;
				continue;
			}
			updated.add(entry);
		}
		if (removed) {
			charSheet.add("waypoints", updated);
			saveCharacterSheet(player, characterId, charSheet);
		}
		return removed;
	}
}