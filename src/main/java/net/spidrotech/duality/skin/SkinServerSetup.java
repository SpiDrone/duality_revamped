package net.spidrotech.duality.skin;

import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Server startup wiring for the skin catalog, in the one order that works:
 *
 *   1. SkinOwnershipRules loads the server owner's override patterns from config. This is a
 *      pure file read - BuiltinPartDefaults (the mod author's own shipped decisions) needs no
 *      loading step at all, since it's plain Java already in the jar.
 *   2. BuiltinSkinParts scans assets/duality/charcreator and writes a json for any part that
 *      doesn't have one yet.
 *   3. SkinPartCatalog reads the whole config directory - generated defaults and admin-authored
 *      parts alike, with no distinction between them from this point on - resolving each part's
 *      ownership as it loads: server rule, else BuiltinPartDefaults, else the generated json's
 *      own value. See SkinOwnershipRules#apply for the exact precedence.
 *
 * Doing it in that order every start is what gives you "drop a PNG in, restart, it's there"
 * without ever clobbering an admin's edits (see BuiltinSkinParts' WRITE-ONCE note).
 *
 * This runs before players can join, so the catalog is always populated by the time
 * SkinManager#onPlayerJoin ships it to someone.
 */
@EventBusSubscriber(modid = "duality")
public final class SkinServerSetup {
	private SkinServerSetup() {
	}

	@SubscribeEvent
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		SkinOwnershipRules.load(FMLPaths.CONFIGDIR.get());
		BuiltinSkinParts.generateMissingConfigs(FMLPaths.CONFIGDIR.get());
		SkinPartCatalog.loadFromConfig(FMLPaths.CONFIGDIR.get());
	}
}
