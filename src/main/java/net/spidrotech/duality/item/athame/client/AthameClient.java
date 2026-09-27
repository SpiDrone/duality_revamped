package net.spidrotech.duality.item.athame.client;

import net.spidrotech.duality.item.athame.StoredPower;
import net.spidrotech.duality.item.athame.DualityBloodItems;
import net.spidrotech.duality.item.athame.BloodSample;
import net.spidrotech.duality.item.athame.AthameTooltips;
import net.spidrotech.duality.item.athame.AthameData;
import net.spidrotech.duality.init.DualityModItems;

import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The athame's tooltip (what's in the hilt, what's in the blade) and the blood vial's colour.
 * Hooked by event rather than on AthameItem itself, because MCreator regenerates that class.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class AthameClient {
	private static final int RED = 0xFF8A0303, GREEN = 0xFF4E9A1E;

	private AthameClient() {
	}

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		if (!event.getItemStack().is(DualityModItems.ATHAME.get()))
			return;
		List<Component> lines = new ArrayList<>();
		BloodSample blood = AthameData.blood(event.getItemStack());
		StoredPower power = AthameData.power(event.getItemStack());
		if (power != null)
			AthameTooltips.describePower(power, lines);
		if (blood != null)
			AthameTooltips.describeBlood(blood, lines);
		// Right under the name, above the attack stats.
		event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
	}

	@SubscribeEvent
	public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
		event.register((stack, tintIndex) -> {
			if (tintIndex != 0)
				return 0xFFFFFFFF;
			BloodSample sample = AthameData.blood(stack);
			return sample != null && sample.color() == BloodSample.GREEN ? GREEN : RED;
		}, DualityBloodItems.BLOOD_VIAL.get());
	}
}
