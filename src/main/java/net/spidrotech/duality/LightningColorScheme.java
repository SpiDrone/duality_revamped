package net.spidrotech.duality;

/**
 * Reusable color configuration for procedural lightning rendering - inner (the bright core),
 * outer (currently unused by the renderer, reserved for a future mid-layer pass), and glow (a
 * wide, soft additive halo). All three are packed ARGB ints (0xAARRGGBB), same convention as
 * vanilla text/particle colors.
 *
 * Example - classic blue-white lightning:
 *   new LightningColorScheme(0xFFFFFFFF, 0xFF99CCFF, 0xFF3388FF)
 *
 * Example - reused for a different ability, e.g. a "shadow bolt":
 *   new LightningColorScheme(0xFFDDBBFF, 0xFF6622AA, 0xFF220044)
 */
public record LightningColorScheme(int innerColor, int outerColor, int glowColor) {
	public static LightningColorScheme classicBlue() {
		return new LightningColorScheme(0xFFFFFFFF, 0xFF99CCFF, 0xFF3388FF);
	}
}