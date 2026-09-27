package net.spidrotech.duality.faction;

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
 * Everything the mod knows about one faction, persisted to duality_data/factions/&lt;id&gt;.json -
 * same "ledger first, place second" split as {@link net.spidrotech.duality.village.VillageRecord}.
 *
 * <p>A faction is an organization: a roster of members (players by character id, NPCs by npc id,
 * mixed freely) with a leader and, for anyone who wants the flavor, a title. It can own zero or
 * more settlements - see {@link #ownedVillageIds}, kept in sync with each
 * {@link net.spidrotech.duality.village.VillageRecord#ownerFactionId} by
 * {@link Factions#claimVillage}/{@link Factions#unclaimVillage} rather than by hand.
 *
 * <p>Deliberately NOT the same thing as {@link net.spidrotech.duality.village.VillageFaction} (that
 * enum is a settlement's fixed archetype - human/coven/vampire-clan/etc, picked once at founding
 * and never player-created). This class is the dynamic organization a player or an NPC forms,
 * which is free to own any number of settlements of any archetype: a vampire queen's court
 * (this class) might own several VAMPIRE_CLAN holds (that enum).
 */
public class FactionRecord {
	public record LogEntry(long day, String text) {
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("day", day);
			json.addProperty("text", text);
			return json;
		}

		public static LogEntry fromJson(JsonObject json) {
			return new LogEntry(json.has("day") ? json.get("day").getAsLong() : 0L, json.has("text") ? json.get("text").getAsString() : "");
		}
	}

	private static final int LOG_LIMIT = 60;

	private final String factionId;
	private String name;
	/** Who founded it - kept even if they later leave or are removed, as a matter of record. */
	private String founderId;
	private long foundedDay;
	/** Settlement the faction calls home, for display purposes only - membership in
	 *  {@link #ownedVillageIds} is what actually matters mechanically. Empty if unset. */
	private String homeVillageId = "";
	/** OPEN (no restriction) unless a leader declares otherwise - see Factions#setKind. */
	private FactionKind kind = FactionKind.OPEN;
	/** How good or evil this faction actually IS, on the same -LIMIT..LIMIT scale as
	 *  net.spidrotech.duality.village.WorldDualityState - a fact about the world, not something a
	 *  leader gets to declare (see {@link #alignment}). Setting a kind seeds this at that kind's
	 *  typical starting point (see FactionKind#startingDualityScore) so a vampire faction reads as
	 *  evil from the moment it exists, but nothing pins it there afterward - it's meant to drift as
	 *  the faction acts, once something calls {@link #adjustDualityScore}. */
	private double dualityScore = 0.0;

	private final Map<String, FactionMember> members = new LinkedHashMap<>();
	/** Members who don't actually qualify for this faction's kind but are passing for it - a demon
	 *  posing as human, say (see Factions#isEligible / #addPlayerMember). Kept entirely separate
	 *  from {@link #members}, both in memory and in the saved file: a fake member has no real say
	 *  in the faction (role/title on their entry here is cosmetic, never checked for permissions),
	 *  isn't counted by {@link #isMember}/{@link #size}, and - the point of the whole mechanic -
	 *  isn't who they're on record as being. Someone can fake more than one faction at once, and
	 *  can fake a faction while genuinely belonging to a different one; see Factions#recheckMembership
	 *  for how membership moves between this and {@link #members} as a member's race changes, and
	 *  FactionImpostorEvents for how a fake membership ends on its own (being found out) rather
	 *  than the faction ever having to notice on its own. */
	private final Map<String, FactionMember> fakeMembers = new LinkedHashMap<>();
	private final Set<String> ownedVillageIds = new LinkedHashSet<>();
	/** factionId -> -100 (at war) .. 100 (allied), same scale and meaning as
	 *  VillageRecord#relations. */
	private final Map<String, Integer> relations = new LinkedHashMap<>();
	private final Set<String> flags = new LinkedHashSet<>();
	private final List<LogEntry> log = new ArrayList<>();

	public FactionRecord(String factionId, String name, String founderId, long foundedDay) {
		this.factionId = factionId;
		this.name = name;
		this.founderId = founderId;
		this.foundedDay = foundedDay;
	}

	/** Mints a faction with founderId as its sole member and leader. */
	public static FactionRecord create(String name, String founderId, long day) {
		FactionRecord faction = new FactionRecord("fac_" + UUID.randomUUID().toString().substring(0, 8), name, founderId, day);
		faction.members.put(founderId, new FactionMember(founderId, FactionRole.LEADER, "", day));
		faction.addLogEntry(day, name + " was founded.");
		return faction;
	}

	public String factionId() {
		return factionId;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String founderId() {
		return founderId;
	}

	public long foundedDay() {
		return foundedDay;
	}

	public String homeVillageId() {
		return homeVillageId;
	}

	public void setHomeVillageId(String homeVillageId) {
		this.homeVillageId = homeVillageId == null ? "" : homeVillageId;
	}

	public FactionKind kind() {
		return kind;
	}

	/** Package-private: go through Factions#setKind, which also seeds dualityScore - calling this
	 *  directly leaves the score at whatever it already was. */
	void setKindRaw(FactionKind kind) {
		this.kind = kind == null ? FactionKind.OPEN : kind;
	}

	/** -LIMIT (wholly evil) .. +LIMIT (wholly good). */
	public static final double DUALITY_LIMIT = 1000.0;

	public double dualityScore() {
		return dualityScore;
	}

	/** -1..+1. */
	public double normalizedDuality() {
		return dualityScore / DUALITY_LIMIT;
	}

	/** GOOD/NEUTRAL/EVIL, read off {@link #normalizedDuality} - never stored directly, so it can
	 *  never drift out of step with the score itself. Same +-0.25 band WorldDualityState's own
	 *  descriptor() uses for "Darkening"/"Blessed", so a faction and the world read consistently. */
	public FactionAlignment alignment() {
		double n = normalizedDuality();
		if (n <= -0.25)
			return FactionAlignment.EVIL;
		if (n >= 0.25)
			return FactionAlignment.GOOD;
		return FactionAlignment.NEUTRAL;
	}

	/** A readable band for chat and UI - same wording and thresholds as
	 *  WorldDualityState#descriptor(), so a faction and the world read the same way. */
	public String dualityDescriptor() {
		double n = normalizedDuality();
		if (n <= -0.6)
			return "Damned";
		if (n <= -0.25)
			return "Darkening";
		if (n < 0.25)
			return "Balanced";
		if (n < 0.6)
			return "Blessed";
		return "Radiant";
	}

	/** Moves the score and returns the delta actually applied (smaller than asked for once pinned
	 *  at a limit) - same contract as WorldDualityState#addDualityScore. This, not a direct
	 *  setAlignment, is how a faction's alignment is meant to change: something it does (or that
	 *  happens to it) nudges the score, and alignment just reads whatever that adds up to. */
	public double adjustDualityScore(double delta) {
		double before = dualityScore;
		dualityScore = clampDuality(dualityScore + delta);
		return dualityScore - before;
	}

	/** For FactionKind's starting seed and JSON loading only - anything else changing a faction's
	 *  standing should read as an action, i.e. go through {@link #adjustDualityScore}. */
	void setDualityScoreRaw(double dualityScore) {
		this.dualityScore = clampDuality(dualityScore);
	}

	private static double clampDuality(double value) {
		return Math.max(-DUALITY_LIMIT, Math.min(DUALITY_LIMIT, value));
	}

	// ------------------------------------------------------------------------------- members
	public Map<String, FactionMember> members() {
		return members;
	}

	public FactionMember member(String memberId) {
		return memberId == null ? null : members.get(memberId);
	}

	public boolean isMember(String memberId) {
		return memberId != null && members.containsKey(memberId);
	}

	public int size() {
		return members.size();
	}

	/** The current leader, or null if the roster is somehow empty (shouldn't happen outside a
	 *  hand-edited file - a faction is deleted rather than left leaderless, see Factions#disband). */
	public FactionMember leader() {
		for (FactionMember member : members.values()) {
			if (member.role() == FactionRole.LEADER)
				return member;
		}
		return null;
	}

	public String leaderId() {
		FactionMember leader = leader();
		return leader == null ? "" : leader.memberId();
	}

	void addMember(FactionMember member) {
		members.put(member.memberId(), member);
	}

	// -------------------------------------------------------------------------- fake members
	public Map<String, FactionMember> fakeMembers() {
		return fakeMembers;
	}

	public FactionMember fakeMember(String memberId) {
		return memberId == null ? null : fakeMembers.get(memberId);
	}

	public boolean isFakeMember(String memberId) {
		return memberId != null && fakeMembers.containsKey(memberId);
	}

	void addFakeMember(FactionMember member) {
		fakeMembers.put(member.memberId(), member);
	}

	void removeFakeMember(String memberId) {
		fakeMembers.remove(memberId);
	}

	void removeMember(String memberId) {
		members.remove(memberId);
	}

	// ---------------------------------------------------------------------------- settlements
	public Set<String> ownedVillageIds() {
		return ownedVillageIds;
	}

	public boolean owns(String villageId) {
		return villageId != null && ownedVillageIds.contains(villageId);
	}

	// ------------------------------------------------------------------------------ relations
	public Map<String, Integer> relations() {
		return relations;
	}

	public int relationTo(String otherFactionId) {
		return relations.getOrDefault(otherFactionId, 0);
	}

	public void setRelation(String otherFactionId, int value) {
		relations.put(otherFactionId, clampInt(value, -100, 100));
	}

	public Set<String> flags() {
		return flags;
	}

	public List<LogEntry> log() {
		return log;
	}

	public void addLogEntry(long day, String text) {
		log.add(new LogEntry(day, text));
		while (log.size() > LOG_LIMIT) {
			log.remove(0);
		}
	}

	public List<LogEntry> recentLog(int count) {
		List<LogEntry> recent = new ArrayList<>();
		for (int i = log.size() - 1; i >= 0 && recent.size() < count; i--) {
			recent.add(log.get(i));
		}
		return recent;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("faction_id", factionId);
		json.addProperty("name", name);
		json.addProperty("founder_id", founderId);
		json.addProperty("founded_day", foundedDay);
		json.addProperty("home_village_id", homeVillageId);
		json.addProperty("kind", kind.name());
		json.addProperty("duality_score", dualityScore);
		JsonArray memberArray = new JsonArray();
		for (FactionMember member : members.values()) {
			memberArray.add(member.toJson());
		}
		json.add("members", memberArray);
		JsonArray fakeMemberArray = new JsonArray();
		for (FactionMember member : fakeMembers.values()) {
			fakeMemberArray.add(member.toJson());
		}
		json.add("fake_members", fakeMemberArray);
		JsonArray villageArray = new JsonArray();
		for (String villageId : ownedVillageIds) {
			villageArray.add(villageId);
		}
		json.add("owned_villages", villageArray);
		JsonObject relationJson = new JsonObject();
		relations.forEach(relationJson::addProperty);
		json.add("relations", relationJson);
		JsonArray flagArray = new JsonArray();
		for (String flag : flags) {
			flagArray.add(flag);
		}
		json.add("flags", flagArray);
		JsonArray logArray = new JsonArray();
		for (LogEntry entry : log) {
			logArray.add(entry.toJson());
		}
		json.add("log", logArray);
		return json;
	}

	public static FactionRecord fromJson(JsonObject json) {
		FactionRecord faction = new FactionRecord(json.get("faction_id").getAsString(), json.has("name") ? json.get("name").getAsString() : "Unnamed Faction",
				json.has("founder_id") ? json.get("founder_id").getAsString() : "", json.has("founded_day") ? json.get("founded_day").getAsLong() : 0L);
		if (json.has("home_village_id"))
			faction.homeVillageId = json.get("home_village_id").getAsString();
		if (json.has("kind"))
			faction.kind = FactionKind.parse(json.get("kind").getAsString());
		faction.dualityScore = json.has("duality_score") ? clampDuality(json.get("duality_score").getAsDouble()) : faction.kind.startingDualityScore();
		if (json.has("members")) {
			JsonArray memberArray = json.getAsJsonArray("members");
			for (int i = 0; i < memberArray.size(); i++) {
				FactionMember member = FactionMember.fromJson(memberArray.get(i).getAsJsonObject());
				faction.members.put(member.memberId(), member);
			}
		}
		if (json.has("fake_members")) {
			JsonArray fakeMemberArray = json.getAsJsonArray("fake_members");
			for (int i = 0; i < fakeMemberArray.size(); i++) {
				FactionMember member = FactionMember.fromJson(fakeMemberArray.get(i).getAsJsonObject());
				faction.fakeMembers.put(member.memberId(), member);
			}
		}
		if (json.has("owned_villages")) {
			JsonArray villageArray = json.getAsJsonArray("owned_villages");
			for (int i = 0; i < villageArray.size(); i++) {
				faction.ownedVillageIds.add(villageArray.get(i).getAsString());
			}
		}
		if (json.has("relations")) {
			JsonObject relationJson = json.getAsJsonObject("relations");
			for (String key : relationJson.keySet()) {
				faction.relations.put(key, relationJson.get(key).getAsInt());
			}
		}
		if (json.has("flags")) {
			JsonArray flagArray = json.getAsJsonArray("flags");
			for (int i = 0; i < flagArray.size(); i++) {
				faction.flags.add(flagArray.get(i).getAsString());
			}
		}
		if (json.has("log")) {
			JsonArray logArray = json.getAsJsonArray("log");
			for (int i = 0; i < logArray.size(); i++) {
				faction.log.add(LogEntry.fromJson(logArray.get(i).getAsJsonObject()));
			}
		}
		return faction;
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
