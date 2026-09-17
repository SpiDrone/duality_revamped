package net.spidrotech.duality.village;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the mod knows about one village, persisted to duality_data/villages/&lt;id&gt;.json.
 *
 * <p>A village is a ledger first and a place second. The blocks on the ground are a rendering of
 * this file; the file is what keeps ticking while nobody's looking. That's the whole design: the
 * simulation reads and writes these numbers whether or not the chunks are loaded, so a player who
 * goes away for twenty days comes back to a village that spent twenty days being somewhere.
 */
public class VillageRecord {
	/** One line of village history. This is the readable record a player can be shown - "what
	 *  happened here while I was gone" - and the audit trail for the world duality score. */
	public record LogEntry(long day, String eventId, String outcome, String text, double dualityDelta) {
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("day", day);
			json.addProperty("event", eventId);
			json.addProperty("outcome", outcome);
			json.addProperty("text", text);
			json.addProperty("duality_delta", dualityDelta);
			return json;
		}

		public static LogEntry fromJson(JsonObject json) {
			return new LogEntry(json.has("day") ? json.get("day").getAsLong() : 0L, json.has("event") ? json.get("event").getAsString() : "UNKNOWN",
					json.has("outcome") ? json.get("outcome").getAsString() : "UNKNOWN", json.has("text") ? json.get("text").getAsString() : "",
					json.has("duality_delta") ? json.get("duality_delta").getAsDouble() : 0.0);
		}
	}

	/** How many log lines a village keeps before the oldest fall off the end. */
	private static final int LOG_LIMIT = 60;
	/** Days of food a village with no pantry can keep. Enough for a few days of bad luck and no
	 *  more - there is physically nowhere to put the rest. */
	public static final double NO_PANTRY_CAPACITY = 8.0;

	private final String villageId;
	private String name;
	private VillageFaction faction;
	private WorldPoint center = WorldPoint.ORIGIN;
	private int radius = 48;
	private long foundedDay = 0;
	/** Last in-game day this village has been simulated through. The gap between this and the
	 *  current day is exactly the backlog {@link VillageSimulator} has to work off. */
	private long lastSimulatedDay = 0;

	/** Total souls, named and unnamed. Always at least the number of living named residents. */
	private int population = 0;
	/** Trained defenders. Counts for far more than raw population in a fight. */
	private int militia = 0;
	/** Walls, gates, towers. 0-10. */
	private int fortification = 0;
	/** Standing protective magic. 0-10, and it decays - a blessing is not permanent. */
	private double wards = 0.0;
	/** 0-1. Scales everything else; a village that's been raided twice defends worse the third time. */
	private double morale = 0.75;
	/** 0-100. Feeds population growth and building, and cushions SOCIAL threats. */
	private double prosperity = 25.0;
	/** Days of food in the granary. Runs out and people start dying - see {@link VillageEconomy}. */
	private double foodStores = 0.0;
	/** Accumulated mason/laborer work toward the next building. */
	private double buildProgress = 0.0;
	/** Food units the pantry containers held the last time anyone looked. The difference between
	 *  this and what's in them now is what a player added or took, which is the only way the world
	 *  gets to talk back to the ledger - see {@link VillagePantry}. */
	private double pantryCheckpoint = 0.0;
	/** Days of ruined harvests left to run. While this is positive the fields yield a fraction of
	 *  normal - see {@link VillageEconomy#BLIGHT_YIELD}. It's the one thing that can starve a
	 *  village no matter how sensibly it assigns its people, which is what makes it worth a quest
	 *  to lift rather than a number to wait out. */
	private int blightDays = 0;

	// Derived from the resident roster once per day by VillageEconomy#recompute, and persisted only
	// so the json is readable. Never edit these directly - change who lives here and what they do.
	private double garrison = 0.0;
	private double foodProduction = 0.0;
	private double foodUpkeep = 0.0;

	private final List<VillageBuilding> buildings = new ArrayList<>();
	/** Named NPCs who live here. */
	private final Set<String> residents = new LinkedHashSet<>();
	/** Named NPCs held here against their will - the rescue targets. */
	private final Set<String> captives = new LinkedHashSet<>();
	/** villageId -> -100 (at war) .. 100 (allied). A vampire clan's negative relation with a human
	 *  village is what makes it the one that comes raiding. */
	private final Map<String, Integer> relations = new LinkedHashMap<>();
	private final Set<String> flags = new LinkedHashSet<>();
	private final List<LogEntry> log = new ArrayList<>();

	public VillageRecord(String villageId, String name, VillageFaction faction) {
		this.villageId = villageId;
		this.name = name;
		this.faction = faction;
	}

	/** Mints a village with starting numbers appropriate to its faction. */
	public static VillageRecord create(String name, VillageFaction faction, WorldPoint center, long day) {
		VillageRecord village = new VillageRecord("vil_" + UUID.randomUUID().toString().substring(0, 8), name, faction);
		village.center = center;
		village.foundedDay = day;
		village.lastSimulatedDay = day;
		switch (faction) {
			case HUMAN -> {
				village.population = 12;
				village.militia = 2;
				village.fortification = 1;
			}
			case WITCH_COVEN -> {
				village.population = 6;
				village.militia = 1;
				village.wards = 5.0;
				village.prosperity = 30;
			}
			case WHITELIGHTER_SANCTUARY -> {
				village.population = 5;
				village.wards = 7.0;
				village.morale = 0.95;
				village.prosperity = 35;
			}
			case VAMPIRE_CLAN -> {
				village.population = 8;
				village.militia = 5;
				village.fortification = 3;
				village.wards = 2.0;
			}
			case DEMON_HOLD -> {
				village.population = 10;
				village.militia = 7;
				village.fortification = 4;
				village.wards = 3.0;
				village.morale = 0.9;
			}
			case OUTLAW_CAMP -> {
				village.population = 9;
				village.militia = 4;
				village.fortification = 2;
				village.morale = 0.6;
				village.prosperity = 15;
			}
		}
		// A settlement that exists already has the houses its people live in and somewhere to keep
		// food, both at the centre. Starting with no pantry would not be a challenge, just a slow
		// death - there would be nowhere to put a good harvest.
		village.buildings.add(new VillageBuilding(BuildingType.PANTRY, day, center));
		for (int houses = Math.max(1, village.population / BuildingType.HOUSE.housing()); houses > 0; houses--) {
			village.buildings.add(new VillageBuilding(BuildingType.HOUSE, day, center));
		}
		village.foodStores = village.population * 1.5;
		return village;
	}

	public String villageId() {
		return villageId;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public VillageFaction faction() {
		return faction;
	}

	public void setFaction(VillageFaction faction) {
		this.faction = faction;
	}

	public WorldPoint center() {
		return center;
	}

	public void setCenter(WorldPoint center) {
		this.center = center;
	}

	public int radius() {
		return radius;
	}

	public void setRadius(int radius) {
		this.radius = Math.max(8, radius);
	}

	public long foundedDay() {
		return foundedDay;
	}

	public long lastSimulatedDay() {
		return lastSimulatedDay;
	}

	public void setLastSimulatedDay(long day) {
		this.lastSimulatedDay = day;
	}

	public int population() {
		return population;
	}

	public void setPopulation(int population) {
		this.population = Math.max(0, population);
	}

	public void addPopulation(int delta) {
		setPopulation(population + delta);
	}

	public int militia() {
		return militia;
	}

	public void setMilitia(int militia) {
		this.militia = Math.max(0, Math.min(militia, Math.max(1, population)));
	}

	public int fortification() {
		return fortification;
	}

	public void setFortification(int fortification) {
		this.fortification = clampInt(fortification, 0, 10);
	}

	public double wards() {
		return wards;
	}

	public void setWards(double wards) {
		this.wards = clamp(wards, 0, 10);
	}

	public double morale() {
		return morale;
	}

	public void setMorale(double morale) {
		this.morale = clamp(morale, 0.05, 1.0);
	}

	public double prosperity() {
		return prosperity;
	}

	public void setProsperity(double prosperity) {
		this.prosperity = clamp(prosperity, 0, 100);
	}

	public double foodStores() {
		return foodStores;
	}

	public void setFoodStores(double foodStores) {
		this.foodStores = clamp(foodStores, 0, foodCapacity());
	}

	/**
	 * How much food this village can hold - which is to say, how much its pantries can hold.
	 *
	 * <p>A village with nowhere to put food lives hand to mouth on {@link #NO_PANTRY_CAPACITY}, and
	 * any bad week kills people. Building a pantry is the single biggest thing a settlement can do
	 * for its own survival, and it's the thing a player can walk up to and fill.
	 */
	public double foodCapacity() {
		double fromPantries = 0;
		for (VillageBuilding building : buildings) {
			if (building.isUsableStore())
				fromPantries += building.type().foodCapacity();
		}
		return fromPantries > 0 ? fromPantries : NO_PANTRY_CAPACITY;
	}

	/** Food produced minus food eaten, per day. Negative means the granary is draining. */
	public double foodBalance() {
		return foodProduction - foodUpkeep;
	}

	/** Days of food left at the current rate, or -1 when the village is not running a deficit. */
	public double daysOfFoodLeft() {
		double balance = foodBalance();
		return balance >= 0 ? -1 : foodStores / -balance;
	}

	public double pantryCheckpoint() {
		return pantryCheckpoint;
	}

	public void setPantryCheckpoint(double pantryCheckpoint) {
		this.pantryCheckpoint = Math.max(0, pantryCheckpoint);
	}

	public int blightDays() {
		return blightDays;
	}

	public void setBlightDays(int blightDays) {
		this.blightDays = Math.max(0, blightDays);
	}

	public boolean isBlighted() {
		return blightDays > 0;
	}

	public double buildProgress() {
		return buildProgress;
	}

	public void setBuildProgress(double buildProgress) {
		this.buildProgress = Math.max(0, buildProgress);
	}

	/** Standing defense contributed by named residents' jobs - guards, mostly. Recomputed daily. */
	public double garrison() {
		return garrison;
	}

	public double foodProduction() {
		return foodProduction;
	}

	public double foodUpkeep() {
		return foodUpkeep;
	}

	/** Called by {@link VillageEconomy#recompute}; not for general use. */
	void setDerived(double garrison, double foodProduction, double foodUpkeep) {
		this.garrison = Math.max(0, garrison);
		this.foodProduction = Math.max(0, foodProduction);
		this.foodUpkeep = Math.max(0, foodUpkeep);
	}

	public List<VillageBuilding> buildings() {
		return buildings;
	}

	/** The first placed building of a type, or null. */
	public VillageBuilding building(BuildingType type) {
		for (VillageBuilding building : buildings) {
			if (building.type() == type)
				return building;
		}
		return null;
	}

	public int countBuildings(BuildingType type) {
		int count = 0;
		for (VillageBuilding building : buildings) {
			if (building.type() == type)
				count++;
		}
		return count;
	}

	/** Every placed building that holds food. These, and only these, are where the village's food
	 *  is - see {@link VillagePantry}. */
	public List<VillageBuilding> pantries() {
		List<VillageBuilding> found = new ArrayList<>();
		for (VillageBuilding building : buildings) {
			if (building.isUsableStore())
				found.add(building);
		}
		return found;
	}

	public boolean hasPantry() {
		for (VillageBuilding building : buildings) {
			if (building.isUsableStore())
				return true;
		}
		return false;
	}

	// The building contributions, summed. Kept as methods rather than stored fields so putting up
	// or losing a building takes effect immediately and can't drift out of sync with the list.
	public int buildingFortification() {
		int total = 0;
		for (VillageBuilding building : buildings) {
			total += building.type().fortification();
		}
		return total;
	}

	public double buildingGarrison() {
		return sum(BuildingType::garrison);
	}

	public double buildingFoodProduction() {
		return sum(BuildingType::foodProduction);
	}

	public double buildingWardUpkeep() {
		return sum(BuildingType::wardUpkeep);
	}

	public double buildingMorale() {
		return sum(BuildingType::morale);
	}

	public double buildingProsperity() {
		return sum(BuildingType::prosperity);
	}

	/** How many people the village has roofs for. Growth past this is slow and unhappy. */
	public int housing() {
		int total = 0;
		for (VillageBuilding building : buildings) {
			total += building.type().housing();
		}
		return total;
	}

	/** How much likelier this village's buildings make an event. A church doubles the odds of a
	 *  whitelighter passing through; two churches quadruple them. */
	public double buildingEventFavor(VillageEvent event) {
		double multiplier = 1.0;
		for (VillageBuilding building : buildings) {
			if (building.type().favors().contains(event))
				multiplier *= BuildingType.FAVOR_MULTIPLIER;
		}
		return multiplier;
	}

	/** What fraction of an event's threat gets through this village's buildings. A well against a
	 *  plague, a watchtower against a raid. */
	public double buildingThreatFraction(VillageEvent event) {
		double fraction = 1.0;
		for (VillageBuilding building : buildings) {
			if (building.type().resists().contains(event))
				fraction *= BuildingType.RESIST_FACTOR;
		}
		return Math.max(BuildingType.MIN_THREAT_FRACTION, fraction);
	}

	private double sum(java.util.function.ToDoubleFunction<BuildingType> field) {
		double total = 0;
		for (VillageBuilding building : buildings) {
			total += field.applyAsDouble(building.type());
		}
		return total;
	}

	public Set<String> residents() {
		return residents;
	}

	public Set<String> captives() {
		return captives;
	}

	public Map<String, Integer> relations() {
		return relations;
	}

	public Set<String> flags() {
		return flags;
	}

	public List<LogEntry> log() {
		return log;
	}

	public boolean isAbandoned() {
		return population <= 0;
	}

	public int relationTo(String otherVillageId) {
		return relations.getOrDefault(otherVillageId, 0);
	}

	public void setRelation(String otherVillageId, int value) {
		relations.put(otherVillageId, clampInt(value, -100, 100));
	}

	public void adjustRelation(String otherVillageId, int delta) {
		setRelation(otherVillageId, relationTo(otherVillageId) + delta);
	}

	public void addLogEntry(LogEntry entry) {
		log.add(entry);
		while (log.size() > LOG_LIMIT) {
			log.remove(0);
		}
	}

	/** The most recent history lines, newest first - what to print in a chat readout. */
	public List<LogEntry> recentLog(int count) {
		List<LogEntry> recent = new ArrayList<>();
		for (int i = log.size() - 1; i >= 0 && recent.size() < count; i--) {
			recent.add(log.get(i));
		}
		return recent;
	}

	/**
	 * How hard this village is to hurt with a given kind of threat.
	 *
	 * <p>Militia and walls carry PHYSICAL, wards carry MAGICAL, morale and prosperity carry SOCIAL,
	 * and everything is then scaled by morale and the faction's bias. The floor of 1 keeps a
	 * wrecked village from dividing by zero its way into infinite defeat multipliers.
	 */
	public double defenseAgainst(ThreatType threat) {
		double raw = switch (threat) {
			// Garrison is the professional watch - named residents doing the job. The militia term
			// is the untrained levy behind them. A village with real guards is a different problem
			// to a village with a lot of frightened farmers holding spears.
			case PHYSICAL -> garrison + militia * 3.0 + (fortification + buildingFortification()) * 4.0 + wards * 1.0 + population * 0.25;
			case MAGICAL -> wards * 6.0 + garrison * 0.15 + militia * 0.5 + (fortification + buildingFortification()) * 0.5 + population * 0.1;
			// Full stores are what stop a village taking a bad offer, and empty ones are why it does.
			case SOCIAL -> prosperity * 0.4 + morale * 25.0 + population * 0.5 + wards * 1.5 + Math.min(20.0, foodStores * 0.3);
			case NONE -> 0.0;
		};
		double bias = switch (threat) {
			case PHYSICAL -> faction.physicalBias();
			case MAGICAL -> faction.magicalBias();
			case SOCIAL, NONE -> 1.0;
		};
		// Morale is already inside the SOCIAL term; applying it again there would double-count.
		double moraleScale = threat == ThreatType.SOCIAL ? 1.0 : 0.4 + 0.6 * morale;
		return Math.max(1.0, raw * bias * moraleScale);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("village_id", villageId);
		json.addProperty("name", name);
		json.addProperty("faction", faction.name());
		json.add("center", center.toJson());
		json.addProperty("radius", radius);
		json.addProperty("founded_day", foundedDay);
		json.addProperty("last_simulated_day", lastSimulatedDay);
		json.addProperty("population", population);
		JsonObject stats = new JsonObject();
		stats.addProperty("militia", militia);
		stats.addProperty("fortification", fortification);
		stats.addProperty("wards", wards);
		stats.addProperty("morale", morale);
		stats.addProperty("prosperity", prosperity);
		stats.addProperty("food_stores", foodStores);
		stats.addProperty("build_progress", buildProgress);
		stats.addProperty("blight_days", blightDays);
		stats.addProperty("pantry_checkpoint", pantryCheckpoint);
		json.add("stats", stats);
		JsonObject derived = new JsonObject();
		derived.addProperty("garrison", garrison);
		derived.addProperty("food_production", foodProduction);
		derived.addProperty("food_upkeep", foodUpkeep);
		json.add("derived", derived);
		JsonArray buildingArray = new JsonArray();
		for (VillageBuilding building : buildings) {
			buildingArray.add(building.toJson());
		}
		json.add("buildings", buildingArray);
		json.add("residents", toArray(residents));
		json.add("captives", toArray(captives));
		JsonObject relationJson = new JsonObject();
		relations.forEach(relationJson::addProperty);
		json.add("relations", relationJson);
		json.add("flags", toArray(flags));
		JsonArray logArray = new JsonArray();
		for (LogEntry entry : log) {
			logArray.add(entry.toJson());
		}
		json.add("log", logArray);
		return json;
	}

	public static VillageRecord fromJson(JsonObject json) {
		VillageRecord village = new VillageRecord(json.get("village_id").getAsString(), json.has("name") ? json.get("name").getAsString() : "Unnamed",
				VillageFaction.parse(json.has("faction") ? json.get("faction").getAsString() : null));
		if (json.has("center"))
			village.center = WorldPoint.fromJson(json.getAsJsonObject("center"));
		if (json.has("radius"))
			village.radius = json.get("radius").getAsInt();
		if (json.has("founded_day"))
			village.foundedDay = json.get("founded_day").getAsLong();
		if (json.has("last_simulated_day"))
			village.lastSimulatedDay = json.get("last_simulated_day").getAsLong();
		if (json.has("population"))
			village.population = json.get("population").getAsInt();
		if (json.has("stats")) {
			JsonObject stats = json.getAsJsonObject("stats");
			if (stats.has("militia"))
				village.militia = stats.get("militia").getAsInt();
			if (stats.has("fortification"))
				village.fortification = stats.get("fortification").getAsInt();
			if (stats.has("wards"))
				village.wards = stats.get("wards").getAsDouble();
			if (stats.has("morale"))
				village.morale = stats.get("morale").getAsDouble();
			if (stats.has("prosperity"))
				village.prosperity = stats.get("prosperity").getAsDouble();
			if (stats.has("food_stores"))
				village.foodStores = stats.get("food_stores").getAsDouble();
			if (stats.has("build_progress"))
				village.buildProgress = stats.get("build_progress").getAsDouble();
			if (stats.has("blight_days"))
				village.blightDays = stats.get("blight_days").getAsInt();
			if (stats.has("pantry_checkpoint"))
				village.pantryCheckpoint = stats.get("pantry_checkpoint").getAsDouble();
		}
		// The "derived" block is informational; VillageEconomy#recompute rebuilds it from the roster
		// on load, so a hand-edited value there is overwritten rather than trusted.
		if (json.has("buildings")) {
			JsonArray buildingArray = json.getAsJsonArray("buildings");
			for (int i = 0; i < buildingArray.size(); i++) {
				village.buildings.add(VillageBuilding.fromJson(buildingArray.get(i).getAsJsonObject()));
			}
		}
		readInto(json, "residents", village.residents);
		readInto(json, "captives", village.captives);
		readInto(json, "flags", village.flags);
		if (json.has("relations")) {
			JsonObject relationJson = json.getAsJsonObject("relations");
			for (String key : relationJson.keySet()) {
				village.relations.put(key, relationJson.get(key).getAsInt());
			}
		}
		if (json.has("log")) {
			JsonArray logArray = json.getAsJsonArray("log");
			for (int i = 0; i < logArray.size(); i++) {
				village.log.add(LogEntry.fromJson(logArray.get(i).getAsJsonObject()));
			}
		}
		return village;
	}

	private static JsonArray toArray(Set<String> values) {
		JsonArray array = new JsonArray();
		for (String value : values) {
			array.add(value);
		}
		return array;
	}

	private static void readInto(JsonObject json, String key, Set<String> target) {
		if (!json.has(key))
			return;
		JsonArray array = json.getAsJsonArray(key);
		for (int i = 0; i < array.size(); i++) {
			target.add(array.get(i).getAsString());
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
