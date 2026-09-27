package net.spidrotech.duality.faction;

import net.spidrotech.duality.village.NpcRecord;
import net.spidrotech.duality.village.Villages;
import net.spidrotech.duality.DualityDatabaseManager;

import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Resolves what a character or NPC really is - reading the actual race/species systems
 * (charactercreation.RaceCatalog's {@code species_profiles[0].class} for players, NpcRecord#species
 * for NPCs) rather than inventing a parallel classification of its own.
 */
public final class MemberKinds {
	private MemberKinds() {
	}

	/** Works for either a character id or an npc id - whichever this member actually is. Always
	 *  the TRUE kind; see {@link #apparentKind} for the one case (a humanized vampire) where what
	 *  someone presents as differs from what they are. */
	public static MemberKind trueKind(MinecraftServer server, String memberId) {
		if (memberId == null)
			return MemberKind.OTHER;
		if (memberId.startsWith("char_"))
			return trueKindOfCharacter(server, memberId);
		if (memberId.startsWith("npc_"))
			return trueKindOfNpc(memberId);
		return MemberKind.OTHER;
	}

	private static MemberKind trueKindOfCharacter(MinecraftServer server, String characterId) {
		if (server == null)
			return MemberKind.OTHER;
		File charFile = new File(new File(server.getWorldPath(LevelResource.ROOT).toFile(), "duality_data/characters"), characterId + ".json");
		if (!charFile.exists())
			return MemberKind.HUMAN;
		try (FileReader reader = new FileReader(charFile)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			JsonArray profiles = json.has("species_profiles") ? json.getAsJsonArray("species_profiles") : null;
			// Matches DualityDatabaseManager#createNewCharacter: a human character is given no
			// species_profiles entry at all, so an empty array is the normal human case, not a
			// missing-data one.
			if (profiles == null || profiles.isEmpty())
				return MemberKind.HUMAN;
			JsonObject first = profiles.get(0).getAsJsonObject();
			String raceId = first.has("class") ? first.get("class").getAsString() : "";
			String subspeciesId = first.has("subspecies") ? first.get("subspecies").getAsString() : "";
			return fromRaceAndSubrace(raceId, subspeciesId);
		} catch (Exception e) {
			return MemberKind.HUMAN;
		}
	}

	/**
	 * A few lineages are what actually decide the kind, not the race they live under - Vampire is a
	 * Demon subrace but has to read as VAMPIRE (not DEMON) for every membership rule keyed on it to
	 * still work, and Wiccan is a Human subrace that has to read as WITCH. Everything else falls
	 * through to {@link #fromRaceId}, which also covers a save's older, pre-restructure characters
	 * whose "class" was itself "vampire", "witch" or "whitelighter".
	 */
	private static MemberKind fromRaceAndSubrace(String raceId, String subspeciesId) {
		String race = raceId == null ? "" : raceId.toLowerCase();
		String subspecies = subspeciesId == null ? "" : subspeciesId.toLowerCase();
		if (race.equals("demon") && (subspecies.equals("vampire") || subspecies.equals("vampiric_queen")))
			return MemberKind.VAMPIRE;
		if (race.equals("human") && subspecies.equals("wiccan"))
			return MemberKind.WITCH;
		if (race.equals("angelic"))
			return MemberKind.ANGEL;
		return fromRaceId(race);
	}

	private static MemberKind fromRaceId(String raceId) {
		if (raceId == null)
			return MemberKind.HUMAN;
		return switch (raceId.toLowerCase()) {
			case "human" -> MemberKind.HUMAN;
			case "witch" -> MemberKind.WITCH;
			case "vampire" -> MemberKind.VAMPIRE;
			case "demon" -> MemberKind.DEMON;
			case "whitelighter", "angelic" -> MemberKind.ANGEL;
			default -> MemberKind.OTHER;
		};
	}

	private static MemberKind trueKindOfNpc(String npcId) {
		if (!Villages.isReady())
			return MemberKind.OTHER;
		NpcRecord npc = Villages.findNpc(npcId);
		if (npc == null || npc.species() == null)
			return MemberKind.OTHER;
		return switch (npc.species().toLowerCase()) {
			case "human" -> MemberKind.HUMAN;
			case "witch" -> MemberKind.WITCH;
			// A Thrall is still a vampire underneath - just the mindless rank, not a different
			// species - see abilities.vampire.VampireRank's own class doc.
			case "vampire", "thrall" -> MemberKind.VAMPIRE;
			default -> MemberKind.OTHER;
		};
	}

	/**
	 * What this ONLINE player currently presents as - accounts for Vampire Mode's concealment
	 * (see abilities.vampire.VampireMode), the only disguise mechanic that exists today: a
	 * humanized vampire's apparent kind is HUMAN, not VAMPIRE. Every other kind has no equivalent
	 * disguise, so this is just their true kind.
	 *
	 * <p>Offline members and NPCs have no live entity to read a concealment toggle from, so there's
	 * no offline/NPC overload of this method on purpose - {@link #trueKind} is the only thing that
	 * makes sense for them.
	 */
	public static MemberKind apparentKind(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		MemberKind trueKind = trueKindOfCharacter(player.serverLevel().getServer(), characterId);
		if (trueKind == MemberKind.VAMPIRE && !net.spidrotech.duality.abilities.vampire.VampireMode.isActive(player))
			return MemberKind.HUMAN;
		return trueKind;
	}

	/**
	 * 0 (no concealment at all - caught on the very next roll, see FactionImpostorEvents) unless
	 * this online player's true kind has an active concealment mechanic. Only vampires (Vampire
	 * Mode) have one today - a demon or anyone else faking membership is passing purely on nobody
	 * having looked closely yet, not on any active effort of their own, so they're always at the
	 * baseline exposure rate. Add a case here if another kind ever gets its own disguise.
	 */
	public static double concealmentEffectiveness(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (trueKindOfCharacter(player.serverLevel().getServer(), characterId) == MemberKind.VAMPIRE)
			return net.spidrotech.duality.abilities.vampire.VampireMode.concealmentEffectiveness(player);
		return 0.0;
	}
}
