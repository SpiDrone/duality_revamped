package net.spidrotech.duality.village;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One named villager, persisted to duality_data/npcs/&lt;id&gt;.json and outliving their entity.
 *
 * <p>The entity is a rendering of this record, not the other way round. That inversion is what
 * makes the abduction chain work: a vampire raid can despawn the entity, move the record into the
 * clan's hold and set a fate, all while the chunk is unloaded and the player is a thousand blocks
 * away. When the player finally walks into the clan hold, the record is what decides whether they
 * find a prisoner, a vampire, or a corpse.
 */
public class NpcRecord {
	private final String npcId;
	private String name;
	private NpcJob job = NpcJob.IDLE;
	/** Which of the duality species this NPC is - "Human", "Vampire", "Witch", "Thrall". Together
	 *  with the job and the village's faction this picks which entity they spawn as; see
	 *  {@link VillageNpcTypes}. */
	private String species = "Human";
	private String homeVillageId = "";
	private String currentVillageId = "";
	private NpcStatus status = NpcStatus.RESIDENT;
	private NpcFate fate = NpcFate.NONE;
	/** In-game day the fate resolves on; ignored unless the fate resolves on a timer. */
	private long fateDay = 0;
	/** Registry id of the entity this record renders as. Empty means "work it out from my species
	 *  and job when I'm next spawned", which is the normal case - see {@link VillageNpcTypes}. It's
	 *  only pinned to a specific id when an existing entity was enrolled. */
	private String entityType = "";
	/** UUID of the live entity while one exists, or empty once despawned. */
	private String entityUuid = "";
	private boolean inWorld = false;
	/** Rough worth in a fight; the sum over residents is a village's militia contribution, and a
	 *  raid prefers to carry off whoever is cheapest to grab. */
	private double combatValue = 1.0;
	/** Where the body was left, for NPCs who died somewhere the player can walk to. */
	private WorldPoint corpseLocation = null;
	private final List<String> log = new ArrayList<>();

	public NpcRecord(String npcId, String name) {
		this.npcId = npcId;
		this.name = name;
	}

	public static NpcRecord create(String name, String homeVillageId) {
		NpcRecord npc = new NpcRecord("npc_" + UUID.randomUUID().toString().substring(0, 8), name);
		npc.homeVillageId = homeVillageId;
		npc.currentVillageId = homeVillageId;
		return npc;
	}

	public String npcId() {
		return npcId;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public NpcJob job() {
		return job;
	}

	public void setJob(NpcJob job) {
		this.job = job == null ? NpcJob.IDLE : job;
	}

	/** Lower-case display form for narrative text: "farmer", "witch". */
	public String jobLabel() {
		return job.displayName().toLowerCase();
	}

	public String species() {
		return species;
	}

	public void setSpecies(String species) {
		this.species = species;
	}

	public String homeVillageId() {
		return homeVillageId;
	}

	public void setHomeVillageId(String homeVillageId) {
		this.homeVillageId = homeVillageId;
	}

	public String currentVillageId() {
		return currentVillageId;
	}

	public void setCurrentVillageId(String currentVillageId) {
		this.currentVillageId = currentVillageId;
	}

	public NpcStatus status() {
		return status;
	}

	public void setStatus(NpcStatus status) {
		this.status = status;
	}

	public NpcFate fate() {
		return fate;
	}

	public long fateDay() {
		return fateDay;
	}

	/** Sets the fate and, for timed fates, the day it comes due. */
	public void setFate(NpcFate fate, long currentDay) {
		this.fate = fate;
		this.fateDay = fate.resolvesOnTimer() ? currentDay + fate.daysToResolve() : 0;
	}

	public void clearFate() {
		this.fate = NpcFate.NONE;
		this.fateDay = 0;
	}

	public String entityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public String entityUuid() {
		return entityUuid;
	}

	public void setEntityUuid(String entityUuid) {
		this.entityUuid = entityUuid == null ? "" : entityUuid;
	}

	public boolean inWorld() {
		return inWorld;
	}

	public void setInWorld(boolean inWorld) {
		this.inWorld = inWorld;
	}

	public double combatValue() {
		return combatValue;
	}

	public void setCombatValue(double combatValue) {
		this.combatValue = combatValue;
	}

	public WorldPoint corpseLocation() {
		return corpseLocation;
	}

	public void setCorpseLocation(WorldPoint corpseLocation) {
		this.corpseLocation = corpseLocation;
	}

	public List<String> log() {
		return log;
	}

	/** Appends a dated line to this NPC's history. This is the text a player would be told when
	 *  they ask around about someone who went missing, so write it readable. */
	public void addLogEntry(long day, String text) {
		log.add("day " + day + ": " + text);
		while (log.size() > 40) {
			log.remove(0);
		}
	}

	public boolean isAlive() {
		return status != NpcStatus.DEAD;
	}

	/** Whether this NPC should have an entity in the world right now. Captives, the missing and
	 *  the dead don't; residents of a loaded village do. */
	public boolean shouldBeEmbodied() {
		return status == NpcStatus.RESIDENT || status == NpcStatus.TURNED;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("npc_id", npcId);
		json.addProperty("name", name);
		json.addProperty("job", job.name());
		json.addProperty("species", species);
		json.addProperty("home_village", homeVillageId);
		json.addProperty("current_village", currentVillageId);
		json.addProperty("status", status.name());
		json.addProperty("fate", fate.name());
		json.addProperty("fate_day", fateDay);
		json.addProperty("entity_type", entityType);
		json.addProperty("entity_uuid", entityUuid);
		json.addProperty("in_world", inWorld);
		json.addProperty("combat_value", combatValue);
		if (corpseLocation != null)
			json.add("corpse_location", corpseLocation.toJson());
		JsonArray logArray = new JsonArray();
		for (String line : log) {
			logArray.add(line);
		}
		json.add("log", logArray);
		return json;
	}

	public static NpcRecord fromJson(JsonObject json) {
		NpcRecord npc = new NpcRecord(json.get("npc_id").getAsString(), json.has("name") ? json.get("name").getAsString() : "Unnamed");
		if (json.has("job"))
			npc.job = NpcJob.parse(json.get("job").getAsString());
		else if (json.has("role"))
			npc.job = NpcJob.parse(json.get("role").getAsString());
		if (json.has("species"))
			npc.species = json.get("species").getAsString();
		if (json.has("home_village"))
			npc.homeVillageId = json.get("home_village").getAsString();
		if (json.has("current_village"))
			npc.currentVillageId = json.get("current_village").getAsString();
		npc.status = NpcStatus.parse(json.has("status") ? json.get("status").getAsString() : null);
		npc.fate = NpcFate.parse(json.has("fate") ? json.get("fate").getAsString() : null);
		if (json.has("fate_day"))
			npc.fateDay = json.get("fate_day").getAsLong();
		if (json.has("entity_type"))
			npc.entityType = json.get("entity_type").getAsString();
		if (json.has("entity_uuid"))
			npc.entityUuid = json.get("entity_uuid").getAsString();
		if (json.has("in_world"))
			npc.inWorld = json.get("in_world").getAsBoolean();
		if (json.has("combat_value"))
			npc.combatValue = json.get("combat_value").getAsDouble();
		if (json.has("corpse_location"))
			npc.corpseLocation = WorldPoint.fromJson(json.getAsJsonObject("corpse_location"));
		if (json.has("log")) {
			JsonArray logArray = json.getAsJsonArray("log");
			for (int i = 0; i < logArray.size(); i++) {
				npc.log.add(logArray.get(i).getAsString());
			}
		}
		return npc;
	}
}
