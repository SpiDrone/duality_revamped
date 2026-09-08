package net.spidrotech.duality;

import org.lwjgl.glfw.GLFW;

import net.spidrotech.duality.RadialAbilityNetwork.RadialMenuSelectionPayload;

import net.spidrone.uiapi.UIRadialMenuElement.RadialWheel;
import net.spidrone.uiapi.UIRadialMenuElement.BorderEffects;
import net.spidrone.uiapi.UIRadialMenuElement;
import net.spidrone.uiapi.UIColorEffects;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public class RadialAbilitySelector {
	// Border Colors
	private static final int BORDER_BLACK = 0xFF000000;
	private static final int BORDER_DARK_RED = 0xFF7A0000;
	// Segment Colors
	private static final int ORANGE = 0xFFFFA500;
	private static final int RED = 0xFFFF0000;
	private static final int PULSE_RED = 0xFFFF5555; // Slightly lighter red for the pulse contrast

	@SubscribeEvent
	public static void onKeyInput(InputEvent.Key event) {
		int key = event.getKey();
		int action = event.getAction();
		// Guard against OS/GLFW key repeat events overwriting states mid-tick
		if (action == GLFW.GLFW_REPEAT) {
			return;
		}
		// Using LEFT_ALT to open the ability radial menu
		if (key == GLFW.GLFW_KEY_LEFT_ALT) {
			if (action == GLFW.GLFW_PRESS) {
				if (!UIRadialMenuElement.isRadialMenuOpen()) {
					UIRadialMenuElement.openRadialMenu(buildAbilityWheel(), 5);
				}
			} else if (action == GLFW.GLFW_RELEASE) {
				if (UIRadialMenuElement.isRadialMenuOpen()) {
					String selection = UIRadialMenuElement.closeRadialMenuAndGetSelection();
					if (selection != null) {
						// Send the selected ability string to the server via our network payload
						PacketDistributor.sendToServer(new RadialMenuSelectionPayload(selection));
					}
				}
			}
		}
	}

	private static RadialWheel buildAbilityWheel() {
		RadialWheel wheel = new RadialWheel();
		// Gradient from orange to red for unselected, pulsing red when selected
		UIColorEffects.ColorEffect unselected = UIColorEffects.gradient(ORANGE, RED);
		UIColorEffects.ColorEffect selected = UIColorEffects.pulse(RED, PULSE_RED);
		//UIColorEffects.ColorEffect unselectedEnergy = 0x19273600;
		// Add the abilities passing their respective IDs and icons
		// note from editor, this wheel has a max of 5 abilities
		wheel.add("orb", unselected, selected, ResourceLocation.fromNamespaceAndPath("duality", "textures/screens/icon_ability_orb_0.png"));
		wheel.add("lightning_hands", unselected, selected, ResourceLocation.fromNamespaceAndPath("duality", "textures/screens/icon_ability_lightning_hands_0.png"));
		wheel.add("demonic_lightning_hands", unselected, selected, ResourceLocation.fromNamespaceAndPath("duality", "textures/screens/icon_ability_lightning_hands_1.png"));
		wheel.add("acid_spit", unselected, selected, ResourceLocation.fromNamespaceAndPath("duality", "textures/screens/icon_ability_acid_spit.png"));
		wheel.add("fireball", unselected, selected, ResourceLocation.fromNamespaceAndPath("duality", "textures/screens/icon_ability_fireball_small.png"));
		wheel.add("fireball_greater", unselected, selected, ResourceLocation.fromNamespaceAndPath("duality", "textures/screens/icon_ability_fireball_medium.png"));
		//wheel.add("fireball_inferno", unselected, selected, ResourceLocation.fromNamespaceAndPath("duality", "textures/screens/icon_ability_fireball_large.png"));
		return wheel;
	}

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void eventHandler(RenderGuiEvent.Pre event) {
		if (!UIRadialMenuElement.isRadialMenuOpen()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		GuiGraphics graphics = event.getGuiGraphics();
		int centerX = mc.getWindow().getGuiScaledWidth() / 2;
		int centerY = mc.getWindow().getGuiScaledHeight() / 2;
		// Updated radius and iconSize for perfect 21px centering
		int radius = 60;
		int iconSize = 21;
		// 1px black outline, 5px dark red band inside it
		BorderEffects border = BorderEffects.of(1, UIColorEffects.solid(BORDER_BLACK), 5, UIColorEffects.solid(BORDER_DARK_RED));
		UIRadialMenuElement.drawRadialMenu(graphics, UIRadialMenuElement.activeWheel, UIRadialMenuElement.activeMaxButtons, centerX, centerY, radius, iconSize, border);
	}
}