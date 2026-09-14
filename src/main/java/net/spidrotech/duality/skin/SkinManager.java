package net.spidrotech.duality.skin;

import net.spidrotech.duality.DualityDatabaseManager;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ArrayList;

/**
 * Server-side owner of every player's appearance: their base loadout, their current disguise (if
 * shapeshifted), and their live temporary augmentations. Single authority - clients hold a
 * read-only mirror (see client.ClientSkinState) that only ever changes because a packet from
 * here told it to.
 *
 * THREE PIECES OF STATE, DELIBERATELY SEPARATE:
 *   loadouts   - the player's own saved look. Persisted. Edited only by the builder GUI.
 *   disguises  - a COPY of somebody else's loadout, or absent. Never persisted; a shapeshift
 *                doesn't survive a relog. Because it holds a SkinLoadout and nothing else, a
 *                disguise structurally cannot carry the source player's augmentations.
 *   tempMods   - augmentations, keyed to the player wearing the body, NOT to the appearance.
 *                They therefore survive a shapeshift and keep applying to whatever the player
 *                currently looks like, which is the behavior you want: a vampire who takes
 *                someone else's form is still a vampire.
 *
 * VERSIONING: every mutation bumps a per-player counter that ships with the sync packet. The
 * client's composite cache compares that number and nothing else to decide whether to rebuild
 * (see client.ClientSkinCache) - which is what keeps a fully static skin at zero work per frame
 * instead of rehashing a loadout every time something wants to draw the player.
 *
 * BROADCAST SCOPE: sendToPlayersTrackingEntityAndSelf, so someone across the world doesn't pay
 * for your eye color changing. A player entering tracking range gets a fresh sync from
 * SkinNetwork's start-tracking hook.
 */
public final class SkinManager {
	private static final SkinManager INSTANCE = new SkinManager();

	public static SkinManager get() {
		return INSTANCE;
	}

	private final Map<UUID, SkinLoadout> loadouts = new ConcurrentHashMap<>();
	private final Map<UUID, SkinLoadout> disguises = new ConcurrentHashMap<>();
	private final Map<UUID, List<TempSkinModification>> tempMods = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> versions = new ConcurrentHashMap<>();

	private SkinManager() {
	}

	// ================================================================== base loadout
	public SkinLoadout loadoutOf(UUID playerId) {
		return loadouts.getOrDefault(playerId, SkinLoadout.EMPTY);
	}

	/** The loadout actually DRAWN for this player - the disguise if one is active, otherwise
	 *  their own. Temp mods are not part of this and are sent alongside it. */
	public SkinLoadout renderedLoadoutOf(UUID playerId) {
		SkinLoadout disguise = disguises.get(playerId);
		return disguise != null ? disguise : loadoutOf(playerId);
	}

	public void setLoadout(ServerPlayer player, SkinLoadout loadout) {
		loadouts.put(player.getUUID(), loadout);
		// Loadout belongs to the CHARACTER, not the account, so a player swapping characters
		// swaps appearance - matching how waypoints are already scoped. A player with no active
		// character (mid-creation) just keeps the loadout in memory until one exists.
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (!characterId.isEmpty()) {
			DualityDatabaseManager.setSkinLoadout(player, characterId, loadout);
		}
		syncAll(player);
	}

	// ================================================================== shapeshifting
	/** Copies ONLY the source's base loadout. The source's own augmentations are not read here
	 *  and could not be copied even by mistake - see class doc. */
	public void setDisguise(ServerPlayer player, UUID sourcePlayerId) {
		disguises.put(player.getUUID(), loadoutOf(sourcePlayerId));
		syncAll(player);
	}

	public void clearDisguise(ServerPlayer player) {
		disguises.remove(player.getUUID());
		syncAll(player);
	}

	public boolean isDisguised(UUID playerId) {
		return disguises.containsKey(playerId);
	}

	// ================================================================== temp modifications
	public List<TempSkinModification> tempModifications(ServerPlayer player) {
		return tempModificationsOf(player.getUUID());
	}

	public List<TempSkinModification> tempModificationsOf(UUID playerId) {
		return List.copyOf(tempMods.getOrDefault(playerId, List.of()));
	}

	public void addTempModification(ServerPlayer player, TempSkinModification mod) {
		tempMods.computeIfAbsent(player.getUUID(), k -> new CopyOnWriteArrayList<>()).add(mod);
		syncAll(player);
	}

	public void removeTempModifications(ServerPlayer player, String key) {
		List<TempSkinModification> list = tempMods.get(player.getUUID());
		if (list == null || !list.removeIf(mod -> mod.key().equals(key)))
			return; // nothing carried that key - don't burn a version bump or a packet on it
		syncAll(player);
	}

	public void clearTempModifications(ServerPlayer player) {
		List<TempSkinModification> list = tempMods.remove(player.getUUID());
		if (list == null || list.isEmpty())
			return;
		syncAll(player);
	}

	// ================================================================== lifecycle / sync
	public int versionOf(UUID playerId) {
		return versions.getOrDefault(playerId, 0);
	}

	/** Bumps this player's version and pushes the full appearance to everyone tracking them. */
	public void syncAll(ServerPlayer player) {
		UUID id = player.getUUID();
		versions.merge(id, 1, Integer::sum);
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, appearancePacketFor(player));
	}

	public SkinNetwork.SyncAppearancePayload appearancePacketFor(ServerPlayer player) {
		UUID id = player.getUUID();
		return new SkinNetwork.SyncAppearancePayload(id, versionOf(id), renderedLoadoutOf(id), tempModificationsOf(id));
	}

	/** Called on login. Loads persisted state and hands the joiner everyone else's appearance. */
	public void onPlayerJoin(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		SkinLoadout saved = characterId.isEmpty() ? SkinLoadout.EMPTY : DualityDatabaseManager.getSkinLoadout(player, characterId);
		loadouts.put(player.getUUID(), saved);
		SkinUnlocks.get().load(player);
		PacketDistributor.sendToPlayer(player, new SkinNetwork.SyncSkinCatalogPayload(new ArrayList<>(SkinPartCatalog.all())));
		for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
			PacketDistributor.sendToPlayer(player, appearancePacketFor(other));
		}
		syncAll(player);
	}

	/**
	 * Call after the player's active character changes (see DualityDatabaseManager#setActiveCharacter).
	 * Reloads the appearance and unlocks for the NOW-active character and clears any temporary
	 * augmentations, since those were tied to the previous character's session. This is what
	 * makes a character swap also swap the visible skin.
	 */
	public void onActiveCharacterChanged(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		SkinLoadout saved = characterId.isEmpty() ? SkinLoadout.EMPTY : DualityDatabaseManager.getSkinLoadout(player, characterId);
		loadouts.put(player.getUUID(), saved);
		disguises.remove(player.getUUID());
		tempMods.remove(player.getUUID());
		SkinUnlocks.get().load(player);
		syncAll(player);
	}

	/** Disguises and augmentations are per-session; the persisted loadout is not touched. */
	public void onPlayerLeave(ServerPlayer player) {
		disguises.remove(player.getUUID());
		tempMods.remove(player.getUUID());
	}

	@Nullable
	public SkinLoadout rawLoadoutOrNull(UUID playerId) {
		return loadouts.get(playerId);
	}
}
