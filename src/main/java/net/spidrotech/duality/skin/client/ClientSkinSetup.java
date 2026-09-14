package net.spidrotech.duality.skin.client;

import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * One-time client wiring for the skin system. Two jobs, both easy to forget and both silent
 * when missed:
 *
 *   1. Register effect implementations. Without this, augmentations still apply their color but
 *      never animate - which looks like "the effect didn't fire" rather than like a missing
 *      registration, so it's worth knowing this is the cause.
 *
 *   2. Invalidate cached images on resource reload (F3+T, resourcepack change). Trimmed part
 *      textures and every uploaded composite are both derived from resourcepack contents, so
 *      both have to go. Skipping this means an artist iterating on a part sees no change until
 *      they restart the game.
 *
 * Both of these are mod-bus events. Recent NeoForge infers the bus from the event type, so the
 * explicit bus = Bus.MOD argument this used to carry is deprecated and has been dropped - the
 * annotation below still routes these correctly.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ClientSkinSetup {
	private ClientSkinSetup() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(ClientSkinEffects::bootstrap);
	}

	@SubscribeEvent
	public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
		event.registerReloadListener((ResourceManagerReloadListener) (ResourceManager manager) -> {
			SkinCompositor.invalidateTextures();
			ClientSkinCache.clearAll();
		});
	}
}
