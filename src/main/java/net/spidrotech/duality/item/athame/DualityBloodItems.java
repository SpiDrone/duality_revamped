package net.spidrotech.duality.item.athame;

import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

/**
 * Hand-written items that go with the athame. Registered from RegisterEvent rather than through
 * DualityModItems, which MCreator regenerates.
 */
@EventBusSubscriber(modid = "duality")
public final class DualityBloodItems {
	public static final ResourceLocation BLOOD_VIAL_ID = ResourceLocation.fromNamespaceAndPath("duality", "blood_vial");
	public static final DeferredHolder<Item, BloodVialItem> BLOOD_VIAL = DeferredHolder.create(Registries.ITEM, BLOOD_VIAL_ID);

	private DualityBloodItems() {
	}

	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
		event.register(Registries.ITEM, BLOOD_VIAL_ID, BloodVialItem::new);
	}
}
