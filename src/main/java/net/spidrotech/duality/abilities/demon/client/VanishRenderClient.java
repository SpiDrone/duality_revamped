package net.spidrotech.duality.abilities.demon.client;

import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.effect.MobEffects;

/**
 * Fixes the well-known vanilla gap in Invisibility: the body disappears but worn armor still
 * renders. Cancelling the whole player render (model, armor, held items, name tag - all part of
 * the same render call) skips every piece at once instead of patching armor specifically.
 *
 * Checks the vanilla effect directly rather than VanishAbility.isVanished (currently the same
 * check) - MobEffectInstance already syncs to every client tracking the entity, so this needs no
 * networking of its own, and it also fixes the same gap for a plain invisibility potion, not just
 * the Vanish ability. If a potion should ever behave differently from Vanish, this is the one
 * place to add that distinction.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class VanishRenderClient {
	private VanishRenderClient() {
	}

	@SubscribeEvent
	public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
		if (event.getEntity().hasEffect(MobEffects.INVISIBILITY)) {
			event.setCanceled(true);
		}
	}
}
