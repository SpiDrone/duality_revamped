package net.spidrotech.duality.village;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File-backed home for every village, NPC and the world duality score.
 *
 * <p>Lives under the same duality_data/ directory DualityDatabaseManager already uses, in
 * villages/, npcs/ and world.json. Records are read once into memory and written back on save,
 * because the simulation touches the same handful of villages over and over and re-parsing a file
 * per lookup would be silly.
 *
 * <p>Deliberately knows nothing about Minecraft - it takes a directory. {@link VillageData} is the
 * layer that turns a running server into one of these, and this split is what lets the simulation
 * be exercised outside the game.
 */
public class VillageStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final File root;
	private final File villageDir;
	private final File npcDir;
	private final File worldFile;

	private final Map<String, VillageRecord> villages = new LinkedHashMap<>();
	private final Map<String, NpcRecord> npcs = new LinkedHashMap<>();
	/** entity uuid -> npc id, so an entity death can be traced back to a record without scanning
	 *  every NPC in the world on every mob death. Entries are verified on read rather than purged
	 *  on write, so a stale one costs a failed lookup and nothing else. */
	private final Map<String, String> npcByEntity = new LinkedHashMap<>();
	private WorldDualityState world = new WorldDualityState();

	public VillageStore(File root) {
		this.root = root;
		this.villageDir = new File(root, "villages");
		this.npcDir = new File(root, "npcs");
		this.worldFile = new File(root, "world.json");
	}

	/** Reads everything off disk. Safe to call again; it replaces the in-memory state. */
	public VillageStore load() {
		villageDir.mkdirs();
		npcDir.mkdirs();
		villages.clear();
		npcs.clear();
		npcByEntity.clear();
		readAll(villageDir, json -> {
			VillageRecord village = VillageRecord.fromJson(json);
			villages.put(village.villageId(), village);
		});
		readAll(npcDir, json -> {
			NpcRecord npc = NpcRecord.fromJson(json);
			npcs.put(npc.npcId(), npc);
			index(npc);
		});
		world = WorldDualityState.fromJson(readJson(worldFile));
		return this;
	}

	public File root() {
		return root;
	}

	// ------------------------------------------------------------------------------- villages
	public Collection<VillageRecord> villages() {
		return villages.values();
	}

	public VillageRecord village(String villageId) {
		return villageId == null ? null : villages.get(villageId);
	}

	/** Lookup by display name, case-insensitive. This is what {@code run(villageName, event)}
	 *  resolves through, so names are expected to be unique; {@link #nameIsTaken} enforces it at
	 *  creation time. */
	public VillageRecord villageByName(String name) {
		if (name == null)
			return null;
		for (VillageRecord village : villages.values()) {
			if (village.name().equalsIgnoreCase(name))
				return village;
		}
		return null;
	}

	/** Accepts either a display name or a raw village id, so commands and code can use whichever
	 *  they have to hand. */
	public VillageRecord resolve(String nameOrId) {
		VillageRecord byName = villageByName(nameOrId);
		return byName != null ? byName : village(nameOrId);
	}

	public boolean nameIsTaken(String name) {
		return villageByName(name) != null;
	}

	/** Nearest village of a given faction within maxDistance blocks, or null. Pass a null faction
	 *  to accept any. Used to find the clan hold that has a reason to come raiding. */
	public VillageRecord nearestVillage(WorldPoint from, VillageFaction faction, double maxDistance, String excludeVillageId) {
		VillageRecord best = null;
		double bestDistSqr = maxDistance * maxDistance;
		for (VillageRecord village : villages.values()) {
			if (village.villageId().equals(excludeVillageId))
				continue;
			if (faction != null && village.faction() != faction)
				continue;
			if (village.isAbandoned())
				continue;
			double distSqr = village.center().horizontalDistanceSqr(from);
			if (distSqr < 0 || distSqr > bestDistSqr)
				continue;
			bestDistSqr = distSqr;
			best = village;
		}
		return best;
	}

	public void add(VillageRecord village) {
		villages.put(village.villageId(), village);
		save(village);
	}

	public void save(VillageRecord village) {
		writeJson(new File(villageDir, village.villageId() + ".json"), village.toJson());
	}

	/** Removes a village and orphans nothing: its residents are marked missing rather than left
	 *  pointing at a file that no longer exists. */
	public boolean delete(String villageId) {
		VillageRecord village = villages.remove(villageId);
		if (village == null)
			return false;
		for (String npcId : new ArrayList<>(village.residents())) {
			NpcRecord npc = npc(npcId);
			if (npc != null && npc.isAlive()) {
				npc.setStatus(NpcStatus.MISSING);
				npc.setCurrentVillageId("");
				save(npc);
			}
		}
		new File(villageDir, villageId + ".json").delete();
		return true;
	}

	// ----------------------------------------------------------------------------------- npcs
	public Collection<NpcRecord> npcs() {
		return npcs.values();
	}

	public NpcRecord npc(String npcId) {
		return npcId == null ? null : npcs.get(npcId);
	}

	public void add(NpcRecord npc) {
		npcs.put(npc.npcId(), npc);
		save(npc);
	}

	public void save(NpcRecord npc) {
		index(npc);
		writeJson(new File(npcDir, npc.npcId() + ".json"), npc.toJson());
	}

	/** The NPC whose entity has this uuid, or null. Used by the death hook, which fires for every
	 *  mob in the world, so this has to stay cheap. */
	public NpcRecord npcByEntityUuid(String entityUuid) {
		if (entityUuid == null || entityUuid.isEmpty() || npcByEntity.isEmpty())
			return null;
		String npcId = npcByEntity.get(entityUuid);
		if (npcId == null)
			return null;
		NpcRecord npc = npcs.get(npcId);
		if (npc == null || !entityUuid.equals(npc.entityUuid())) {
			npcByEntity.remove(entityUuid);
			return null;
		}
		return npc;
	}

	private void index(NpcRecord npc) {
		if (npc.entityUuid() != null && !npc.entityUuid().isEmpty())
			npcByEntity.put(npc.entityUuid(), npc.npcId());
	}

	/** Living named residents of a village, in roster order. */
	public List<NpcRecord> residentsOf(VillageRecord village) {
		List<NpcRecord> found = new ArrayList<>();
		for (String npcId : village.residents()) {
			NpcRecord npc = npc(npcId);
			if (npc != null && npc.isAlive())
				found.add(npc);
		}
		return found;
	}

	/** Everyone currently held in a village against their will. */
	public List<NpcRecord> captivesOf(VillageRecord village) {
		List<NpcRecord> found = new ArrayList<>();
		for (String npcId : village.captives()) {
			NpcRecord npc = npc(npcId);
			if (npc != null)
				found.add(npc);
		}
		return found;
	}

	// ---------------------------------------------------------------------------------- world
	public WorldDualityState world() {
		return world;
	}

	public void saveWorld() {
		writeJson(worldFile, world.toJson());
	}

	/** Moves the world score and persists it in one step, returning the delta actually applied. */
	public double applyDuality(double delta) {
		double applied = world.addDualityScore(delta);
		saveWorld();
		return applied;
	}

	/** Flushes everything. Called on world save and shutdown rather than per-mutation. */
	public void saveAll() {
		for (VillageRecord village : villages.values()) {
			save(village);
		}
		for (NpcRecord npc : npcs.values()) {
			save(npc);
		}
		saveWorld();
	}

	// ------------------------------------------------------------------------------------- io
	private interface JsonConsumer {
		void accept(JsonObject json);
	}

	private static void readAll(File dir, JsonConsumer consumer) {
		File[] files = dir.listFiles((file, name) -> name.endsWith(".json"));
		if (files == null)
			return;
		for (File file : files) {
			JsonObject json = readJson(file);
			// A file that's been hand-edited into nonsense shouldn't take the rest of the save with
			// it - skip it and keep loading.
			if (json == null || json.entrySet().isEmpty())
				continue;
			try {
				consumer.accept(json);
			} catch (Exception e) {
				System.err.println("[duality] skipping unreadable village record " + file.getName() + ": " + e);
			}
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
