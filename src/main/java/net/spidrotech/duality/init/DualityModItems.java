/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.spidrotech.duality.init;

import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import net.minecraft.world.item.Item;

public class DualityModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(DualityMod.MODID);
	public static final DeferredItem<Item> LIGHTNING_VISUAL_SPAWN_EGG;
	public static final DeferredItem<Item> STAGNANT_VISUAL_SPAWN_EGG;
	static {
		LIGHTNING_VISUAL_SPAWN_EGG = REGISTRY.register("lightning_visual_spawn_egg", () -> new DeferredSpawnEggItem(DualityModEntities.LIGHTNING_VISUAL, -1, -1, new Item.Properties()));
		STAGNANT_VISUAL_SPAWN_EGG = REGISTRY.register("stagnant_visual_spawn_egg", () -> new DeferredSpawnEggItem(DualityModEntities.STAGNANT_VISUAL, -1, -1, new Item.Properties()));
	}
	// Start of user code block custom items
	// End of user code block custom items
}