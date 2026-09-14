package net.spidrotech.duality.skin;

import java.util.List;

/**
 * Where a part lives - which answers TWO different questions with one enum, deliberately:
 *
 *   1. SLOT EXCLUSIVITY (for the builder GUI): two parts with the same exclusive target can't
 *      both be equipped. You can't wear two hairstyles. You CAN wear several ACCESSORY parts,
 *      which is why that one is non-exclusive.
 *
 *   2. PIXEL REGION (for temp effects): "turn the skin around the eyes black" needs to know
 *      WHICH pixels those are, in 64x64 skin space. Effects operate on a target's regions()
 *      rather than on a part, so an effect works even if the player has no part equipped in
 *      that slot - a vanilla-skinned vampire still gets black eyes.
 *
 * RENDER ORDER is the enum's own declaration order (see order()). Parts composite strictly in
 * this sequence, so a HAT always lands on top of HAIR regardless of what order the loadout
 * happens to list them in. Insert new values in the position you want them drawn, not at the
 * end.
 *
 * REGION COORDINATES are the classic 64x64 skin layout. They are NOT part placement (parts are
 * full-canvas overlays - see SkinPart) - they exist purely so effects have somewhere to aim,
 * and only as a FALLBACK when nothing is actually equipped there (see PUPIL LAYERING below).
 *
 * PUPIL LAYERING: EYES and PUPIL are two separate targets, composited in that order, for one
 * reason - it's what lets a temp effect land strictly UNDER the pupil. SkinCompositor doesn't
 * run "every part, then every effect": it interleaves them by target order, applying any
 * modification aimed at a target the instant that target's parts finish compositing and BEFORE
 * the next target's parts draw. An effect aimed at EYES therefore runs, then PUPIL composites on
 * top of it - a crack effect structurally cannot appear over the pupil, because the pupil
 * doesn't exist on the canvas yet when the crack is drawn. This is also why eye color changes
 * belong on PUPIL rather than EYES: SkinCompositor records exactly which pixels each equipped
 * part painted (its real alpha shape), so a .color() aimed at PUPIL recolors precisely that
 * part's own pupil pixels - whatever size or position it happens to be - falling back to
 * PUPIL_FALLBACK's small rect only when the player has no pupil part equipped at all.
 */
public enum SkinPartTarget {
	/** The body itself - skin tone, base texture. Exactly one, always beneath everything. */
	SKIN_TONE(true, Rect.WHOLE),
	/** Face detail: scars, war paint, freckles. Front of the head only. */
	FACE(true, Rect.HEAD_FRONT),
	/** Iris/sclera. Its own slot rather than part of FACE so vampirize et al can aim here. */
	EYES(true, Rect.EYES),
	/** The pupil specifically, ALWAYS drawn immediately after EYES - see class doc PUPIL LAYERING.
	 *  A player equips a pupil texture whose own shape/size can be anything; nothing here
	 *  assumes a fixed size or position for it. */
	PUPIL(true, Rect.PUPIL_FALLBACK),
	/** Its own slot rather than part of FACE so a player can mix any brows with any lips - two
	 *  exclusive slots, not one that they'd have to share. */
	EYEBROWS(true, Rect.EYEBROWS),
	LIPS(true, Rect.LIPS),
	HAIR(true, Rect.HEAD_ALL, Rect.HAT_ALL),
	/** Skeleton_Skull, masks, helmets - anything that replaces or covers the head silhouette. */
	HEAD(true, Rect.HEAD_ALL, Rect.HAT_ALL),
	TORSO(true, Rect.BODY, Rect.JACKET),
	/** Shirts cover the body AND both arms, which is why this is its own target rather than
	 *  TORSO: a sleeve is part of the same garment and has to be displaced with it. */
	SHIRT(true, Rect.BODY, Rect.JACKET, Rect.ARM_LEFT, Rect.ARM_RIGHT, Rect.SLEEVE_LEFT, Rect.SLEEVE_RIGHT),
	PANTS(true, Rect.LEG_LEFT, Rect.LEG_RIGHT, Rect.PANTS_LEFT, Rect.PANTS_RIGHT),
	/** Drawn after PANTS so a boot reads as being over the trouser leg. */
	SHOES(true, Rect.LEG_LEFT, Rect.LEG_RIGHT, Rect.PANTS_LEFT, Rect.PANTS_RIGHT),
	ARM_RIGHT(true, Rect.ARM_RIGHT, Rect.SLEEVE_RIGHT),
	ARM_LEFT(true, Rect.ARM_LEFT, Rect.SLEEVE_LEFT),
	LEG_RIGHT(true, Rect.LEG_RIGHT, Rect.PANTS_RIGHT),
	LEG_LEFT(true, Rect.LEG_LEFT, Rect.PANTS_LEFT),
	/** Non-exclusive on purpose - wings, tattoos, belts can stack. */
	ACCESSORY(false, Rect.WHOLE),
	/** Drawn last, over everything. Reserved for effects and full-body overlays. */
	OVERLAY(false, Rect.WHOLE);

