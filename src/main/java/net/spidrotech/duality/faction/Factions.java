package net.spidrotech.duality.faction;

import net.spidrotech.duality.village.VillageRecord;
import net.spidrotech.duality.village.Villages;
import net.spidrotech.duality.DualityDatabaseManager;

import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The front door to the faction system - same role {@link Villages} plays for settlements, and
 * deliberately styled after it. Player-facing operations (create/invite/claim/...) live here
 * rather than in the command class, so the same rules apply whether a command, another system, or
 * a future NPC AI is the one calling them.
 *
 * <pre>
 *   Factions.createForPlayer(player, "The Ashmere Compact");
 *   Factions.claimVillage(faction, village);                 // now "one faction, many towns" is true
 *   Factions.factionOf(someCharId);                          // what faction is this character in
 * </pre>
 *
 * <p>Vampires are meant to run through this same system unchanged (a Queen's court is just a
 * faction whose leader happens to also hold VampireRank.QUEEN, and whose members happen to be
 * other vampires) - nothing here special-cases them, on purpose. Nothing here makes NPCs found
 * factions on their own initiative yet either; {@link #createForNpc} exists so that behavior has
 * somewhere to call into once it's built, but nothing calls it automatically today.
 */
public final class Factions {
	private static MinecraftServer server;
	private static FactionStore store;

	private Factions() {
	}

	// ------------------------------------------------------------------------------ lifecycle
	public static void attach(MinecraftServer startingServer) {
		server = startingServer;
		File dualityDir = new File(startingServer.getWorldPath(LevelResource.ROOT).toFile(), "duality_data");
		store = new FactionStore(dualityDir).load();
	}

	public static void detach() {
		if (store != null)
			store.saveAll();
		server = null;
		store = null;
	}

	public static boolean isReady() {
		return store != null;
	}

	public static FactionStore store() {
		return store;
	}

	public static long currentDay() {
		if (server == null || server.overworld() == null)
			return 0;
		return server.overworld().getDayTime() / 24000L;
	}

	// ---------------------------------------------------------------------------- membership
	public enum CreateResult {
		OK, NAME_TAKEN, NO_ACTIVE_CHARACTER, ALREADY_IN_A_FACTION
	}

	public record CreateOutcome(CreateResult result, FactionRecord faction) {
		public boolean ok() {
			return result == CreateResult.OK;
		}
	}

	/** The generic path any founder (player character or NPC) goes through. */
	public static CreateOutcome create(String founderId, String name) {
		if (!isReady())
			return new CreateOutcome(CreateResult.NAME_TAKEN, null); // no server attached - nothing sensible to do
		if (store.nameIsTaken(name))
			return new CreateOutcome(CreateResult.NAME_TAKEN, null);
		if (store.factionOf(founderId) != null)
			return new CreateOutcome(CreateResult.ALREADY_IN_A_FACTION, null);
		FactionRecord faction = FactionRecord.create(name, founderId, currentDay());
		store.add(faction);
		return new CreateOutcome(CreateResult.OK, faction);
	}

	/** Resolves the player's ACTIVE character (see DualityDatabaseManager) as the founder - a
	 *  player with no active character (mid character-creation) can't found a faction. */
	public static CreateOutcome createForPlayer(ServerPlayer player, String name) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId == null || characterId.isEmpty())
			return new CreateOutcome(CreateResult.NO_ACTIVE_CHARACTER, null);
		return create(characterId, name);
	}

	public static CreateOutcome createForNpc(String npcId, String name) {
		return create(npcId, name);
	}

	public enum MemberResult {
		OK, ALREADY_IN_A_FACTION, NOT_A_MEMBER, LAST_LEADER, NO_ACTIVE_CHARACTER, KIND_NOT_ALLOWED
	}

	/**
	 * Whether apparentKind may join a faction of this kind and alignment - the composed rule the
	 * user asked for. {@link FactionKind#accepts} alone covers "a vampire can only be in a vampire
	 * faction (or pass as human into a human one)" and "vampires can't join other demon factions";
	 * the alignment half here separately covers "angels won't join a demon faction or a low
	 * (evil) one", which has to be a second check since an OPEN or HUMAN-kind faction can still be
	 * EVIL-aligned (bandits) without being VAMPIRE- or DEMON-kind at all.
	 */
	public static boolean isEligible(FactionRecord faction, MemberKind apparentKind) {
		if (!faction.kind().accepts(apparentKind))
			return false;
		return !(apparentKind == MemberKind.ANGEL && faction.alignment() == FactionAlignment.EVIL);
	}

	/** For NPCs and admin use - checks the member's TRUE kind. There's no fake-join path here: a
	 *  disguise only exists for an online player with a live entity to read Vampire Mode (or
	 *  whatever else) off of - see {@link #addPlayerMember} for the one path that has one. */
	public static MemberResult addMember(FactionRecord faction, String memberId, FactionRole role, String title) {
		if (!isEligible(faction, MemberKinds.trueKind(server, memberId)))
			return MemberResult.KIND_NOT_ALLOWED;
		if (store.factionOf(memberId) != null)
			return MemberResult.ALREADY_IN_A_FACTION;
		addMemberInternal(faction, memberId, role, title);
		return MemberResult.OK;
	}

	/**
	 * Tries the player's TRUE kind first - a real member if that's eligible. Only if that fails
	 * does their APPARENT kind get a look, and if THAT'S eligible they become a fake member
	 * instead: someone who doesn't really belong but is passing for it, filed separately (see
	 * FactionRecord#fakeMembers) and free to also genuinely belong to a different faction at the
	 * same time - a demon can fake their way into a human faction while remaining a real member of
	 * their own demon one. A fake join never checks "already in a faction" the way a real one does,
	 * since it isn't one.
	 *
	 * <p>Nothing here ever kicks a fake member out on its own - see FactionImpostorEvents for how a
	 * fake membership actually ends (being found out, on a timescale that depends on whether their
	 * true kind has any concealment mechanic at all).
	 */
	public static MemberResult addPlayerMember(FactionRecord faction, ServerPlayer player, FactionRole role, String title) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId == null || characterId.isEmpty())
			return MemberResult.NO_ACTIVE_CHARACTER;
		if (isEligible(faction, MemberKinds.trueKind(server, characterId))) {
			if (store.factionOf(characterId) != null)
				return MemberResult.ALREADY_IN_A_FACTION;
			addMemberInternal(faction, characterId, role, title);
			return MemberResult.OK;
		}
		if (!isEligible(faction, MemberKinds.apparentKind(player)))
			return MemberResult.KIND_NOT_ALLOWED;
		if (faction.isFakeMember(characterId))
			return MemberResult.ALREADY_IN_A_FACTION;
		faction.addFakeMember(new FactionMember(characterId, FactionRole.MEMBER, title, currentDay()));
		faction.addLogEntry(currentDay(), displayName(characterId) + " has quietly ingratiated themselves here.");
		store.save(faction);
		return MemberResult.OK;
	}

	private static void addMemberInternal(FactionRecord faction, String memberId, FactionRole role, String title) {
		faction.addMember(new FactionMember(memberId, role == null ? FactionRole.MEMBER : role, title, currentDay()));
		faction.addLogEntry(currentDay(), displayName(memberId) + " joined.");
		store.save(faction);
	}

	/** For a leader who's caught a fake member themselves (a command should call this, not scan
	 *  fakeMembers directly) - the automatic version of this is FactionImpostorEvents. */
	public static boolean removeFakeMember(FactionRecord faction, String memberId) {
		if (!faction.isFakeMember(memberId))
			return false;
		faction.removeFakeMember(memberId);
		faction.addLogEntry(currentDay(), displayName(memberId) + " was caught faking membership here and cast out.");
		store.save(faction);
		return true;
	}

	/** Every faction memberId is currently faking membership in, real membership elsewhere (or
	 *  nowhere) notwithstanding. */
	public static java.util.List<FactionRecord> fakeFactionsOf(String memberId) {
		java.util.List<FactionRecord> found = new java.util.ArrayList<>();
		if (!isReady() || memberId == null)
			return found;
		for (FactionRecord faction : store.factions()) {
			if (faction.isFakeMember(memberId))
				found.add(faction);
		}
		return found;
	}

	/** Refuses to remove a sole leader with other members still on the roster - transfer
	 *  leadership first (see {@link #transferLeadership}), or {@link #disband} if they're the last
	 *  one left. */
	public static MemberResult removeMember(FactionRecord faction, String memberId) {
		FactionMember member = faction.member(memberId);
		if (member == null)
			return MemberResult.NOT_A_MEMBER;
		if (member.role() == FactionRole.LEADER && faction.size() > 1)
			return MemberResult.LAST_LEADER;
		faction.removeMember(memberId);
		faction.addLogEntry(currentDay(), displayName(memberId) + " left.");
		store.save(faction);
		return MemberResult.OK;
	}

	public static MemberResult setRole(FactionRecord faction, String memberId, FactionRole role) {
		FactionMember member = faction.member(memberId);
		if (member == null)
			return MemberResult.NOT_A_MEMBER;
		member.setRole(role);
		store.save(faction);
		return MemberResult.OK;
	}

	public static MemberResult setTitle(FactionRecord faction, String memberId, String title) {
		FactionMember member = faction.member(memberId);
		if (member == null)
			return MemberResult.NOT_A_MEMBER;
		member.setTitle(title);
		store.save(faction);
		return MemberResult.OK;
	}

	/** Hands leadership to another member, demoting the outgoing leader to OFFICER rather than
	 *  MEMBER - stepping down from running things isn't the same as stepping away from them. */
	public static MemberResult transferLeadership(FactionRecord faction, String newLeaderId) {
		FactionMember incoming = faction.member(newLeaderId);
		if (incoming == null)
			return MemberResult.NOT_A_MEMBER;
		FactionMember outgoing = faction.leader();
		if (outgoing != null && !outgoing.memberId().equals(newLeaderId))
			outgoing.setRole(FactionRole.OFFICER);
		incoming.setRole(FactionRole.LEADER);
		faction.addLogEntry(currentDay(), displayName(newLeaderId) + " is now the leader of " + faction.name() + ".");
		store.save(faction);
		return MemberResult.OK;
	}

	/** Unclaims every settlement first, so nothing is ever left owned by a faction id that no
	 *  longer resolves to anything. */
	public static void disband(FactionRecord faction) {
		if (Villages.isReady()) {
			for (String villageId : faction.ownedVillageIds()) {
				VillageRecord village = Villages.store().village(villageId);
				if (village == null)
					continue;
				village.setOwnerFactionId("");
				Villages.store().save(village);
			}
		}
		store.delete(faction.factionId());
	}

	// -------------------------------------------------------------------------------- kind
	/** Sets the kind and seeds dualityScore to that kind's usual starting point in one step - see
	 *  FactionKind#startingDualityScore. Alignment itself is never set directly (see
	 *  FactionRecord#alignment) - it's a fact the world reads off the faction's actions, not a
	 *  leader's declaration, which is why there's no setAlignment here. Use
	 *  {@link #adjustDualityScore} for whatever should actually move it afterward.
	 *
	 *  <p>Every current member (real and fake) is rechecked against the new kind immediately - see
	 *  {@link #recheckAllMembers}: nobody is kicked outright just because the rules changed under
	 *  them, but a real member who no longer qualifies is quietly refiled as a fake one (or, if
	 *  they can't even pass as that anymore, removed). */
	public static void setKind(FactionRecord faction, FactionKind kind) {
		faction.setKindRaw(kind);
		faction.setDualityScoreRaw(kind.startingDualityScore());
		store.save(faction);
		recheckAllMembers(faction);
	}

	/** How a faction's standing is actually meant to change - something it does, or that happens
	 *  to it, nudges the score, and alignment just reads whatever that adds up to. Nothing calls
	 *  this automatically yet (no faction-level events exist), so a faction's alignment today only
	 *  moves via its founding kind - this is here for whatever calls into it next. */
	public static double adjustDualityScore(FactionRecord faction, double delta) {
		double applied = faction.adjustDualityScore(delta);
		store.save(faction);
		return applied;
	}

	/**
	 * The one place membership eligibility is re-checked after the fact - not a ticker sweeping
	 * every faction, but called directly at the two moments that could actually invalidate someone:
	 * a character or NPC's race changing (see charactercreation.CharacterCreation#applyToPlayer and
	 * the village package's NpcRecord#setSpecies call sites), and a faction's own kind changing (see
	 * {@link #setKind}). Safe to call redundantly or speculatively - nothing here does anything if
	 * membership is still valid.
	 *
	 * <p>Never kicks anyone the instant they stop truly qualifying. If their real faction no longer
	 * accepts their TRUE kind but still accepts their APPARENT one, they're quietly refiled as a
	 * fake member instead of a real one (see FactionRecord#fakeMembers) - "found out" is a separate
	 * event (FactionImpostorEvents), not this one. Only a member who can't even pass as eligible any
	 * more (apparent kind fails too - the normal case for anyone without a concealment mechanic,
     *  since their apparent kind is just their true kind) is actually removed here, since there's no
	 *  cover story left to preserve. Existing fake memberships are re-examined the same way: a
	 *  disguise that stops working (apparent kind no longer fits) ends outright, and one that turns
	 *  out to be unnecessary (their true kind now fits after all) is promoted to real - unless they
	 *  already have a real faction elsewhere, in which case it's just dropped.
	 */
	public static void onRaceChanged(String memberId) {
		recheckMembership(memberId);
	}

	/** Re-runs {@link #onRaceChanged}'s logic for every current real and fake member of a faction -
	 *  what {@link #setKind} calls after changing the rules out from under them. */
	private static void recheckAllMembers(FactionRecord faction) {
		for (String memberId : new java.util.ArrayList<>(faction.members().keySet())) {
			recheckMembership(memberId);
		}
		for (String memberId : new java.util.ArrayList<>(faction.fakeMembers().keySet())) {
			recheckMembership(memberId);
		}
	}

	private static void recheckMembership(String memberId) {
		if (!isReady() || memberId == null)
			return;
		MemberKind trueKind = MemberKinds.trueKind(server, memberId);
		MemberKind apparentKind = bestApparentKind(memberId);
		FactionRecord real = store.factionOf(memberId);
		if (real != null) {
			if (isEligible(real, trueKind)) {
				// still legitimately belongs - nothing to do
			} else if (isEligible(real, apparentKind)) {
				real.removeMember(memberId);
				real.addFakeMember(new FactionMember(memberId, FactionRole.MEMBER, "", currentDay()));
				real.addLogEntry(currentDay(), displayName(memberId) + " isn't what they once were - quietly kept on, for now.");
				store.save(real);
			} else {
				real.removeMember(memberId);
				real.addLogEntry(currentDay(), displayName(memberId) + " no longer belongs here and was removed.");
				if (real.size() == 0)
					store.delete(real.factionId());
				else
					store.save(real);
			}
		}
		for (FactionRecord faction : new java.util.ArrayList<>(store.factions())) {
			if (faction == real || !faction.isFakeMember(memberId))
				continue;
			if (isEligible(faction, trueKind)) {
				faction.removeFakeMember(memberId);
				if (store.factionOf(memberId) == null) {
					faction.addMember(new FactionMember(memberId, FactionRole.MEMBER, "", currentDay()));
					faction.addLogEntry(currentDay(), displayName(memberId) + " no longer needs to hide what they are here.");
				} else {
					faction.addLogEntry(currentDay(), displayName(memberId) + " quietly stopped associating here.");
				}
				store.save(faction);
			} else if (!isEligible(faction, apparentKind)) {
				faction.removeFakeMember(memberId);
				faction.addLogEntry(currentDay(), displayName(memberId) + "'s cover here doesn't hold up any more.");
				store.save(faction);
			}
		}
	}

	/** MemberKinds#apparentKind needs a live ServerPlayer - this finds one if memberId happens to
	 *  be an online player's active character, and falls back to their true kind (the only option
	 *  for an offline player or an NPC) otherwise. */
	private static MemberKind bestApparentKind(String memberId) {
		if (server != null && memberId != null && memberId.startsWith("char_")) {
			for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
				if (memberId.equals(DualityDatabaseManager.getActiveCharacterId(candidate)))
					return MemberKinds.apparentKind(candidate);
			}
		}
		return MemberKinds.trueKind(server, memberId);
	}

	// -------------------------------------------------------------------------- relations
	/** Above this, two factions are considered allied for {@link FactionAlignment#canAlly}'s
	 *  purposes - good and evil are blocked from crossing it in either direction. */
	public static final int ALLY_THRESHOLD = 50;

	public enum RelationResult {
		OK, ALIGNMENT_BLOCKS_ALLIANCE
	}

	public static RelationResult setRelation(FactionRecord faction, String otherFactionId, int value) {
		FactionRecord other = store.faction(otherFactionId);
		if (other != null && value >= ALLY_THRESHOLD && !FactionAlignment.canAlly(faction.alignment(), other.alignment()))
			return RelationResult.ALIGNMENT_BLOCKS_ALLIANCE;
		faction.setRelation(otherFactionId, value);
		store.save(faction);
		return RelationResult.OK;
	}

	// -------------------------------------------------------------------------- settlements
	public enum ClaimResult {
		OK, ALREADY_CLAIMED, NOT_CLAIMED_BY_YOU
	}

	public static ClaimResult claimVillage(FactionRecord faction, VillageRecord village) {
		if (village.isClaimed())
			return ClaimResult.ALREADY_CLAIMED;
		village.setOwnerFactionId(faction.factionId());
		faction.ownedVillageIds().add(village.villageId());
		faction.addLogEntry(currentDay(), village.name() + " was claimed.");
		Villages.store().save(village);
		store.save(faction);
		return ClaimResult.OK;
	}

	public static ClaimResult unclaimVillage(FactionRecord faction, VillageRecord village) {
		if (!faction.owns(village.villageId()))
			return ClaimResult.NOT_CLAIMED_BY_YOU;
		village.setOwnerFactionId("");
		faction.ownedVillageIds().remove(village.villageId());
		faction.addLogEntry(currentDay(), village.name() + " was released.");
		Villages.store().save(village);
		store.save(faction);
		return ClaimResult.OK;
	}

	// ------------------------------------------------------------------------------- lookup
	public static FactionRecord factionOf(String memberId) {
		return isReady() ? store.factionOf(memberId) : null;
	}

	public static FactionRecord factionOfPlayer(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		return characterId == null || characterId.isEmpty() ? null : factionOf(characterId);
	}

	/** "char_..." resolves against a character sheet, "npc_..." against the village system's NPC
	 *  roster (if it's loaded), anything else is returned as-is. Never null - falls back to the raw
	 *  id so a broken reference shows up as an odd-looking name instead of an exception. */
	public static String displayName(String memberId) {
		if (memberId == null)
			return "someone";
		if (memberId.startsWith("char_") && server != null) {
			File charFile = new File(new File(server.getWorldPath(LevelResource.ROOT).toFile(), "duality_data/characters"), memberId + ".json");
			if (charFile.exists()) {
				try (FileReader reader = new FileReader(charFile)) {
					JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
					if (json.has("name"))
						return json.get("name").getAsString();
				} catch (Exception ignored) {
					// fall through to the raw id below
				}
			}
		} else if (memberId.startsWith("npc_") && Villages.isReady()) {
			var npc = Villages.findNpc(memberId);
			if (npc != null)
				return npc.name();
		}
		return memberId;
	}
}
