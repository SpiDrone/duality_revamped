package net.spidrotech.duality.faction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * File-backed home for every faction, at duality_data/factions/&lt;id&gt;.json - same root
 * {@link net.spidrotech.duality.village.VillageStore} and DualityDatabaseManager's character/npc
 * files already share, and the same read-once/write-through shape as VillageStore.
 *
 * <p>Knows nothing about Minecraft, same reasoning as VillageStore: this can be exercised with a
 * plain directory and no server running.
 */
public class FactionStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final File root;
	private final File factionDir;
	private final Map<String, FactionRecord> factions = new LinkedHashMap<>();
	/** memberId -> factionId, so "what faction is this character/npc in" doesn't scan the roster
	 *  of every faction on every lookup. Rebuilt on load; kept current by add/save/delete. */
	private final Map<String, String> factionByMember = new LinkedHashMap<>();

	public FactionStore(File root) {
		this.root = root;
		this.factionDir = new File(root, "factions");
	}

	public FactionStore load() {
		factionDir.mkdirs();
		factions.clear();
		factionByMember.clear();
		File[] files = factionDir.listFiles((file, name) -> name.endsWith(".json"));
		if (files != null) {
			for (File file : files) {
				JsonObject json = readJson(file);
				if (json == null || json.entrySet().isEmpty())
					continue;
				try {
					FactionRecord faction = FactionRecord.fromJson(json);
					factions.put(faction.factionId(), faction);
					index(faction);
				} catch (Exception e) {
					System.err.println("[duality] skipping unreadable faction record " + file.getName() + ": " + e);
				}
			}
		}
		return this;
	}

	public File root() {
		return root;
	}

	public Collection<FactionRecord> factions() {
		return factions.values();
	}

	public FactionRecord faction(String factionId) {
		return factionId == null ? null : factions.get(factionId);
	}

	/** Lookup by display name, case-insensitive - what commands resolve through. */
	public FactionRecord factionByName(String name) {
		if (name == null)
			return null;
		for (FactionRecord faction : factions.values()) {
			if (faction.name().equalsIgnoreCase(name))
				return faction;
		}
		return null;
	}

	/** Accepts either a display name or a raw faction id. */
	public FactionRecord resolve(String nameOrId) {
		FactionRecord byName = factionByName(nameOrId);
		return byName != null ? byName : faction(nameOrId);
	}

	public boolean nameIsTaken(String name) {
		return factionByName(name) != null;
	}

	/** The faction memberId (a character id or npc id) currently belongs to, or null. A member can
	 *  only ever be in one faction at a time - joining a second one isn't offered anywhere in
	 *  Factions, so this doesn't need to return more than one. */
	public FactionRecord factionOf(String memberId) {
		if (memberId == null)
			return null;
		String factionId = factionByMember.get(memberId);
		if (factionId == null)
			return null;
		FactionRecord faction = factions.get(factionId);
		if (faction == null || !faction.isMember(memberId)) {
			factionByMember.remove(memberId);
			return null;
		}
		return faction;
	}

	public void add(FactionRecord faction) {
		factions.put(faction.factionId(), faction);
		save(faction);
	}

	public void save(FactionRecord faction) {
		index(faction);
		writeJson(new File(factionDir, faction.factionId() + ".json"), faction.toJson());
	}

	public boolean delete(String factionId) {
		FactionRecord faction = factions.remove(factionId);
		if (faction == null)
			return false;
		for (String memberId : faction.members().keySet()) {
			factionByMember.remove(memberId);
		}
		new File(factionDir, factionId + ".json").delete();
		return true;
	}

	private void index(FactionRecord faction) {
		for (String memberId : faction.members().keySet()) {
			factionByMember.put(memberId, faction.factionId());
		}
	}

	public void saveAll() {
		for (FactionRecord faction : factions.values()) {
			save(faction);
		}
	}

	private static JsonObject readJson(File file) {
		if (!file.exists())
			return new JsonObject();
		try (FileReader reader = new FileReader(file)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			System.err.println("[duality] could not read " + file + ": " + e);
			return new JsonObject();
		}
	}

	private static void writeJson(File file, JsonObject json) {
		File parent = file.getParentFile();
		if (parent != null)
			parent.mkdirs();
		try (FileWriter writer = new FileWriter(file)) {
			GSON.toJson(json, writer);
		} catch (IOException e) {
			System.err.println("[duality] could not write " + file + ": " + e);
		}
	}
}
