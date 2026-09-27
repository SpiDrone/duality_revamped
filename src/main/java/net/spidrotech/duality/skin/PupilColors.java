package net.spidrotech.duality.skin;

import java.awt.Color;

/**
 * Which colours a player may tint their pupils. Black, red and near-white are held back - they
 * read as a vampire's or something else's eyes, not a person's - so the character creator's picker
 * greys them out and SkinNetwork refuses them outright if a client sends one anyway. One rule, used
 * by both sides, so the picker can never offer something the server then rejects.
 */
public final class PupilColors {
	private static final float MIN_BRIGHTNESS = 0.25f;
	private static final float WHITE_MAX_SATURATION = 0.15f;
	private static final float WHITE_MIN_BRIGHTNESS = 0.85f;
	private static final float RED_MIN_SATURATION = 0.35f;
	private static final float RED_HUE_WIDTH = 15f;

	private PupilColors() {
	}

	/** -1 means "the texture's own colour" (see SkinLoadout.Equipped) and is always allowed. */
	public static boolean isAllowed(int argb) {
		if (argb == -1)
			return true;
		float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
		return isAllowed(hsb[0], hsb[1], hsb[2]);
	}

	/** hue 0..1, saturation 0..1, brightness 0..1. */
	public static boolean isAllowed(float hue, float saturation, float brightness) {
		if (brightness < MIN_BRIGHTNESS)
			return false;
		if (saturation < WHITE_MAX_SATURATION && brightness > WHITE_MIN_BRIGHTNESS)
			return false;
		float degrees = hue * 360f;
		return !(saturation > RED_MIN_SATURATION && (degrees < RED_HUE_WIDTH || degrees > 360f - RED_HUE_WIDTH));
	}
}
