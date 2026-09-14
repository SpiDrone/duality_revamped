package net.spidrotech.duality.skin.client;

import net.spidrotech.duality.skin.TempSkinModification;
import net.spidrotech.duality.skin.SkinPartTarget;
import net.spidrotech.duality.skin.SkinPartCatalog;
import net.spidrotech.duality.skin.SkinPart;
import net.spidrotech.duality.skin.SkinLoadout;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.io.InputStream;

/**
 * All actual pixel work for the skin builder: loading part textures, trimming them, blending
 * them onto a base, applying tints, and applying temporary augmentations. Everything here is
 * pure image manipulation - it uploads nothing and knows nothing about caching or rendering
 * (see ClientSkinCache for both).
 *
 * TRIM ON LOAD - the resolution to "should parts be full 64x64 files": yes on disk, no in
 * memory. Part textures are authored as full-canvas 64x64 overlays because that's what every
 * skin editor produces, it needs no placement metadata that an author can get wrong, and it
 * handles parts spanning several UV faces (a hat crossing four faces, pale skin crossing six
 * limbs) which a single crop rectangle cannot express. But a decoded 64x64 NativeImage is 16KB
 * regardless of how much of it is transparent, so on load we scan the alpha channel, keep only
 * the bounding box, and remember its origin. A 4x4 mole costs 64 bytes resident instead of
 * 16KB, and blitting it touches 16 pixels instead of 4096. Authors never see any of this.
 *
 * TEXTURE CACHE is a bounded LRU over TRIMMED images, not over the catalog - only parts
 * actually worn by someone currently visible are ever decoded, so a catalog of several thousand
 * parts costs nothing until somebody wears one.
 *
 * INTERLEAVED COMPOSITING, not "every part then every effect": compose() walks SkinPartTarget's
 * values in order, and for EACH target draws that target's equipped parts, records exactly
 * which pixels they painted (their real alpha shape - see PER-TARGET MASKS), THEN applies any
 * temp modification aimed at that target, THEN moves to the next target. This is what makes
 * PUPIL composite strictly after anything aimed at EYES: an effect targeting EYES runs and
 * finishes before PUPIL's own parts ever touch the canvas, so it is structurally impossible for
 * an eye-socket effect to paint over the pupil - see SkinPartTarget's PUPIL LAYERING doc.
 *
 * PER-TARGET MASKS: a BitSet per target, set wherever that target's parts actually put ink
 * (their own alpha, not just "canvas is opaque there"). Computed in a dedicated pass BEFORE any
 * drawing happens - see compose()'s PUPIL LOOKUP note - so every target's mask is available
 * regardless of composite order. TempSkinModification#color() uses this instead of a generic
 * rectangle when one is available, so recoloring a pupil recolors exactly that pupil texture's
 * own pixels - whatever size or shape it happens to be - rather than a fixed box. Effects get
 * the full map too, which is what lets one aimed at an earlier-drawn target still see the real
 * shape of a later-drawn one (see ClientSkinEffects#VAMPIRIZE, which looks at PUPIL's mask while
 * running at the EYES stage). No mask exists for a target nobody equipped anything in, which is
 * the fallback-to-static-Rect signal.
 *
 * PIXEL FORMAT WARNING: NativeImage in 1.21.1 packs as ABGR (getPixelRGBA/setPixelRGBA), NOT
 * ARGB, and everything else in this codebase - part tints, TempSkinModification#color,
 * TeleportGlowStyle - is ARGB. Every read and write below goes through toArgb/toNative rather
 * than touching raw values, and any new code here must do the same or colors come out with red
 * and blue swapped, which reads as "my red vampire eyes are blue" rather than as an obvious
 * crash.
 * TODO: 1.21.2+ renamed these to getPixel/setPixel AND switched them to ARGB - if you update,
 * the helpers below become identity functions and the call sites stay unchanged.
 */
@OnlyIn(Dist.CLIENT)
public final class SkinCompositor {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int SKIN_WIDTH = 64;
	public static final int SKIN_HEIGHT = 64;
	/** Roughly 40 worn parts' worth of trimmed images - comfortably more than a busy server's
	 *  worth of visible players, and small enough that it can never grow into a leak. */
	private static final int MAX_CACHED_TEXTURES = 256;
	/** Textures that failed to load are remembered so a missing resourcepack doesn't spam the
	 *  log once per frame per player. */
	private static final Set<ResourceLocation> FAILED = new LinkedHashSet<>();

