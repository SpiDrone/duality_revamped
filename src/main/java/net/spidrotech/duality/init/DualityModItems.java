/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.spidrotech.duality.init;

import net.spidrotech.duality.procedures.ReturnAthameBloodLevelProcedure;
import net.spidrotech.duality.item.AthameItem;
import net.spidrotech.duality.item.AbilityItemItem;
import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.item.ItemProperties;

public class DualityModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(DualityMod.MODID);
	public static final DeferredItem<Item> LIGHTNING_VISUAL_SPAWN_EGG;
	public static final DeferredItem<Item> STAGNANT_VISUAL_SPAWN_EGG;
	public static final DeferredItem<Item> SPIDER_QUEEN_SPAWN_EGG;
	public static final DeferredItem<Item> ATHAME;
	public static final DeferredItem<Item> ABILITY_ITEM;
	static {
		LIGHTNING_VISUAL_SPAWN_EGG = REGISTRY.register("lightning_visual_spawn_egg", () -> new DeferredSpawnEggItem(DualityModEntities.LIGHTNING_VISUAL, -1, -1, new Item.Properties()));
		STAGNANT_VISUAL_SPAWN_EGG = REGISTRY.register("stagnant_visual_spawn_egg", () -> new DeferredSpawnEggItem(DualityModEntities.STAGNANT_VISUAL, -1, -1, new Item.Properties()));
		SPIDER_QUEEN_SPAWN_EGG = REGISTRY.register("spider_queen_spawn_egg", () -> new DeferredSpawnEggItem(DualityModEntities.SPIDER_QUEEN, -1, -1, new Item.Properties()));
		ATHAME = REGISTRY.register("athame", AthameItem::new);
		ABILITY_ITEM = REGISTRY.register("ability_item", AbilityItemItem::new);
	}

	// Start of user code block custom items
	// End of user code block custom items
	@EventBusSubscriber(Dist.CLIENT)
	public static class ItemsClientSideHandler {
		@SubscribeEvent
		@OnlyIn(Dist.CLIENT)
		public static void clientLoad(FMLClientSetupEvent event) {
			event.enqueueWork(() -> {
				ItemProperties.register(ATHAME.get(), ResourceLocation.parse("duality:athame_blood"), (itemStackToRender, clientWorld, entity, itemEntityId) -> (float) ReturnAthameBloodLevelProcedure.execute(itemStackToRender));
			});
		}
	}
}