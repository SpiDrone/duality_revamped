package net.spidrotech.duality.client.screens;

import org.checkerframework.checker.units.qual.h;

import net.spidrotech.duality.procedures.*;

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
	static {
		net.spidrone.uiapi.RadialMenuNetwork.registerHandler("duality:radial_abilities_radial", (serverPlayer, selectionId) -> {
			net.minecraft.world.entity.Entity entity = serverPlayer;
			double x = entity.getX();
			double y = entity.getY();
			double z = entity.getZ();
			net.minecraft.world.level.Level world = entity.level();
			switch (selectionId) {
				case "fireball_normal" -> {
					if (RetFireballNormalProcedure.execute(entity)) {
						String radial_value = "fireball_normal";

						AbilitiesRadialSelectProcedure.execute(entity, radial_value);
					}
				}
				case "fireball_greater" -> {
					if (RetFireballGreaterProcedure.execute(entity)) {
						String radial_value = "fireball_greater";

						AbilitiesRadialSelectProcedure.execute(entity, radial_value);
					}
				}
				case "firebolt" -> {
					if (RetFireboltProcedure.execute(entity)) {
						String radial_value = "firebolt";

						AbilitiesRadialSelectProcedure.execute(entity, radial_value);
					}
				}
				case "web_spit" -> {
					if (RetWebSpitProcedure.execute(entity)) {
						String radial_value = "web_spit";

						AbilitiesRadialSelectProcedure.execute(entity, radial_value);
					}
				}
				case "acid_spit" -> {
					if (RetAcidSpitProcedure.execute(entity)) {
						String radial_value = "acid_spit";

						AbilitiesRadialSelectProcedure.execute(entity, radial_value);
					}
				}
				case "lightning_hands_normal" -> {
					if (RetLightningHandsProcedure.execute(entity)) {
						String radial_value = "lightning_hands_normal";

						AbilitiesRadialSelectProcedure.execute(entity, radial_value);
					}
				}
				case "lightning_hands_demonic" -> {
					if (RetDemLightningHProcedure.execute(entity)) {
						String radial_value = "lightning_hands_demonic";

						AbilitiesRadialSelectProcedure.execute(entity, radial_value);
					}
				}
				default -> {
				}
			}
		});
	}

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
			boolean sua_radial_abilities_radial_shouldOpen = RadialKeybindProcedure.execute();
			if (sua_radial_abilities_radial_shouldOpen && !sua_radial_abilities_radial_open) {
				net.spidrone.uiapi.UIRadialMenuElement.RadialWheel wheel = new net.spidrone.uiapi.UIRadialMenuElement.RadialWheel();
				if (RetFireballNormalProcedure.execute(entity))
					wheel.add("fireball_normal", net.spidrone.uiapi.UIColorEffects.holographic(-8355712, -12566464), net.spidrone.uiapi.UIColorEffects.holographic(-52429, -10092544),
							net.minecraft.resources.ResourceLocation.parse("duality:textures/screens/icon_ability_fireball_medium.png"), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF));
				if (RetFireballGreaterProcedure.execute(entity))
					wheel.add("fireball_greater", net.spidrone.uiapi.UIColorEffects.solid(-8355712), net.spidrone.uiapi.UIColorEffects.holographic(-65536, -26368),
							net.minecraft.resources.ResourceLocation.parse("duality:textures/screens/icon_ability_fireball_large.png"), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF));
				if (RetFireboltProcedure.execute(entity))
					wheel.add("firebolt", net.spidrone.uiapi.UIColorEffects.solid(-8355712), net.spidrone.uiapi.UIColorEffects.solid(-65536), net.minecraft.resources.ResourceLocation.parse("duality:textures/screens/icon_ability_fireball_small.png"),
							net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF));
				if (RetWebSpitProcedure.execute(entity))
					wheel.add("web_spit", net.spidrone.uiapi.UIColorEffects.holographic(-8355712, -12566464), net.spidrone.uiapi.UIColorEffects.holographic(-1, -4144960),
							net.minecraft.resources.ResourceLocation.parse("duality:textures/screens/icon_ability_web_spit.png"), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF));
				if (RetAcidSpitProcedure.execute(entity))
					wheel.add("acid_spit", net.spidrone.uiapi.UIColorEffects.holographic(-8355712, -12566464), net.spidrone.uiapi.UIColorEffects.holographic(-16711850, -393472),
							net.minecraft.resources.ResourceLocation.parse("duality:textures/screens/icon_ability_acid_spit.png"), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF));
				if (RetLightningHandsProcedure.execute(entity))
					wheel.add("lightning_hands_normal", net.spidrone.uiapi.UIColorEffects.holographic(-8355712, -12566464), net.spidrone.uiapi.UIColorEffects.holographic(-1, -7680),
							net.minecraft.resources.ResourceLocation.parse("duality:textures/screens/icon_ability_lightning_hands_0.png"), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF));
				if (RetDemLightningHProcedure.execute(entity))
					wheel.add("lightning_hands_demonic", net.spidrone.uiapi.UIColorEffects.holographic(-8355712, -12566464), net.spidrone.uiapi.UIColorEffects.holographic(-1, -65536),
							net.minecraft.resources.ResourceLocation.parse("duality:textures/screens/icon_ability_lightning_hands_1.png"), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF), net.spidrone.uiapi.UIColorEffects.solid(0xFFFFFFFF));
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
						net.spidrone.uiapi.UIColorEffects.holographic(-65281, -10092442));
				net.spidrone.uiapi.UIRadialMenuElement.drawRadialMenu(event.getGuiGraphics(), sua_radial_abilities_radial_wheel, sua_radial_abilities_radial_wheel.getButtons().size(), w / 2 + -60 + sua_radial_abilities_radial_radius,
						h / 2 + -60 + sua_radial_abilities_radial_radius, sua_radial_abilities_radial_radius, 21, sua_radial_abilities_radial_border, 0);
				String sua_radial_abilities_radial_hovered = sua_radial_abilities_radial_wheel.isHoverActive() ? sua_radial_abilities_radial_wheel.getSelectedButtonId() : null;
				if (sua_radial_abilities_radial_hovered != null) {
					switch (sua_radial_abilities_radial_hovered) {
						case "fireball_normal" -> {
							long sua_tt_time = System.currentTimeMillis();
							sua_radial_abilities_radial_wheel.setTooltipLines("fireball_normal",
									net.spidrone.uiapi.UIRadialMenuElement.TooltipLine.of("Fire Ball", net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"),
											((net.spidrone.uiapi.UIColorEffects.holographic(-65536, -3381760).colorAt(0, 1, 0f, sua_tt_time) & 0x00FFFFFF) | (-65536 & 0xFF000000))),
									net.spidrone.uiapi.UIRadialMenuElement.TooltipLine.of("Dmg: §4██", -6710887), net.spidrone.uiapi.UIRadialMenuElement.TooltipLine.of("Element: Flame", -39424));
							net.spidrone.uiapi.UIRadialMenuElement.wedgeHoveredShowTooltip(event.getGuiGraphics(), sua_radial_abilities_radial_wheel, sua_radial_abilities_radial_wheel.getButtons().size(),
									w / 2 + -60 + sua_radial_abilities_radial_radius, h / 2 + -60 + sua_radial_abilities_radial_radius, sua_radial_abilities_radial_radius + 0, net.minecraft.resources.ResourceLocation.parse("minecraft:default"),
									net.minecraft.resources.ResourceLocation.parse("duality:textures/screens/tooltip_background_flame.png"), -6710887, -3407668, -1);
						}
						case "fireball_greater" -> {
							long sua_tt_time = System.currentTimeMillis();
							sua_radial_abilities_radial_wheel.setTooltipLines("fireball_greater", net.spidrone.uiapi.UIRadialMenuElement.TooltipLine.of("Infero Blast", net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"),
									((net.spidrone.uiapi.UIColorEffects.holographic(-205, -26317).colorAt(0, 1, 0f, sua_tt_time) & 0x00FFFFFF) | (-205 & 0xFF000000))));
							net.spidrone.uiapi.UIRadialMenuElement.wedgeHoveredShowTooltip(event.getGuiGraphics(), sua_radial_abilities_radial_wheel, sua_radial_abilities_radial_wheel.getButtons().size(),
									w / 2 + -60 + sua_radial_abilities_radial_radius, h / 2 + -60 + sua_radial_abilities_radial_radius, sua_radial_abilities_radial_radius + 0, net.minecraft.resources.ResourceLocation.parse("minecraft:default"), null,
									-267386864, -10066262, -1);
						}
						case "firebolt" -> {
							long sua_tt_time = System.currentTimeMillis();
							sua_radial_abilities_radial_wheel.setTooltipLines("firebolt", net.spidrone.uiapi.UIRadialMenuElement.TooltipLine.of("Firebolt", net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"), -26880));
							net.spidrone.uiapi.UIRadialMenuElement.wedgeHoveredShowTooltip(event.getGuiGraphics(), sua_radial_abilities_radial_wheel, sua_radial_abilities_radial_wheel.getButtons().size(),
									w / 2 + -60 + sua_radial_abilities_radial_radius, h / 2 + -60 + sua_radial_abilities_radial_radius, sua_radial_abilities_radial_radius + 0, net.minecraft.resources.ResourceLocation.parse("minecraft:default"), null,
									-263978237, -10066262, -1);
						}
						case "web_spit" -> {
							long sua_tt_time = System.currentTimeMillis();
							sua_radial_abilities_radial_wheel.setTooltipLines("web_spit", net.spidrone.uiapi.UIRadialMenuElement.TooltipLine.of("Web Spit", net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"), -6181449));
							net.spidrone.uiapi.UIRadialMenuElement.wedgeHoveredShowTooltip(event.getGuiGraphics(), sua_radial_abilities_radial_wheel, sua_radial_abilities_radial_wheel.getButtons().size(),
									w / 2 + -60 + sua_radial_abilities_radial_radius, h / 2 + -60 + sua_radial_abilities_radial_radius, sua_radial_abilities_radial_radius + 0, net.minecraft.resources.ResourceLocation.parse("minecraft:default"), null,
									-263304370, -10066262, -1);
						}
						case "acid_spit" -> {
							long sua_tt_time = System.currentTimeMillis();
							sua_radial_abilities_radial_wheel.setTooltipLines("acid_spit", net.spidrone.uiapi.UIRadialMenuElement.TooltipLine.of("Acid Spit", net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"),
									((net.spidrone.uiapi.UIColorEffects.holographic(-16711900, -393472).colorAt(0, 1, 0f, sua_tt_time) & 0x00FFFFFF) | (-16711900 & 0xFF000000))));
							net.spidrone.uiapi.UIRadialMenuElement.wedgeHoveredShowTooltip(event.getGuiGraphics(), sua_radial_abilities_radial_wheel, sua_radial_abilities_radial_wheel.getButtons().size(),
									w / 2 + -60 + sua_radial_abilities_radial_radius, h / 2 + -60 + sua_radial_abilities_radial_radius, sua_radial_abilities_radial_radius + 0, net.minecraft.resources.ResourceLocation.parse("minecraft:default"),
									net.minecraft.resources.ResourceLocation.parse("duality:textures/screens/tooltip_background_acid.png"), -4934476, -10066262, -1);
						}
						case "lightning_hands_normal" -> {
							long sua_tt_time = System.currentTimeMillis();
							sua_radial_abilities_radial_wheel.setTooltipLines("lightning_hands_normal", net.spidrone.uiapi.UIRadialMenuElement.TooltipLine.of("Lighting Strike", net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"),
									((net.spidrone.uiapi.UIColorEffects.holographic(-1, -393472).colorAt(0, 1, 0f, sua_tt_time) & 0x00FFFFFF) | (-1 & 0xFF000000))));
							net.spidrone.uiapi.UIRadialMenuElement.wedgeHoveredShowTooltip(event.getGuiGraphics(), sua_radial_abilities_radial_wheel, sua_radial_abilities_radial_wheel.getButtons().size(),
									w / 2 + -60 + sua_radial_abilities_radial_radius, h / 2 + -60 + sua_radial_abilities_radial_radius, sua_radial_abilities_radial_radius + 0, net.minecraft.resources.ResourceLocation.parse("minecraft:default"), null,
									-268171709, ((net.spidrone.uiapi.UIColorEffects.holographic(-5376, -1).colorAt(0, 1, 0f, sua_tt_time) & 0x00FFFFFF) | (-5376 & 0xFF000000)), -1);
						}
						case "lightning_hands_demonic" -> {
							long sua_tt_time = System.currentTimeMillis();
							sua_radial_abilities_radial_wheel.setTooltipLines("lightning_hands_demonic", net.spidrone.uiapi.UIRadialMenuElement.TooltipLine.of("Demonic Strike", net.minecraft.resources.ResourceLocation.parse("duality:boldpixels"),
									((net.spidrone.uiapi.UIColorEffects.holographic(-65536, -1).colorAt(0, 1, 0f, sua_tt_time) & 0x00FFFFFF) | (-65536 & 0xFF000000))));
							net.spidrone.uiapi.UIRadialMenuElement.wedgeHoveredShowTooltip(event.getGuiGraphics(), sua_radial_abilities_radial_wheel, sua_radial_abilities_radial_wheel.getButtons().size(),
									w / 2 + -60 + sua_radial_abilities_radial_radius, h / 2 + -60 + sua_radial_abilities_radial_radius, sua_radial_abilities_radial_radius + 0, net.minecraft.resources.ResourceLocation.parse("minecraft:default"), null,
									-268304368, ((net.spidrone.uiapi.UIColorEffects.holographic(-65536, -1).colorAt(0, 1, 0f, sua_tt_time) & 0x00FFFFFF) | (-65536 & 0xFF000000)), -1);
						}
						default -> {
						}
					}
				}
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