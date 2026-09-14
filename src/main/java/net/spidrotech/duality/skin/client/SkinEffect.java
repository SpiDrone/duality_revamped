package net.spidrotech.duality.skin.client;

import net.spidrotech.duality.skin.SkinPartTarget;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.BitSet;
import java.util.Map;

/**
 * A temporary augmentation's visual behavior - the thing that turns "key: vampire" into pixels.
 *
 * Effects are pure functions of (image, region, color, progress, seed, masks). They hold no
 * per-player state, which is what lets the same effect instance serve every player on screen
 * and lets the composite cache rebuild a frame from scratch at any time without an effect
 * noticing. Progress is derived from the SERVER's start tick (see TempSkinModification), so
 * every client watching the same transformation sees it at the same moment.
 *
 * SEED is likewise derived server-side (from the modification's start tick and key - see
 * SkinCompositor#applyModification) and stays constant for the lifetime of one modification. It
 * exists for effects that need PIXEL-STABLE randomness - a crack pattern, say - which must look
 * identical on every frame of the same animation (and on every client watching it) while only
 * its revealed extent grows with progress. An effect that regenerates the same seed's random
 * path every call and reveals more of it as progress climbs gets a stable-looking, growing
 * effect for free; recomputing fresh randomness each call instead would flicker.
 *
 * MASKS is every target's per-target pixel mask (see SkinCompositor's class doc PER-TARGET
 * MASKS), computed BEFORE any drawing happens - so it's available regardless of composite
 * order, and regardless of which target this effect itself is aimed at. A target nobody
 * equipped anything in simply has no entry (masks.get returns null) - fall back to that
 * target's static Rect in that case. This full-map access (rather than just "this effect's own
 * target's mask") is what lets an effect aimed at an EARLIER-drawn target still see the real
 * shape of a LATER-drawn one - see ClientSkinEffects#VAMPIRIZE, which targets EYES (so it
 * finishes drawing before PUPIL ever touches the canvas) but looks at masks.get(PUPIL) to find
 * out where the eyes actually are when no separate sclera part is equipped.
 *
 * SETTLING: an effect runs for durationTicks() and then holds at progress 1.0 forever. That
 * distinction drives the caching: while progress is still climbing the composite is rebuilt
 * every frame, and the moment it reaches 1.0 the result becomes a static texture again with
 * zero per-frame cost (see ClientSkinCache). A transformation that is permanent but has a
 * one-second reveal therefore costs ~20 rebuilds total, not 20 per second forever.
 *
 * Return 0 from durationTicks() for an instant effect that never animates.
 */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface SkinEffect {
	/**
	 * @param canvas   the 64x64 composite so far, mutated in place
	 * @param target   which region this was aimed at - see SkinPartTarget#regions and
	 *                 #bleedBounds
	 * @param color    the modification's ARGB color, or 0 if none was given. An effect may
	 *                 ignore this; the generic recolor has already been applied by the time
	 *                 this runs, so most do.
	 * @param progress 0..1, clamped
	 * @param seed     stable for the lifetime of one modification - see class doc SEED
	 * @param masks    every target's real painted pixels - see class doc MASKS
	 */
	void apply(NativeImage canvas, SkinPartTarget target, int color, float progress, long seed, Map<SkinPartTarget, BitSet> masks);

	default int durationTicks() {
		return 0;
	}
}
