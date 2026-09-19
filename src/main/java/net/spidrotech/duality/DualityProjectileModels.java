package net.spidrotech.duality;

import net.spidrotech.duality.client.model.Modelwebspit;
import net.spidrotech.duality.client.model.Modelfireball;
import net.spidrotech.duality.client.model.Modelacid_spit;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.resources.ResourceLocation;

@OnlyIn(Dist.CLIENT)
final class DualityProjectileModels {
	private DualityProjectileModels() {
	}

	static void registerAll() {
		ProjectileVisualRegistry.register(ResourceLocation.fromNamespaceAndPath("duality", "fireball"), Modelfireball.LAYER_LOCATION, Modelfireball::createBodyLayer, Modelfireball::new,
				ResourceLocation.parse("duality:textures/entities/fireball.png"));
		ProjectileVisualRegistry.register(ResourceLocation.fromNamespaceAndPath("duality", "acid_spit"), Modelacid_spit.LAYER_LOCATION, Modelacid_spit::createBodyLayer, Modelacid_spit::new,
				ResourceLocation.parse("duality:textures/entities/projectile_acidspit0.png"));
		// Wrapped so it points along its flight path. The pivot is the centre of Modelwebspit's box
		// (x -2.5..2.5, y 19..24, z -4..3 in pixels) so it turns in place. Flip yawOffset to 180
		// if it ever flies tail-first.
		ProjectileVisualRegistry.register(ResourceLocation.fromNamespaceAndPath("duality", "web_spit"), Modelwebspit.LAYER_LOCATION, Modelwebspit::createBodyLayer,
				root -> new DirectionalProjectileModel(new Modelwebspit<>(root), 0f, 21.5f, -0.5f, 0f), ResourceLocation.parse("duality:textures/entities/projectile_webspit.png"));
		// one block per Blockbench/MCreator model you export - copy/paste and change the id,
		// LAYER_LOCATION/createBodyLayer/::new triplet, and texture path
	}
}