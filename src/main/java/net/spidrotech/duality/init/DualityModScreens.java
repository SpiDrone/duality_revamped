/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.spidrotech.duality.init;

import net.spidrotech.duality.client.gui.CharacterSelectorScreen;
import net.spidrotech.duality.client.gui.CharacterSelectorPage5Screen;
import net.spidrotech.duality.client.gui.CharacterSelectorPage4Screen;
import net.spidrotech.duality.client.gui.CharacterSelectorPage3Screen;
import net.spidrotech.duality.client.gui.CharacterSelectorPage2Screen;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(Dist.CLIENT)
public class DualityModScreens {
	@SubscribeEvent
	public static void clientLoad(RegisterMenuScreensEvent event) {
		event.register(DualityModMenus.CHARACTER_SELECTOR.get(), CharacterSelectorScreen::new);
		event.register(DualityModMenus.CHARACTER_SELECTOR_PAGE_2.get(), CharacterSelectorPage2Screen::new);
		event.register(DualityModMenus.CHARACTER_SELECTOR_PAGE_3.get(), CharacterSelectorPage3Screen::new);
		event.register(DualityModMenus.CHARACTER_SELECTOR_PAGE_4.get(), CharacterSelectorPage4Screen::new);
		event.register(DualityModMenus.CHARACTER_SELECTOR_PAGE_5.get(), CharacterSelectorPage5Screen::new);
	}

	public interface ScreenAccessor {
		void updateMenuState(int elementType, String name, Object elementState);
	}
}