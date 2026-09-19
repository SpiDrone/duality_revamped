package net.spidrotech.duality.abilities.shapeshift;

import net.spidrotech.duality.DualityMod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import net.minecraft.resources.ResourceLocation;

/**
 * Permanent home for every shapeshift form, the same "one more call here" shape as
 * DualityProjectiles. Until this existed the shapeshift system had state, networking, commands and
 * a radial but nothing registered to shift into, so there was no way to see any of it work.
 *
 * <p>Forms are pure data and deliberately know nothing about how they look - the model and texture
 * for a form live client-side in ShapeshiftFormRenderers, keyed by the same id. A form with no
 * entry there still works, it just leaves the player looking like themselves.
 *
 * <p>To add a form: one constant and one registerForm call below, then an entry in
 * ShapeshiftFormRenderers if it should change how the player looks.
 */
@EventBusSubscriber(modid = "duality")
public final class DualityShapeshiftForms {
	public static final ResourceLocation DEMON_SPIDER = ResourceLocation.fromNamespaceAndPath(DualityMod.MODID, "demon_spider");

	private DualityShapeshiftForms() {
	}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(DualityShapeshiftForms::registerAll);
	}

	private static void registerAll() {
		// Low health and no item use: being a spider should be a movement/scouting shape, not a
		// strictly better way to fight. No requirement yet - anyone who can reach the radial can use
		// it, which is what makes it testable before the unlock chain exists.
		Shapeshift.registerForm(new ShapeshiftForm(DEMON_SPIDER, 0.75, false, true, true, 1.0f, ctx -> true));
	}
}
