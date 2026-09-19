package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.charactercreation.CharacterNameFormat.Style;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes a player read as their character everywhere their name or face appears.
 *
 * <p>Three surfaces, two mechanisms:
 *
 * <ul>
 * <li><b>The name over their head, chat, and death messages</b> all come from
 * {@code Player#getDisplayName()}, so one {@link PlayerEvent.NameFormat} handler covers all three.
 * <li><b>The tab list name</b> is separate - it travels in the player-info packet - so it gets its
 * own handler and its own style, because the place you go to find out who someone actually is
 * should probably still tell you.
 * <li><b>The face in the tab list</b> is neither: it's drawn from the composited skin, which
 * {@code PlayerInfoMixin} already routes through the same cache the player model uses.
 * </ul>
 *
 * <p>The awkward part is that name tags are drawn client-side, so every client needs to know every
 * other player's character name. That's what {@link CharacterIdentity} is for: the server keeps the
 * map authoritatively and pushes it out, and both sides then read the same map by UUID. In
 * singleplayer both halves share the one map, which is simply correct rather than a special case.
 */
@EventBusSubscriber(modid = "duality")
public final class CharacterDisplay {
	/**
	 * How the name over a head, in chat, and in death messages reads.
	 *
	 * <p>Character only, by default: this is the in-fiction surface, and seeing an account name
	 * floating over someone is the thing that breaks it.
	 */
	public static final Style DISPLAY_NAME_STYLE = Style.CHARACTER_ONLY;

	/**
	 * How the tab list reads.
	 *
	 * <p>Both, by default. Tab is the out-of-fiction surface - it's where you look to find out who
	 * you're actually playing with, and an all-character tab list makes moderating a server
	 * unnecessarily hard. Set it to {@link Style#CHARACTER_ONLY} if you'd rather it match.
	 */
	public static final Style TAB_LIST_STYLE = Style.CHARACTER_THEN_ACCOUNT;

	/** UUID -> who that player currently is. Written by the server, mirrored on clients. */
	private static final Map<UUID, CharacterIdentity> IDENTITIES = new ConcurrentHashMap<>();

	private CharacterDisplay() {
	}

	// -------------------------------------------------------------------------------- the map
	public static CharacterIdentity identityOf(UUID playerId) {
		CharacterIdentity known = IDENTITIES.get(playerId);
		return known != null ? known : CharacterIdentity.absent(playerId);
	}

	public static Collection<CharacterIdentity> all() {
		return IDENTITIES.values();
	}

	/** Applied by the packet handler on the client, and by {@link #publish} on the server. */
	public static void accept(CharacterIdentity identity) {
		if (identity == null)
			return;
		IDENTITIES.put(identity.playerId(), identity);
	}

	public static void forget(UUID playerId) {
		IDENTITIES.remove(playerId);
	}

	public static void clear() {
		IDENTITIES.clear();
	}

	/** Reads a player's current character off disk and returns who they are now. */
	public static CharacterIdentity read(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId.isEmpty())
			return CharacterIdentity.absent(player.getUUID());
		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, characterId);
		if (sheet == null)
			return CharacterIdentity.absent(player.getUUID());
		// A dead character is nobody. Their sheet still has a name on it, and the profile may not
		// have been cleared yet if this runs before the creator reopens, so check the status rather
		// than trusting the id alone - otherwise a player respawns wearing a dead name.
		boolean alive = !sheet.has("status") || !"DEAD".equalsIgnoreCase(sheet.get("status").getAsString());
		String name = sheet.has("name") ? sheet.get("name").getAsString() : "";
		if (!alive)
			return CharacterIdentity.absent(player.getUUID());
		return new CharacterIdentity(player.getUUID(), name, CharacterCreation.raceIdOf(sheet), !name.isEmpty());
	}

	/**
	 * Re-reads this player's character, tells every client about it, and refreshes the names that
	 * are already on screen.
	 *
	 * <p>Call after anything that changes who a player is: committing a new character, switching
	 * between them, or dying out of one.
	 */
	public static void publish(ServerPlayer player) {
		if (player == null || player.getServer() == null)
			return;
		CharacterIdentity identity = read(player);
		accept(identity);
		CharacterCreationNetwork.broadcastIdentity(player.getServer(), List.of(identity));
		refresh(player);
	}

	/** Hands a joining player everyone's identity, and everyone the joiner's. */
	public static void publishAllTo(ServerPlayer joiner) {
		if (joiner == null || joiner.getServer() == null)
			return;
		List<CharacterIdentity> everyone = new ArrayList<>();
		for (ServerPlayer online : joiner.getServer().getPlayerList().getPlayers()) {
			CharacterIdentity identity = read(online);
			accept(identity);
			everyone.add(identity);
		}
		CharacterCreationNetwork.sendIdentities(joiner, everyone);
		CharacterCreationNetwork.broadcastIdentity(joiner.getServer(), List.of(identityOf(joiner.getUUID())));
	}

	// ------------------------------------------------------------------------------ the names
	/** The label for a player, given their account name and which surface is asking. */
	public static String labelFor(UUID playerId, String accountName, Style style) {
		return CharacterNameFormat.format(style, identityOf(playerId).characterName(), accountName);
	}

	/**
	 * The name over their head, in chat, and in death messages.
	 *
	 * <p>Fires on both sides, because both sides call {@code getDisplayName()} - the server for
	 * chat and death messages, the client for the name tag. Both read the same identity map, so
	 * both agree.
	 */
	@SubscribeEvent
	public static void onNameFormat(PlayerEvent.NameFormat event) {
		Player player = event.getEntity();
		if (player == null)
			return;
		CharacterIdentity identity = identityOf(player.getUUID());
		if (!identity.hasName())
			return; // no character yet - leave them as their account name
		event.setDisplayname(Component.literal(CharacterNameFormat.format(DISPLAY_NAME_STYLE, identity.characterName(), event.getUsername())));
	}

	/** The tab list entry. Separate event, separate style - see {@link #TAB_LIST_STYLE}. */
	@SubscribeEvent
	public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
		Player player = event.getEntity();
		if (player == null)
			return;
		CharacterIdentity identity = identityOf(player.getUUID());
		if (!identity.hasName())
			return;
		event.setDisplayName(Component.literal(CharacterNameFormat.format(TAB_LIST_STYLE, identity.characterName(), player.getGameProfile().getName())));
	}

	/**
	 * Drops the cached display name so the next draw picks up the new one.
	 *
	 * <p>NeoForge caches the result of the NameFormat event on the player rather than firing it
	 * every frame, which is the right trade until the name changes underneath it. Without this a
	 * player who just finished the creator would keep their account name over their head until they
	 * relogged.
	 */
	public static void refresh(Player player) {
		if (player != null)
			player.refreshDisplayName();
	}

	// ---------------------------------------------------------------------------------- hooks
	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			publishAllTo(player);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			forget(player.getUUID());
	}

	/** A respawn builds a fresh entity with a fresh display-name cache, and a canon death may have
	 *  left the player with no character at all. Both want re-publishing. */
	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			publish(player);
	}
}