	/** A part texture with its transparent margins removed. originX/originY put it back where
	 *  it belongs on the 64x64 canvas. An entirely blank texture yields width/height 0 and is
	 *  skipped at blit time rather than being a special case everywhere. */
	public record TrimmedImage(NativeImage image, int originX, int originY, int width, int height) {
		public boolean isEmpty() {
			return width == 0 || height == 0;
		}
	}

	private static final Map<ResourceLocation, TrimmedImage> TEXTURE_CACHE = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<ResourceLocation, TrimmedImage> eldest) {
			if (size() <= MAX_CACHED_TEXTURES)
				return false;
			eldest.getValue().image().close(); // NativeImage is native memory - dropping the
			return true; // reference alone would leak it
		}
	};

	private SkinCompositor() {
	}

	// ================================================================== loading
	@Nullable
	public static TrimmedImage texture(ResourceLocation location) {
		TrimmedImage cached = TEXTURE_CACHE.get(location);
		if (cached != null)
			return cached;
		if (FAILED.contains(location))
			return null;
		try (InputStream stream = Minecraft.getInstance().getResourceManager().open(location)) {
			NativeImage raw = NativeImage.read(stream);
			TrimmedImage trimmed = trim(raw);
			raw.close();
			TEXTURE_CACHE.put(location, trimmed);
			return trimmed;
		} catch (Exception missing) {
			// A part whose texture no resourcepack provides is skipped, not fatal - the rest of
			// the player's skin still composites. This is the expected failure when a server
			// defines parts the client's resourcepack doesn't have yet.
			FAILED.add(location);
			LOGGER.warn("[duality] Skin part texture missing, skipping layer: {}", location);
			return null;
		}
	}

	/** Alpha-channel bounding box - see class doc TRIM ON LOAD. */
	private static TrimmedImage trim(NativeImage raw) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
		for (int y = 0; y < raw.getHeight(); y++) {
			for (int x = 0; x < raw.getWidth(); x++) {
				if (alphaOf(raw.getPixelRGBA(x, y)) == 0)
					continue;
				if (x < minX)
					minX = x;
				if (y < minY)
					minY = y;
				if (x > maxX)
					maxX = x;
				if (y > maxY)
					maxY = y;
			}
		}
		if (maxX < 0) {
			return new TrimmedImage(new NativeImage(1, 1, false), 0, 0, 0, 0);
		}
		int width = maxX - minX + 1;
		int height = maxY - minY + 1;
		NativeImage cropped = new NativeImage(width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				cropped.setPixelRGBA(x, y, raw.getPixelRGBA(minX + x, minY + y));
			}
		}
		return new TrimmedImage(cropped, minX, minY, width, height);
	}

	public static void invalidateTextures() {
		for (TrimmedImage trimmed : TEXTURE_CACHE.values()) {
			trimmed.image().close();
		}
		TEXTURE_CACHE.clear();
		FAILED.clear();
	}

	// ================================================================== compositing
	/**
	 * Builds a finished 64x64 skin. Caller owns the returned image and must close it.
	 *
	 * TWO PASSES, for two different reasons:
	 *   1. MASK PASS - scans every equipped part's own texture alpha (no drawing, no canvas
	 *      involved) to learn where EVERY target's part would land, regardless of draw order.
	 *      This is what lets an EYES-targeted effect see PUPIL's actual shape even though PUPIL
	 *      hasn't been drawn yet - see PUPIL LOOKUP note below.
	 *   2. DRAW PASS - walks every SkinPartTarget in enum order; at each target, draws that
	 *      target's parts, then applies any modification aimed at that target, then moves on.
	 *      See class doc INTERLEAVED COMPOSITING - this is what guarantees an EYES-targeted
	 *      effect finishes drawing before PUPIL's texture ever touches the canvas.
	 *
	 * PUPIL LOOKUP: a crack effect wants to know where the EYES are, but a lot of setups have
	 * no separate sclera part at all - just a PUPIL. If the effect only had access to its own
	 * target's mask, it would fall back to a generic fixed rectangle that has no relationship to
	 * wherever that pupil texture actually is, producing cracks that visibly float apart from
	 * the eyes. Splitting mask computation into its own pass up front - independent of draw
	 * order - is what lets ClientSkinEffects#VAMPIRIZE look at masks.get(PUPIL) even while
	 * technically running at the EYES stage, and anchor itself on the real eye shape instead.
	 */
	public static NativeImage compose(NativeImage base, SkinLoadout loadout, List<TempSkinModification> temps, long gameTime) {
		NativeImage out = new NativeImage(SKIN_WIDTH, SKIN_HEIGHT, false);
		out.copyFrom(base);

		Map<SkinPartTarget, List<SkinLoadout.Equipped>> partsByTarget = new EnumMap<>(SkinPartTarget.class);
		for (SkinLoadout.Equipped equipped : loadout.sortedForRender()) {
			SkinPart part = SkinPartCatalog.get(equipped.partId());
			if (part == null)
				continue;
			partsByTarget.computeIfAbsent(part.target(), k -> new ArrayList<>()).add(equipped);
		}
		Map<SkinPartTarget, List<TempSkinModification>> modsByTarget = new EnumMap<>(SkinPartTarget.class);
		for (TempSkinModification mod : temps) {
			modsByTarget.computeIfAbsent(mod.target(), k -> new ArrayList<>()).add(mod);
		}

		// ---- pass 1: masks for every target, independent of draw order - see PUPIL LOOKUP ----
		Map<SkinPartTarget, BitSet> masks = new EnumMap<>(SkinPartTarget.class);
		for (Map.Entry<SkinPartTarget, List<SkinLoadout.Equipped>> entry : partsByTarget.entrySet()) {
			BitSet mask = masks.computeIfAbsent(entry.getKey(), k -> new BitSet(SKIN_WIDTH * SKIN_HEIGHT));
			for (SkinLoadout.Equipped equipped : entry.getValue()) {
				SkinPart part = SkinPartCatalog.get(equipped.partId());
				int tintIndex = 0;
				for (SkinPart.SubPart sub : part.subParts()) {
					int tint = 0xFFFFFFFF;
					if (sub.tintable()) {
						int chosen = tintIndex < equipped.tints().size() ? equipped.tints().get(tintIndex) : -1;
						tint = chosen == -1 ? sub.defaultColor() : chosen;
						tintIndex++;
					}
					markMask(texture(sub.texture()), tint, mask);
				}
			}
		}

		// ---- pass 2: actual drawing, interleaved with effects, in target order ----
		for (SkinPartTarget target : SkinPartTarget.values()) {
			for (SkinLoadout.Equipped equipped : partsByTarget.getOrDefault(target, List.of())) {
				SkinPart part = SkinPartCatalog.get(equipped.partId());
				int tintIndex = 0;
				for (SkinPart.SubPart sub : part.subParts()) {
					int tint = 0xFFFFFFFF;
					if (sub.tintable()) {
						// -1 means "whatever the catalog currently calls the default" rather
						// than a baked-in color, so retheming a part updates every saved
						// loadout that never overrode it. See SkinLoadout.Equipped.
						int chosen = tintIndex < equipped.tints().size() ? equipped.tints().get(tintIndex) : -1;
						tint = chosen == -1 ? sub.defaultColor() : chosen;
						tintIndex++;
					}
					blit(out, texture(sub.texture()), tint);
				}
			}
			for (TempSkinModification mod : modsByTarget.getOrDefault(target, List.of())) {
				applyModification(out, mod, gameTime, masks);
			}
		}
		return out;
	}

	/** Alpha-only pass for the MASK PASS above - records exactly which pixels this specific
	 *  overlay would paint (its own texture alpha times the tint's alpha), without touching any
	 *  canvas at all. Deliberately separate from blit() rather than blit() with a null canvas -
	 *  this never allocates or blends, it only ever sets bits. */
	private static void markMask(@Nullable TrimmedImage overlay, int tintArgb, BitSet mask) {
		if (overlay == null || overlay.isEmpty())
			return;
		int tintA = (tintArgb >>> 24) & 0xFF;
		for (int y = 0; y < overlay.height(); y++) {
			int canvasY = overlay.originY() + y;
			if (canvasY < 0 || canvasY >= SKIN_HEIGHT)
				continue;
			for (int x = 0; x < overlay.width(); x++) {
				int canvasX = overlay.originX() + x;
				if (canvasX < 0 || canvasX >= SKIN_WIDTH)
					continue;
				int srcA = (alphaOf(overlay.image().getPixelRGBA(x, y)) * tintA) / 255;
				if (srcA > 0) {
					mask.set(canvasY * SKIN_WIDTH + canvasX);
				}
			}
		}
	}

	private static void applyModification(NativeImage canvas, TempSkinModification mod, long gameTime, Map<SkinPartTarget, BitSet> masks) {
		if (mod.hasColor()) {
			recolorRegion(canvas, mod.target(), mod.color(), masks.get(mod.target()));
		}
		mod.effect().ifPresent(effectId -> {
			SkinEffect effect = ClientSkinEffects.get(effectId);
			if (effect == null)
				return;
			float progress = effect.durationTicks() <= 0 ? 1f : Math.min(1f, (gameTime - mod.startGameTime()) / (float) effect.durationTicks());
			// Deterministic per-application, not per-player - two players vampirised at
			// different moments (different startGameTime) get different-looking cracks; the
			// SAME modification recomputes to the SAME seed every frame, which is what keeps a
			// crack's shape stable across frames while only its revealed LENGTH grows with
			// progress. See ClientSkinEffects#VAMPIRIZE.
			long seed = mod.startGameTime() * 1_000_003L + mod.key().hashCode();
			effect.apply(canvas, mod.target(), mod.color(), Math.max(0f, progress), seed, masks);
		});
	}

	/** True while any of these modifications is still animating - see ClientSkinCache, which
	 *  uses this to decide whether the composite has settled and can go back to being cached
	 *  instead of rebuilt per frame. */
	public static boolean isAnimating(List<TempSkinModification> temps, long gameTime) {
		for (TempSkinModification mod : temps) {
			SkinEffect effect = mod.effect().map(ClientSkinEffects::get).orElse(null);
			if (effect != null && effect.durationTicks() > 0 && gameTime - mod.startGameTime() < effect.durationTicks())
				return true;
		}
		return false;
	}

	// ================================================================== pixel operations
	public static void blit(NativeImage canvas, @Nullable TrimmedImage overlay, int tintArgb) {
		blit(canvas, overlay, tintArgb, null);
	}

	/** Standard source-over alpha blend of a trimmed overlay onto the canvas, with a per-channel
	 *  multiply by tint. Tintable subparts should be authored greyscale so the multiply lands on
	 *  exactly the chosen color; a shaded subpart keeps its shading through the multiply, which
	 *  is occasionally what you want.
	 *
	 *  mask, if given, gets a bit set for every pixel this call actually painted (post-tint
	 *  alpha > 0) - see class doc PER-TARGET MASKS. Pass null when the caller has no use for one
	 *  (e.g. compositing a base body, where there's no "target" to record against). */
	public static void blit(NativeImage canvas, @Nullable TrimmedImage overlay, int tintArgb, @Nullable BitSet mask) {
		if (overlay == null || overlay.isEmpty())
			return;
		int tintR = (tintArgb >> 16) & 0xFF, tintG = (tintArgb >> 8) & 0xFF, tintB = tintArgb & 0xFF, tintA = (tintArgb >>> 24) & 0xFF;
		int canvasWidth = canvas.getWidth();
		for (int y = 0; y < overlay.height(); y++) {
			int canvasY = overlay.originY() + y;
			if (canvasY < 0 || canvasY >= canvas.getHeight())
				continue;
			for (int x = 0; x < overlay.width(); x++) {
				int canvasX = overlay.originX() + x;
				if (canvasX < 0 || canvasX >= canvasWidth)
					continue;
				int src = toArgb(overlay.image().getPixelRGBA(x, y));
				int srcA = ((src >>> 24) * tintA) / 255;
				if (srcA == 0)
					continue;
				int srcR = (((src >> 16) & 0xFF) * tintR) / 255;
				int srcG = (((src >> 8) & 0xFF) * tintG) / 255;
				int srcB = ((src & 0xFF) * tintB) / 255;
				int dst = toArgb(canvas.getPixelRGBA(canvasX, canvasY));
				canvas.setPixelRGBA(canvasX, canvasY, toNative(blendOver(srcA, srcR, srcG, srcB, dst)));
				if (mask != null) {
					mask.set(canvasY * canvasWidth + canvasX);
				}
			}
		}
	}

	private static int blendOver(int srcA, int srcR, int srcG, int srcB, int dstArgb) {
		if (srcA == 255)
			return (0xFF << 24) | (srcR << 16) | (srcG << 8) | srcB;
		int dstA = dstArgb >>> 24;
		int outA = srcA + dstA * (255 - srcA) / 255;
		if (outA == 0)
			return 0;
		int outR = (srcR * srcA + ((dstArgb >> 16) & 0xFF) * dstA * (255 - srcA) / 255) / outA;
		int outG = (srcG * srcA + ((dstArgb >> 8) & 0xFF) * dstA * (255 - srcA) / 255) / outA;
		int outB = (srcB * srcA + (dstArgb & 0xFF) * dstA * (255 - srcA) / 255) / outA;
		return (outA << 24) | (clamp(outR) << 16) | (clamp(outG) << 8) | clamp(outB);
	}

	/**
	 * Overwrites a region's HUE while keeping its LUMINANCE - the operation behind
	 * SkinTempModify's .color(rgb). A flat fill would turn an eye into a solid rectangle and
	 * destroy the pupil and highlight that make it read as an eye at all; scaling the target
	 * color by each pixel's existing brightness keeps every shape the artist drew and changes
	 * only what color it is.
	 *
	 * mask, when non-null and non-empty, is used INSTEAD of target.regions() - this is what
	 * lets a pupil of any size or position get recolored exactly on its own pixels rather than
	 * a fixed box (see class doc PER-TARGET MASKS). Falls back to the static Rect when nothing
	 * is equipped at this target (a vanilla-skinned player still gets red eyes).
	 */
	public static void recolorRegion(NativeImage canvas, SkinPartTarget target, int argb, @Nullable BitSet mask) {
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		float strength = ((argb >>> 24) & 0xFF) / 255f;
		if (mask != null && !mask.isEmpty()) {
			int canvasWidth = canvas.getWidth();
			for (int bit = mask.nextSetBit(0); bit >= 0; bit = mask.nextSetBit(bit + 1)) {
				recolorPixel(canvas, bit % canvasWidth, bit / canvasWidth, r, g, b, strength);
			}
			return;
		}
		for (SkinPartTarget.Rect rect : target.regions()) {
			for (int y = rect.y(); y < rect.y() + rect.height(); y++) {
				for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
					recolorPixel(canvas, x, y, r, g, b, strength);
				}
			}
		}
	}

	private static void recolorPixel(NativeImage canvas, int x, int y, int r, int g, int b, float strength) {
		if (x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight())
			return;
		int pixel = toArgb(canvas.getPixelRGBA(x, y));
		if ((pixel >>> 24) == 0)
			return;
		float luma = luminance(pixel) / 255f;
		int newR = lerp((pixel >> 16) & 0xFF, clamp((int) (r * luma * 2f)), strength);
		int newG = lerp((pixel >> 8) & 0xFF, clamp((int) (g * luma * 2f)), strength);
		int newB = lerp(pixel & 0xFF, clamp((int) (b * luma * 2f)), strength);
		canvas.setPixelRGBA(x, y, toNative((pixel & 0xFF000000) | (newR << 16) | (newG << 8) | newB));
	}

	// ================================================================== helpers
	/** ABGR (NativeImage) -> ARGB (everything else). See class doc PIXEL FORMAT WARNING. */
	public static int toArgb(int nativeAbgr) {
		return (nativeAbgr & 0xFF00FF00) | ((nativeAbgr & 0xFF) << 16) | ((nativeAbgr >> 16) & 0xFF);
	}

	/** ARGB -> ABGR. Same swap; kept as two named methods so call sites read directionally. */
	public static int toNative(int argb) {
		return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
	}

	public static int alphaOf(int nativeAbgr) {
		return nativeAbgr >>> 24;
	}

	public static float luminance(int argb) {
		return 0.2126f * ((argb >> 16) & 0xFF) + 0.7152f * ((argb >> 8) & 0xFF) + 0.0722f * (argb & 0xFF);
	}

	public static int lerp(int a, int b, float t) {
		return clamp((int) (a + (b - a) * t));
	}

	public static int clamp(int channel) {
		return channel < 0 ? 0 : Math.min(channel, 255);
	}
}
