package net.spidrotech.duality.skin;

import net.minecraft.resources.ResourceLocation;

/**
 * Effect IDs, kept in common code so ability logic on the server can name an effect without
 * touching the client-only implementation that actually pushes pixels around. The server never
 * knows what an effect LOOKS like or how long it runs - it only stamps the id and a start time
 * into a TempSkinModification (see SkinTempModify) and lets each client draw it.
 *
 * That split is what makes effects resourcepack-friendly later: a client with a different
 * implementation registered under the same id renders it differently, and nothing about the
 * server's gameplay logic changes.
 *
 * Registering a new one: add the id here, add the implementation in
 * client.ClientSkinEffects#bootstrap.
 */
public final class SkinEffects {
	private SkinEffects() {
	}

	/** Skin around the target region darkens to black, spreading outward over ~0.75s, then
	 *  holds. See client.ClientSkinEffects. */
	public static final ResourceLocation VAMPIRIZE = ResourceLocation.fromNamespaceAndPath("duality", "vampirize");

	/** Region drains to greyscale - a starting point for possession/soulless looks. */
	public static final ResourceLocation DESATURATE = ResourceLocation.fromNamespaceAndPath("duality", "desaturate");
}
