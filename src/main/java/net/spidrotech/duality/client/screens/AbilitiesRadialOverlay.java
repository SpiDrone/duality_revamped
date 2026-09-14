package net.spidrotech.duality.client.screens;

import org.checkerframework.checker.units.qual.h;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;

@EventBusSubscriber(Dist.CLIENT)
public class AbilitiesRadialOverlay {
	private static boolean sua_radial_abilities_radial_open = false;
	private static net.spidrone.uiapi.UIRadialMenuElement.RadialWheel sua_radial_abilities_radial_wheel = null;

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void eventHandler(RenderGuiEvent.Pre event) {
		int w = event.getGuiGraphics().guiWidth();
		int h = event.getGuiGraphics().guiHeight();
		Level world = null;
		double x = 0;
		double y = 0;
		double z = 0;
		Player entity = Minecraft.getInstance().player;
		if (entity != null) {
			world = entity.level();
			x = entity.getX();
			y = entity.getY();
			z = entity.getZ();
		}
		if (true) {
			boolean sua_radial_abilities_radial_shouldOpen = true;
			if (sua_radial_abilities_radial_shouldOpen && !sua_radial_abilities_radial_open) {
				net.spidrone.uiapi.UIRadialMenuElement.RadialWheel wheel = new net.spidrone.uiapi.UIRadialMenuElement.RadialWheel();
				if (!wheel.getButtons().isEmpty()) {
					net.spidrone.uiapi.UIRadialMenuElement.openRadialMenu(wheel, wheel.getButtons().size());
					sua_radial_abilities_radial_wheel = wheel;
				}
				sua_radial_abilities_radial_open = true;
			} else if (!sua_radial_abilities_radial_shouldOpen && sua_radial_abilities_radial_open) {
				if (sua_radial_abilities_radial_wheel != null) {
					String sua_radial_abilities_radial_selection = net.spidrone.uiapi.UIRadialMenuElement.closeRadialMenuAndGetSelection();
					if (sua_radial_abilities_radial_selection != null) {
						net.spidrone.uiapi.RadialMenuNetwork.sendSelection("duality:radial_abilities_radial", sua_radial_abilities_radial_selection);
					}
				}
				sua_radial_abilities_radial_wheel = null;
				sua_radial_abilities_radial_open = false;
			}
			if (sua_radial_abilities_radial_open && sua_radial_abilities_radial_wheel != null) {
				int sua_radial_abilities_radial_radius = 60;
				net.spidrone.uiapi.UIRadialMenuElement.BorderEffects sua_radial_abilities_radial_border = net.spidrone.uiapi.UIRadialMenuElement.BorderEffects.of(1, net.spidrone.uiapi.UIColorEffects.solid(-16777216), 5,
						net.spidrone.uiapi.UIColorEffects.solid(-8781824));
				net.spidrone.uiapi.UIRadialMenuElement.drawRadialMenu(event.getGuiGraphics(), sua_radial_abilities_radial_wheel, sua_radial_abilities_radial_wheel.getButtons().size(), w / 2 + -60 + sua_radial_abilities_radial_radius,
						h / 2 + -60 + sua_radial_abilities_radial_radius, sua_radial_abilities_radial_radius, 21, sua_radial_abilities_radial_border, 0);
			}
		}
	}

	/**
	 * Resolves a radial wedge icon returned by a returnString procedure: a full "namespace:path" is used
	 * as-is, a bare name resolves under this mod's textures/screens/, and ".png" is added if the file name
	 * has no extension. Blank or invalid results fall back to the wedge's selected icon.
	 */
	private static net.minecraft.resources.ResourceLocation sua_resolveRadialIcon(String path, net.minecraft.resources.ResourceLocation fallback) {
		if (path == null || path.isBlank())
			return fallback;
		String resolved = path.trim();
		if (!resolved.contains(":"))
			resolved = "duality:textures/screens/" + resolved;
		if (!resolved.substring(resolved.lastIndexOf('/') + 1).contains("."))
			resolved += ".png";
		net.minecraft.resources.ResourceLocation location = net.minecraft.resources.ResourceLocation.tryParse(resolved);
		return location != null ? location : fallback;
	}
}