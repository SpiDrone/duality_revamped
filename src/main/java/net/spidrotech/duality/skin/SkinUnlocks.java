package net.spidrotech.duality.skin;

import net.spidrotech.duality.DualityDatabaseManager;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has earned what. Grants are by UNLOCK ID, not by part id - see SkinOwnershipRules for why:
 * one grant of "duality:vampire_line" hands over every part tagged with it, so a progression
 * step stays one call however many cosmetics you later attach to it. Adding a new hairstyle to
 * an existing unlock retroactively gives it to everyone who already earned that unlock, with no
 * migration.
 *
 * SERVER-SIDE ONLY and never synced as such. The client is told which parts to DRAW (via
 * SkinNetwork's appearance sync) and, for the builder GUI, which ones it may offer - but the
 * grant set itself is checked here, on the server, every time an equip request arrives. A client
 * that lies about owning something gets its request dropped by SkinNetwork#ownsPart.
 *
 * Grants are per-character rather than per-account, matching how waypoints are already scoped:
 * a second character starts from nothing, which is usually the point of having characters. The
 * in-memory map here is keyed by player UUID and mirrors ONLY the active character's set - it's
 * reloaded from that character's sheet on join and on character switch (see load()), and every
 * mutation writes straight through to the active character's sheet, so the map never disagrees
 * with disk for the character currently being played.
 */
public final class SkinUnlocks {
	private static final SkinUnlocks INSTANCE = new SkinUnlocks();

	public static SkinUnlocks get() {
		return INSTANCE;
	}

	// Player UUID -> the ACTIVE character's unlock set. Reloaded wholesale on character switch,
	// so it always reflects whoever the player is currently playing, not an accumulation across
	// characters.
	private final Map<UUID, Set<String>> grants = new ConcurrentHashMap<>();

	private SkinUnlocks() {
	}

	/** The ownership question, in one place. Free parts short-circuit; a locked part with no
	 *  unlock id is unobtainable rather than free, which is the safe reading of a rules file
	 *  that says "free": false and forgets the id. */
	public boolean owns(ServerPlayer player, SkinPart part) {
		if (part.free())
			return true;
		String unlockId = part.unlock().orElse(null);
		if (unlockId == null)
			return false;
		return grantsFor(player.getUUID()).contains(unlockId);
	}

	public Set<String> grantsFor(UUID playerId) {
		return grants.getOrDefault(playerId, Set.of());
	}

	public boolean grant(ServerPlayer player, String unlockId) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId.isEmpty())
			return false; // no active character to attach the unlock to - nothing to grant against
		Set<String> set = grants.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
		if (!set.add(unlockId))
			return false;
		DualityDatabaseManager.setSkinUnlocks(player, characterId, set);
		return true;
	}

	public boolean revoke(ServerPlayer player, String unlockId) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId.isEmpty())
			return false;
		Set<String> set = grants.get(player.getUUID());
		if (set == null || !set.remove(unlockId))
			return false;
		// Deliberately NOT unequipping parts the player loses access to. Revoking is rare and
		// usually an admin correction; silently stripping someone's outfit as a side effect is
		// worse than letting them keep wearing it until they next change it. Add the stripping
		// pass here if your progression actually takes things away.
		DualityDatabaseManager.setSkinUnlocks(player, characterId, set);
		return true;
	}

	/** Loads the ACTIVE character's unlock set from disk into the in-memory mirror, replacing
	 *  whatever was there - call on join and after a character switch (SkinManager does both).
	 *  A player with no active character gets an empty set rather than keeping the previous
	 *  character's. */
	public void load(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		Set<String> set = ConcurrentHashMap.newKeySet();
		if (!characterId.isEmpty()) {
			set.addAll(DualityDatabaseManager.getSkinUnlocks(player, characterId));
		}
		grants.put(player.getUUID(), set);
	}

	/** Every unlock id referenced by any part in the catalog - for command tab completion, and
	 *  for a "collection progress" screen later. */
	public static Set<String> knownUnlockIds() {
		Set<String> ids = new LinkedHashSet<>();
		for (SkinPart part : SkinPartCatalog.all()) {
			part.unlock().ifPresent(ids::add);
		}
		return ids;
	}
}
