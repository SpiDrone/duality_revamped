package net.spidrotech.duality.skin.client;

import net.spidrotech.duality.skin.TempSkinModification;
import net.spidrotech.duality.skin.SkinLoadout;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only client mirror of SkinManager, one entry per player this client can currently see.
 * Written exclusively by SkinNetwork's SyncAppearancePayload handler; read by ClientSkinCache
 * and by the builder GUI's preview.
 *
 * VERSION is the whole point of this class. The composite cache never inspects a loadout to
 * decide whether to rebuild - it compares one int. A player standing still with a static skin
 * therefore costs a single integer comparison per render call, not a walk of their part list.
 *
 * The mirror is deliberately NOT cleared when a player leaves tracking range: they'll very
 * likely be back, and a stale-but-correct entry costs a few hundred bytes while a missing one
 * costs a visible pop to the default skin. ClientSkinCache prunes the expensive part (the
 * uploaded texture) on its own schedule.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkinState {
	private record Appearance(int version, SkinLoadout loadout, List<TempSkinModification> temps) {
	}

	private static final Map<UUID, Appearance> STATE = new ConcurrentHashMap<>();

	private ClientSkinState() {
	}

	public static void accept(UUID playerId, int version, SkinLoadout loadout, List<TempSkinModification> temps) {
		STATE.put(playerId, new Appearance(version, loadout, List.copyOf(temps)));
	}

	public static boolean has(UUID playerId) {
		return STATE.containsKey(playerId);
	}

	/** 0 means "we've never heard about this player" - the cache treats that as "no composite,
	 *  fall through to the vanilla skin," which is the correct look for a player whose sync
	 *  hasn't arrived yet rather than a flash of bald grey. */
	public static int versionOf(UUID playerId) {
		Appearance appearance = STATE.get(playerId);
		return appearance == null ? 0 : appearance.version();
	}

	public static SkinLoadout loadoutOf(UUID playerId) {
		Appearance appearance = STATE.get(playerId);
		return appearance == null ? SkinLoadout.EMPTY : appearance.loadout();
	}

	public static List<TempSkinModification> tempsOf(UUID playerId) {
		Appearance appearance = STATE.get(playerId);
		return appearance == null ? List.of() : appearance.temps();
	}

	/** True if this player has anything at all worth compositing. A vanilla-looking player with
	 *  no parts and no augmentations short-circuits the whole pipeline and keeps their real
	 *  Mojang skin, untouched and uncopied. */
	public static boolean hasAnything(UUID playerId) {
		Appearance appearance = STATE.get(playerId);
		if (appearance == null)
			return false;
		return !appearance.loadout().parts().isEmpty() || appearance.loadout().baseTexturePartId().isPresent() || !appearance.temps().isEmpty();
	}

	public static void clear() {
		STATE.clear();
	}
}
