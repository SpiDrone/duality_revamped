package net.spidrotech.duality.abilities.demon.client;

import net.spidrotech.duality.abilities.demon.ShimmerEffects;

import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;

/**
 * Supplies the actual 50%-70% alpha for the Shimmer effect. The other half is
 * mixin.LivingEntityRendererMixin, which forces a blending render type and reads the color this
 * class computes - see ShimmerAbility's class doc for why both halves are needed.
 *
 * WHY A VERTEX COLOR AND NOT RenderSystem.setShaderColor (this was the actual bug behind "the
 * opacity of the player does not change at all"): an earlier version of this class bracketed the
 * render call with setShaderColor(1,1,1,alpha) in Pre and reset it in Post. That cannot work.
 * Entity geometry is not drawn during render() at all - it is BUFFERED into a MultiBufferSource
 * and flushed to the GPU later, once the whole entity pass ends. By flush time Post had already
 * put the shader color back to opaque, so the draw used alpha 1.0 and looked exactly like no
 * effect at all (the mixin was applying correctly the whole time - the alpha simply never
 * survived to the draw). A vertex color, by contrast, is baked into the buffered vertices at
 * buffer time, so it is still there whenever the batch actually flushes.
 *
 * The 50%-70% swing is computed from the entity's own level game time, not a client-local clock
 * or anything synced over the network - every observer's client reaches the same instant off the
 * same already-synced game tick, so the shimmer phase agrees across every screen for free (same
 * technique client.TeleportMarkerRenderer already uses for its own animation timing).
 *
 * SCOPE: this colors the entity's main model. Armor and held items are drawn by separate
 * RenderLayers with their own buffer calls, so they stay opaque - fine for a caster in no armor,
 * and matching how far vanilla's own translucent-entity path reaches.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ShimmerRenderer {
	private static final float ALPHA_MID = 0.6f;
	private static final float ALPHA_SWING = 0.1f; // +/- 0.1 around the midpoint -> 50%-70%
	private static final float SHIMMER_HZ = 1.0f; // one full 50%<->70%<->50% cycle per second
	/** Never a legal shimmer color (those always carry a 0x80-0xB3 alpha and white RGB), so it
	 *  doubles as "no shimmering entity is mid-render right now". */
	private static final int INACTIVE = 0;

	/** Set for the duration of ONE shimmering entity's render call - Pre sets it, Post clears it,
	 *  and the mixin's renderToBuffer hook reads it in between. A plain static is enough because
	 *  rendering is single-threaded and LivingEntityRenderer#render is never nested inside itself. */
	private static int activeColor = INACTIVE;

	private ShimmerRenderer() {
	}

	@SubscribeEvent
	public static void onPre(RenderLivingEvent.Pre<?, ?> event) {
		LivingEntity entity = event.getEntity();
		if (!isShimmering(entity)) {
			activeColor = INACTIVE;
			return;
		}
		float gameTimeSeconds = ((float) entity.level().getGameTime() + event.getPartialTick()) / 20f;
		float alpha = ALPHA_MID + ALPHA_SWING * (float) Math.sin(gameTimeSeconds * SHIMMER_HZ * Math.PI * 2);
		// Floored well above the 0.1 alpha the entity shaders discard at, so a shimmering caster
		// can never flicker out entirely the way the old Invisibility-toggle version did.
		int alphaByte = Mth.clamp((int) (alpha * 255f), 32, 255);
		activeColor = (alphaByte << 24) | 0xFFFFFF;
	}

	@SubscribeEvent
	public static void onPost(RenderLivingEvent.Post<?, ?> event) {
		activeColor = INACTIVE;
	}

	public static boolean isShimmering(LivingEntity entity) {
		return entity.hasEffect(ShimmerEffects.SHIMMERING);
	}

	/** The packed ARGB the mixin should hand to renderToBuffer - the caller's own original color
	 *  whenever the entity being drawn isn't shimmering, so every other entity is untouched. */
	public static int shimmerColorOr(int fallback) {
		return activeColor == INACTIVE ? fallback : activeColor;
	}
}
