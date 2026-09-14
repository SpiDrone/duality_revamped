package net.spidrotech.duality.skin.client;

import net.spidrotech.duality.skin.SkinPartTarget;
import net.spidrotech.duality.skin.SkinEffects;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.platform.NativeImage;

import javax.annotation.Nullable;

import java.util.Random;
import java.util.BitSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Client-side registry of effect implementations, keyed by the ids in SkinEffects. Call
 * bootstrap() once at client setup (FMLClientSetupEvent).
 *
 * Unknown ids are a no-op rather than an error - a server running a newer duality can name an
 * effect this client has never heard of, and the sane outcome is "the color change still
 * applies, the animation doesn't" rather than a crash or a missing player.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkinEffects {
	private static final Map<ResourceLocation, SkinEffect> EFFECTS = new HashMap<>();

	private ClientSkinEffects() {
	}

	public static void register(ResourceLocation id, SkinEffect effect) {
		EFFECTS.put(id, effect);
	}

	@Nullable
	public static SkinEffect get(ResourceLocation id) {
		return EFFECTS.get(id);
	}

	public static void bootstrap() {
		register(SkinEffects.VAMPIRIZE, VAMPIRIZE);
		register(SkinEffects.DESATURATE, DESATURATE);
	}

	// ================================================================== VAMPIRIZE
	private static final int VAMPIRIZE_DURATION = 20; // 1s
	/** How many pixels outward each crack walks PAST its seed - total crack length is this + 1
	 *  (the seed pixel itself). The whole face is 8x8, so keep this small; 2 gives short cracks
	 *  hugging the eye, higher values reach further across the face. */
	private static final int VAMPIRIZE_STEPS = 2;
	/** Chance a border pixel starts a crack - the exact rule requested: each candidate rolls
	 *  independently, but only if no already-decided NEIGHBOR started one. */
	private static final float VAMPIRIZE_START_CHANCE = 0.5f;
	private static final float VAMPIRIZE_TURN_CHANCE = 0.3f;
	private static final float VAMPIRIZE_CRACK_STRENGTH = 0.9f;
	/** Fraction of the total duration a freshly-revealed crack pixel takes to go from faint to
	 *  full strength - see class doc FADE-IN on the effect itself. */
	private static final float VAMPIRIZE_RAMP_FRACTION = 0.4f;
	/** The faint darkening bled onto the pixels immediately around each drawn crack pixel -
	 *  "the pixels surrounding the crack have a tint," NOT a block-shaped halo around the eye.
	 *  It follows the cracks wherever they go, so a crack reads as a dark line with a soft edge
	 *  rather than a hard 1px line on clean skin. */
	private static final float VAMPIRIZE_EDGE_TINT_STRENGTH = 0.28f;

	private static final int[][] DIRECTIONS = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 } };

	/** One crack line: the pixels it walks, in step order (index 0 = the seed border pixel,
	 *  which is directly adjacent to the eye, then each subsequent pixel one step further out). */
	private record CrackLine(List<int[]> pixels) {
	}

	/**
	 * Several short cracks radiating outward FROM the eye pixels themselves, each drawn as a
	 * dark line with a faint tint bleeding onto its immediate neighbors. No block-shaped halo
	 * around the eye - the tint follows the cracks, not the eye region.
	 *
	 * SEEDING (the requested rule): let X be every pixel of the eye itself - see EYE SOURCE on
	 * eyeGrid for where that comes from. Let O be every pixel touching an X that isn't one. Scan
	 * the O pixels in a fixed order and, for each, roll a 50% chance to start a crack there - but
	 * skip the roll entirely if a neighboring O pixel already started one:
	 *
	 *   OOOOO
	 *   OXOXO      X = eye pixels. O = candidates. Each O independently gets a shot at starting
	 *   OOOOO      a crack, spaced out by the neighbor-exclusion rule so starts don't clump.
	 *
	 * A crack's FIRST drawn pixel is the seed O itself (which is directly adjacent to an eye
	 * pixel), so every crack physically touches the eye - the walk only adds pixels AFTER that,
	 * never before it. Getting this wrong (stepping before recording the seed) is what put a
	 * one-pixel gap between the eye and each crack.
	 *
	 * Using the real mask (unioned EYES + PUPIL) is what makes this land on BOTH eyes
	 * automatically when the equipped texture draws them as two separate blobs - each blob gets
	 * its own ring of candidates and its own independent 50% rolls, no per-eye code anywhere.
	 *
	 * FADE-IN: a crack pixel doesn't snap to full darkness the instant it's revealed. Every
	 * pixel has a reveal threshold (how far into the animation its step becomes visible) and
	 * then ramps from faint to VAMPIRIZE_CRACK_STRENGTH over VAMPIRIZE_RAMP_FRACTION of the
	 * total duration - segments that appeared a while ago are fully dark, freshly-appeared ones
	 * are still lightening in.
	 *
	 * All crack lines advance in lockstep (every line's step k becomes visible at the same
	 * progress threshold as every other line's step k), so multiple cracks visibly grow
	 * together rather than one finishing before the next starts.
	 */
	public static final SkinEffect VAMPIRIZE = new SkinEffect() {
		@Override
		public int durationTicks() {
			return VAMPIRIZE_DURATION;
		}

		@Override
		public void apply(NativeImage canvas, SkinPartTarget target, int color, float progress, long seed, Map<SkinPartTarget, BitSet> masks) {
			// Union EYES and PUPIL - see EYE SOURCE. A setup with only a pupil equipped (no
			// separate sclera part) still gets cracks anchored on the real pupil shape.
			boolean[] isEye = eyeGrid(target, masks.get(SkinPartTarget.EYES), masks.get(SkinPartTarget.PUPIL));
			double[] center = centerOf(isEye);
			SkinPartTarget.Rect bounds = target.bleedBounds();

			List<CrackLine> cracks = crackLines(target, isEye, center, seed);
			// Pass 1: the faint edge tint, so crack pixels drawn in pass 2 land ON TOP of their
			// own tint rather than under it. A pixel that's part of the eye or part of a crack
			// never gets tinted - the tint is strictly for the clean skin bordering a crack.
			for (CrackLine crack : cracks) {
				List<int[]> pixels = crack.pixels();
				for (int step = 0; step < pixels.size(); step++) {
					float revealAt = step / (float) (VAMPIRIZE_STEPS + 1);
					if (progress < revealAt)
						break;
					int[] pixel = pixels.get(step);
					float age = VAMPIRIZE_RAMP_FRACTION <= 0 ? 1f : Math.min(1f, (progress - revealAt) / VAMPIRIZE_RAMP_FRACTION);
					for (int[] dir : DIRECTIONS) {
						int nx = pixel[0] + dir[0], ny = pixel[1] + dir[1];
						if (!bounds.contains(nx, ny) || isSet(isEye, nx, ny))
							continue;
						darken(canvas, nx, ny, VAMPIRIZE_EDGE_TINT_STRENGTH * age);
					}
				}
			}
			// Pass 2: the cracks themselves, over the tint.
			for (CrackLine crack : cracks) {
				List<int[]> pixels = crack.pixels();
				for (int step = 0; step < pixels.size(); step++) {
					float revealAt = step / (float) (VAMPIRIZE_STEPS + 1);
					if (progress < revealAt)
						break; // this and every later step on this line haven't appeared yet
					float age = VAMPIRIZE_RAMP_FRACTION <= 0 ? 1f : Math.min(1f, (progress - revealAt) / VAMPIRIZE_RAMP_FRACTION);
					int[] pixel = pixels.get(step);
					darken(canvas, pixel[0], pixel[1], VAMPIRIZE_CRACK_STRENGTH * age);
				}
			}
		}
	};

	/**
	 * Builds the 64x64 "is this pixel the eye itself" grid.
	 *
	 * EYE SOURCE: unions the EYES mask and the PUPIL mask, whichever exist - not just the
	 * effect's own target. A setup with a proper sclera part gets that shape; a setup with only
	 * a pupil equipped (the common case - see class doc on the VAMPIRIZE effect) gets the
	 * pupil's real shape instead; a setup with both gets the union of both, which is the most
	 * accurate picture available. Only when NEITHER mask has anything (a fully vanilla-skinned
	 * player) does this fall back to the static rects for both targets - a fixed guess is
	 * better than nothing, but real pixels always win when they exist.
	 *
	 * FRONT-FACE ONLY: a part mask is the texture's real painted pixels ANYWHERE on the 64x64
	 * sheet, and a full head texture usually paints eyes/pupils on several faces (they share one
	 * skin). We only ever want the FRONT face's eyes here - so the grid is intersected with
	 * bleedBounds (HEAD_FRONT for these targets). Without this clamp, off-face eye pixels would
	 * drag centerOf()'s centroid off the true front-face center (skewing every crack's outward
	 * direction) and could mis-flag front-face pixels sitting across a UV seam from an off-face
	 * eye as border candidates. The crack SEEDING is already bounds-clamped, so this doesn't
	 * change where cracks can spawn - it fixes what they aim away from.
	 */
	private static boolean[] eyeGrid(SkinPartTarget target, @Nullable BitSet eyesMask, @Nullable BitSet pupilMask) {
		boolean[] grid = new boolean[SkinCompositor.SKIN_WIDTH * SkinCompositor.SKIN_HEIGHT];
		boolean any = false;
		any |= fillFromMask(grid, eyesMask);
		any |= fillFromMask(grid, pupilMask);
		if (!any) {
			fillFromRects(grid, target.regions());
			fillFromRects(grid, SkinPartTarget.PUPIL.regions());
		}
		clampToBounds(grid, target.bleedBounds());
		return grid;
	}

	/** Clears every set pixel outside the given box - see eyeGrid's FRONT-FACE ONLY note. */
	private static void clampToBounds(boolean[] grid, SkinPartTarget.Rect bounds) {
		for (int i = 0; i < grid.length; i++) {
			if (!grid[i])
				continue;
			int x = i % SkinCompositor.SKIN_WIDTH, y = i / SkinCompositor.SKIN_WIDTH;
			if (!bounds.contains(x, y)) {
				grid[i] = false;
			}
		}
	}

	private static boolean fillFromMask(boolean[] grid, @Nullable BitSet mask) {
		if (mask == null || mask.isEmpty())
			return false;
		for (int bit = mask.nextSetBit(0); bit >= 0; bit = mask.nextSetBit(bit + 1)) {
			grid[bit] = true;
		}
		return true;
	}

	private static void fillFromRects(boolean[] grid, List<SkinPartTarget.Rect> rects) {
		for (SkinPartTarget.Rect rect : rects) {
			for (int y = rect.y(); y < rect.y() + rect.height(); y++) {
				for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
					if (x >= 0 && y >= 0 && x < SkinCompositor.SKIN_WIDTH && y < SkinCompositor.SKIN_HEIGHT) {
						grid[y * SkinCompositor.SKIN_WIDTH + x] = true;
					}
				}
			}
		}
	}

	private static double[] centerOf(boolean[] isEye) {
		double sumX = 0, sumY = 0;
		int count = 0;
		for (int i = 0; i < isEye.length; i++) {
			if (!isEye[i])
				continue;
			sumX += i % SkinCompositor.SKIN_WIDTH;
			sumY += i / SkinCompositor.SKIN_WIDTH;
			count++;
		}
		return count == 0 ? new double[] { 0, 0 } : new double[] { sumX / count, sumY / count };
	}

	private static boolean isSet(boolean[] grid, int x, int y) {
		if (x < 0 || y < 0 || x >= SkinCompositor.SKIN_WIDTH || y >= SkinCompositor.SKIN_HEIGHT)
			return false;
		return grid[y * SkinCompositor.SKIN_WIDTH + x];
	}

	/** Generic 8-neighbor "does any neighbor of (x,y) satisfy this grid" check - used both for
	 *  "is this a border pixel of the eye" (grid = isEye) and for "did a neighbor already start
	 *  a crack" (grid = started), which is exactly the same kind of check against two different
	 *  grids. */
	private static boolean hasTrueNeighbor(boolean[] grid, int x, int y) {
		for (int[] dir : DIRECTIONS) {
			if (isSet(grid, x + dir[0], y + dir[1]))
				return true;
		}
		return false;
	}

	/** Implements the requested seeding rule exactly: scan candidate border pixels in a fixed
	 *  order, each independently rolls VAMPIRIZE_START_CHANCE, but a candidate whose neighbor
	 *  already started a crack this pass is skipped outright rather than rolled. */
	private static List<CrackLine> crackLines(SkinPartTarget target, boolean[] isEye, double[] center, long seed) {
		SkinPartTarget.Rect bounds = target.bleedBounds();
		Random random = new Random(seed);
		boolean[] started = new boolean[SkinCompositor.SKIN_WIDTH * SkinCompositor.SKIN_HEIGHT];
		List<int[]> starts = new ArrayList<>();
		for (int y = bounds.y(); y < bounds.y() + bounds.height(); y++) {
			for (int x = bounds.x(); x < bounds.x() + bounds.width(); x++) {
				if (isSet(isEye, x, y) || !hasTrueNeighbor(isEye, x, y))
					continue; // not a border candidate at all
				if (hasTrueNeighbor(started, x, y))
					continue; // a neighbor already started - see class doc SEEDING
				if (random.nextFloat() < VAMPIRIZE_START_CHANCE) {
					started[y * SkinCompositor.SKIN_WIDTH + x] = true;
					starts.add(new int[] { x, y });
				}
			}
		}

		List<CrackLine> lines = new ArrayList<>();
		for (int[] start : starts) {
			lines.add(walkCrack(start, center, bounds, random));
		}
		return lines;
	}

	/** Walks outward starting FROM the seed border pixel (which is itself adjacent to an eye
	 *  pixel), recording that seed as pixel 0 so the crack physically connects to the eye, then
	 *  stepping outward in the direction away from the eye's own center - with a small chance to
	 *  turn each step for a jagged rather than dead-straight line. */
	private static CrackLine walkCrack(int[] start, double[] center, SkinPartTarget.Rect bounds, Random random) {
		List<int[]> pixels = new ArrayList<>(VAMPIRIZE_STEPS + 1);
		int x = start[0], y = start[1];
		pixels.add(new int[] { x, y }); // the seed itself - touches the eye, closes the gap
		double dx = x - center[0], dy = y - center[1];
		int dir = snapDirection(dx, dy, random);
		for (int step = 0; step < VAMPIRIZE_STEPS; step++) {
			if (random.nextFloat() < VAMPIRIZE_TURN_CHANCE) {
				dir = (dir + (random.nextBoolean() ? 1 : DIRECTIONS.length - 1)) % DIRECTIONS.length;
			}
			x += DIRECTIONS[dir][0];
			y += DIRECTIONS[dir][1];
			if (!bounds.contains(x, y))
				break; // walked off the allowed face - stop this line here, not later
			pixels.add(new int[] { x, y });
		}
		return new CrackLine(pixels);
	}

	/** Picks whichever of the 8 directions points closest to (dx,dy). Falls back to a random
	 *  direction only in the degenerate case of a single-pixel eye shape with dx=dy=0. */
	private static int snapDirection(double dx, double dy, Random random) {
		if (dx == 0 && dy == 0) {
			return random.nextInt(DIRECTIONS.length);
		}
		int best = 0;
		double bestDot = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < DIRECTIONS.length; i++) {
			double dot = dx * DIRECTIONS[i][0] + dy * DIRECTIONS[i][1];
			if (dot > bestDot) {
				bestDot = dot;
				best = i;
			}
		}
		return best;
	}

	// ================================================================== DESATURATE
	/** Drains the target region toward its own greyscale. Uses the static Rect regions rather
	 *  than a mask - simpler template for a whole-region effect; VAMPIRIZE above is the one to
	 *  copy if what you're writing needs pixel-precise placement instead. */
	public static final SkinEffect DESATURATE = new SkinEffect() {
		@Override
		public int durationTicks() {
			return 20;
		}

		@Override
		public void apply(NativeImage canvas, SkinPartTarget target, int color, float progress, long seed, Map<SkinPartTarget, BitSet> masks) {
			for (SkinPartTarget.Rect rect : target.regions()) {
				for (int y = rect.y(); y < rect.y() + rect.height(); y++) {
					for (int x = rect.x(); x < rect.x() + rect.width(); x++) {
						if (x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight())
							continue;
						int pixel = SkinCompositor.toArgb(canvas.getPixelRGBA(x, y));
						if ((pixel >>> 24) == 0)
							continue;
						int grey = SkinCompositor.clamp((int) SkinCompositor.luminance(pixel));
						int r = SkinCompositor.lerp((pixel >> 16) & 0xFF, grey, progress);
						int g = SkinCompositor.lerp((pixel >> 8) & 0xFF, grey, progress);
						int b = SkinCompositor.lerp(pixel & 0xFF, grey, progress);
						canvas.setPixelRGBA(x, y, SkinCompositor.toNative((pixel & 0xFF000000) | (r << 16) | (g << 8) | b));
					}
				}
			}
		}
	};

	// ================================================================== shared helpers
	/** Blends one pixel toward black by strength, leaving alpha alone so the silhouette never
	 *  changes shape. Skips fully-transparent pixels so a crack step that lands just past the
	 *  edge of the head doesn't paint a stray dark dot into empty canvas space. */
	public static void darken(NativeImage canvas, int x, int y, float strength) {
		if (x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight() || strength <= 0)
			return;
		int pixel = SkinCompositor.toArgb(canvas.getPixelRGBA(x, y));
		if ((pixel >>> 24) == 0)
			return;
		int r = SkinCompositor.lerp((pixel >> 16) & 0xFF, 0, strength);
		int g = SkinCompositor.lerp((pixel >> 8) & 0xFF, 0, strength);
		int b = SkinCompositor.lerp(pixel & 0xFF, 0, strength);
		canvas.setPixelRGBA(x, y, SkinCompositor.toNative((pixel & 0xFF000000) | (r << 16) | (g << 8) | b));
	}
}
