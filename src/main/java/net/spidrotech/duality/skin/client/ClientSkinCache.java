package net.spidrotech.duality.skin.client;

import net.spidrotech.duality.skin.TempSkinModification;
import net.spidrotech.duality.skin.SkinPartCatalog;
import net.spidrotech.duality.skin.SkinPart;
import net.spidrotech.duality.skin.SkinLoadout;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.HashMap;

/**
 * Owns one uploaded composite texture per visible player, and decides when it needs rebuilding.
 * This is the class that answers "don't redraw the skin every tick."
 *
 * THE CACHE KEY IS TWO INTS, deliberately. A rebuild happens only when either changes:
 *   version   - bumped server-side on any appearance change (see SkinManager), mirrored in
 *               ClientSkinState. Nothing here ever inspects a loadout to detect a change.
 *   animFrame - the current game tick, but ONLY while an effect is still animating. Once every
 *               effect has settled this is frozen at a sentinel, so a fully static skin
 *               produces two equal ints forever and costs a comparison per render call.
 * That's the whole mechanism. A server full of players with elaborate skins standing still does
 * literally no image work; a player mid-vampirisation does ~15 rebuilds and then stops.
 *
 * BASE IMAGE, two paths:
 *   - loadout has a base part -> that part's first subpart texture is the base. Cheap, exact,
 *     no GPU involvement. This is the preferred path and what the builder GUI should always
 *     produce.
 *   - loadout has none -> read the player's real Mojang skin back off the GPU. Skins are
 *     downloaded at runtime and never exist as a resource this client can open, so binding the
 *     texture and downloading it is genuinely the only way to get its pixels. It's a ~16KB
 *     readback that happens once per rebuild, not per frame, and it means a player who only
 *     equips a hat keeps their own real skin underneath it instead of being forced onto a
 *     generic body.
 *
 * NOT-YET-LOADED is handled by returning null rather than by caching a wrong result: a skin
 * still downloading reads back as fully transparent, and caching that would leave the player
 * invisible until their next appearance change. Returning null falls through to the vanilla
 * texture for that frame and tries again on the next one.
 *
 * THREADING: every method here must run on the render thread - it binds textures, downloads
 * pixels, and uploads. It's called from the AbstractClientPlayer mixin during rendering, which
 * satisfies that; the assert is there to catch anyone calling it from elsewhere later.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ClientSkinCache {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** animFrame value meaning "nothing is animating" - any constant works, it just has to be
	 *  stable across frames so the comparison keeps matching. */
	private static final int SETTLED = -1;
	/** How often to look for players who are no longer around. Pruning is cheap but pointless to
	 *  do every tick. */
	private static final int PRUNE_INTERVAL_TICKS = 100;

	private record Entry(DynamicTexture texture, ResourceLocation id, int version, int animFrame) {
	}

	private static final Map<UUID, Entry> CACHE = new HashMap<>();
	private static int pruneTimer = 0;

	private ClientSkinCache() {
	}

	/**
	 * The composite texture for this player, or null to use their vanilla skin unchanged.
	 * Called from the mixin on every getSkin() - which is often - so the fast path (cache hit)
	 * does one map lookup and two int comparisons and nothing else.
	 */
	@Nullable
	public static ResourceLocation textureFor(AbstractClientPlayer player, ResourceLocation vanillaTexture) {
		UUID id = player.getUUID();
		if (!ClientSkinState.hasAnything(id))
			return null; // no parts, no augmentations - leave their real skin completely alone
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null)
			return null;
		long gameTime = mc.level.getGameTime();
		List<TempSkinModification> temps = ClientSkinState.tempsOf(id);
		int version = ClientSkinState.versionOf(id);
		int animFrame = SkinCompositor.isAnimating(temps, gameTime) ? (int) gameTime : SETTLED;
		Entry cached = CACHE.get(id);
		if (cached != null && cached.version() == version && cached.animFrame() == animFrame) {
			return cached.id(); // ---- the fast path, and where nearly every call ends ----
		}
		return rebuild(player, vanillaTexture, cached, version, animFrame, temps, gameTime);
	}

	@Nullable
	private static ResourceLocation rebuild(AbstractClientPlayer player, ResourceLocation vanillaTexture, @Nullable Entry existing, int version, int animFrame, List<TempSkinModification> temps, long gameTime) {
		if (!RenderSystem.isOnRenderThread()) {
			// Should be impossible from the mixin; bail rather than corrupt GL state if some
			// future caller gets this wrong.
			return existing != null ? existing.id() : null;
		}
		UUID id = player.getUUID();
		SkinLoadout loadout = ClientSkinState.loadoutOf(id);
		NativeImage base = baseImageFor(loadout, vanillaTexture);
		if (base == null)
			return existing != null ? existing.id() : null; // skin not ready - retry next frame
		NativeImage composed;
		try {
			composed = SkinCompositor.compose(base, loadout, temps, gameTime);
		} catch (Exception failure) {
			// A bad part shouldn't make a player un-renderable. Drop back to vanilla and say so
			// once, rather than throwing inside a render call.
			LOGGER.error("[duality] Failed to compose skin for {}", player.getGameProfile().getName(), failure);
			base.close();
			return null;
		} finally {
			base.close();
		}
		ResourceLocation textureId = existing != null ? existing.id() : ResourceLocation.fromNamespaceAndPath("duality", "skins/" + id.toString().replace('-', '_'));
		DynamicTexture texture = existing != null ? existing.texture() : null;
		if (texture == null) {
			texture = new DynamicTexture(composed);
			Minecraft.getInstance().getTextureManager().register(textureId, texture);
		} else {
			// Reuse the same GL texture object across rebuilds - re-registering every frame
			// during an animation would churn texture handles for no reason.
			NativeImage pixels = texture.getPixels();
			if (pixels != null) {
				pixels.copyFrom(composed);
			}
			composed.close();
			texture.upload();
		}
		CACHE.put(id, new Entry(texture, textureId, version, animFrame));
		return textureId;
	}

	/** See class doc BASE IMAGE. Caller closes the result. */
	@Nullable
	private static NativeImage baseImageFor(SkinLoadout loadout, ResourceLocation vanillaTexture) {
		String basePartId = loadout.baseTexturePartId().orElse(null);
		if (basePartId != null) {
			SkinPart part = SkinPartCatalog.get(basePartId);
			if (part != null && !part.subParts().isEmpty()) {
				SkinCompositor.TrimmedImage trimmed = SkinCompositor.texture(part.subParts().get(0).texture());
				if (trimmed != null && !trimmed.isEmpty()) {
					NativeImage canvas = new NativeImage(SkinCompositor.SKIN_WIDTH, SkinCompositor.SKIN_HEIGHT, false);
					canvas.fillRect(0, 0, SkinCompositor.SKIN_WIDTH, SkinCompositor.SKIN_HEIGHT, 0);
					SkinCompositor.blit(canvas, trimmed, 0xFFFFFFFF);
					return canvas;
				}
			}
		}
		return readBack(vanillaTexture);
	}

	/**
	 * Pulls a texture's pixels back off the GPU. Needed because a downloaded player skin is a
	 * runtime texture with no resource behind it - getResourceManager().open() on it fails.
	 *
	 * TODO: verify NativeImage#downloadTexture(int, boolean) against your 1.21.1 mappings; this
	 * is the same call vanilla's screenshot path uses, but it has moved before. If it's absent,
	 * the alternative is GL11.glGetTexImage directly.
	 *
	 * Returns null if the readback comes back entirely transparent, which is what an
	 * still-downloading skin looks like - see class doc NOT-YET-LOADED.
	 */
	@Nullable
	private static NativeImage readBack(ResourceLocation textureId) {
		try {
			AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
			if (texture == null)
				return null;
			texture.bind();
			NativeImage image = new NativeImage(SkinCompositor.SKIN_WIDTH, SkinCompositor.SKIN_HEIGHT, false);
			image.downloadTexture(0, false);
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					if (SkinCompositor.alphaOf(image.getPixelRGBA(x, y)) != 0)
						return image;
				}
			}
			image.close();
			return null;
		} catch (Exception failure) {
			LOGGER.warn("[duality] Could not read back base skin texture {}", textureId, failure);
			return null;
		}
	}

	// ================================================================== lifecycle
	/** Drops a single player's composite - call this if you ever need to force a rebuild
	 *  outside the version mechanism (a resourcepack reload, for instance). */
	public static void invalidate(UUID playerId) {
		Entry entry = CACHE.remove(playerId);
		if (entry != null) {
			release(entry);
		}
	}

	public static void clearAll() {
		for (Entry entry : CACHE.values()) {
			release(entry);
		}
		CACHE.clear();
	}

	private static void release(Entry entry) {
		Minecraft.getInstance().getTextureManager().release(entry.id());
		entry.texture().close();
	}

	/**
	 * Frees composites for players who have left. Without this, every player seen in a long
	 * session keeps a 16KB GL texture alive forever - individually trivial, collectively a slow
	 * leak on a busy server. ClientSkinState's cheap metadata mirror is deliberately NOT pruned
	 * here (see that class): keeping it means a returning player composites instantly instead of
	 * flashing their vanilla skin while a sync round-trips.
	 */
	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			if (!CACHE.isEmpty()) {
				clearAll();
				ClientSkinState.clear();
				SkinCompositor.invalidateTextures();
			}
			return;
		}
		if (++pruneTimer < PRUNE_INTERVAL_TICKS)
			return;
		pruneTimer = 0;
		if (CACHE.isEmpty())
			return;
		Set<UUID> present = new LinkedHashSet<>();
		for (AbstractClientPlayer player : mc.level.players()) {
			present.add(player.getUUID());
		}
		CACHE.keySet().removeIf(id -> {
			if (present.contains(id))
				return false;
			release(CACHE.get(id));
			return true;
		});
	}
}
