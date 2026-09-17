package net.spidrotech.duality.abilities.shapeshift.client;

import net.spidrotech.duality.abilities.shapeshift.ShapeshiftForm;
import net.spidrotech.duality.abilities.shapeshift.Shapeshift;

import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.Minecraft;

/**
 * First-person side of shapeshifting. Only FOV today, driven by the form's fovMultiplier; a real
 * per-form viewmodel/camera (e.g. bat wings in view) would hang off the same current-form lookup
 * but needs render work that isn't attempted here.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ShapeshiftClient {
	private ShapeshiftClient() {
	}

	@SubscribeEvent
	public static void onComputeFov(ViewportEvent.ComputeFov event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null)
			return;
		float multiplier = Shapeshift.current(mc.player).map(ShapeshiftForm::fovMultiplier).orElse(1.0f);
		if (multiplier != 1.0f)
			event.setFOV(event.getFOV() * multiplier);
	}
}