	/** One axis-aligned box in 64x64 skin space. */
	public record Rect(int x, int y, int width, int height) {
		public static final Rect WHOLE = new Rect(0, 0, 64, 64);
		public static final Rect HEAD_ALL = new Rect(0, 0, 32, 16);
		public static final Rect HAT_ALL = new Rect(32, 0, 32, 16);
		public static final Rect HEAD_FRONT = new Rect(8, 8, 8, 8);
		public static final Rect EYES = new Rect(9, 11, 6, 2);
		/** Only used when NO pupil part is equipped (a vanilla-skinned player, or a base body
		 *  with no eyes at all). Any equipped pupil part uses ITS OWN painted shape instead -
		 *  see SkinCompositor's per-target pixel masks - which is what makes pupil size and
		 *  position a property of the texture rather than of this constant. */
		public static final Rect PUPIL_FALLBACK = new Rect(10, 12, 2, 1);
		public static final Rect EYEBROWS = new Rect(9, 10, 6, 1);
		public static final Rect LIPS = new Rect(10, 14, 4, 1);
		public static final Rect BODY = new Rect(16, 16, 24, 16);
		public static final Rect JACKET = new Rect(16, 32, 24, 16);
		public static final Rect ARM_RIGHT = new Rect(40, 16, 16, 16);
		public static final Rect SLEEVE_RIGHT = new Rect(40, 32, 16, 16);
		public static final Rect ARM_LEFT = new Rect(32, 48, 16, 16);
		public static final Rect SLEEVE_LEFT = new Rect(48, 48, 16, 16);
		public static final Rect LEG_RIGHT = new Rect(0, 16, 16, 16);
		public static final Rect PANTS_RIGHT = new Rect(0, 32, 16, 16);
		public static final Rect LEG_LEFT = new Rect(16, 48, 16, 16);
		public static final Rect PANTS_LEFT = new Rect(0, 48, 16, 16);

		public boolean contains(int px, int py) {
			return px >= x && py >= y && px < x + width && py < y + height;
		}

		public double centerX() {
			return x + width / 2.0;
		}

		public double centerY() {
			return y + height / 2.0;
		}
	}

	private final boolean exclusive;
	private final List<Rect> regions;

	SkinPartTarget(boolean exclusive, Rect... regions) {
		this.exclusive = exclusive;
		this.regions = List.of(regions);
	}

	/** True if equipping a part here unequips whatever was already in this slot. */
	public boolean isExclusive() {
		return exclusive;
	}

	public List<Rect> regions() {
		return regions;
	}

	public boolean containsPixel(int px, int py) {
		for (Rect rect : regions) {
			if (rect.contains(px, py))
				return true;
		}
		return false;
	}

	/** Composite order - see class doc RENDER ORDER. */
	public int order() {
		return ordinal();
	}

	/**
	 * The box an effect's spread is allowed to bleed into. Effects like vampirize grow outward
	 * from their target's regions, and skin UV layout is unforgiving about that: the pixels
	 * immediately left of the front face belong to the RIGHT SIDE of the head, so an unclamped
	 * radius around the eyes puts black smudges on the player's temples in 3D. Clamping to the
	 * containing FACE rather than to the region itself is what lets a spread look continuous on
	 * the surface it's actually drawn on.
	 */
	public Rect bleedBounds() {
		return switch (this) {
			case EYES, PUPIL, FACE, EYEBROWS, LIPS -> Rect.HEAD_FRONT;
			case HEAD, HAIR -> Rect.HEAD_ALL;
			case TORSO -> Rect.BODY;
			case ARM_RIGHT -> Rect.ARM_RIGHT;
			case ARM_LEFT -> Rect.ARM_LEFT;
			case LEG_RIGHT -> Rect.LEG_RIGHT;
			case LEG_LEFT -> Rect.LEG_LEFT;
			default -> Rect.WHOLE;
		};
	}
}
