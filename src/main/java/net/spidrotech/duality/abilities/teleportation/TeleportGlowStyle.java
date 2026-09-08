package net.spidrotech.duality.abilities.teleportation;

/**
 * Controls how a teleport marker's glow looks - color, and whether/how it pulses. WHITE is the
 * ability-agnostic default used for plain "you're looking at this" feedback while browsing; a
 * specific teleport ability can set its own style for the LOCKED/confirmed marker instead (see
 * TeleportPickerClientState#setGlowStyle) - e.g. whitelighter Orb uses PULSE_WHITELIGHTER.
 */
@FunctionalInterface
public interface TeleportGlowStyle {
	int colorAt(float timeSeconds);

	TeleportGlowStyle WHITE = solid(0xFFFFFFFF);

	/** Whitelighter-specific: pulses between white and cyan - "visual confirmation of where
	 *  you're teleporting to" once a destination is locked in. */
	TeleportGlowStyle PULSE_WHITELIGHTER = pulse(0xFFFFFFFF, 0xFF00FFFF, 0.6f);

	static TeleportGlowStyle solid(int argb) {
		return time -> argb;
	}

	static TeleportGlowStyle pulse(int colorA, int colorB, float speed) {
		return time -> {
			float t = (float) (0.5 + 0.5 * Math.sin(time * speed * Math.PI * 2));
			return lerpArgb(colorA, colorB, t);
		};
	}

	private static int lerpArgb(int a, int b, float t) {
		int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int ra = (int) (aa + (ba - aa) * t);
		int rr = (int) (ar + (br - ar) * t);
		int rg = (int) (ag + (bg - ag) * t);
		int rb = (int) (ab + (bb - ab) * t);
		return (ra << 24) | (rr << 16) | (rg << 8) | rb;
	}
}
