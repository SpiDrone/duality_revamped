package net.spidrotech.duality.client;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.abilities.vampire.VampireStatus;
import net.spidrotech.duality.abilities.vampire.VampireRank;
import net.spidrotech.duality.abilities.vampire.VampireMode;
import net.spidrotech.duality.AbilitiesRadialHandler;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;

import net.spidrone.uiapi.UIRadialMenuElement;
import net.spidrone.uiapi.UIColorEffects;

/**
 * Wedges the ability radial gets on top of the ones built in MCreator (AbilitiesRadialOverlay), for
 * wedges whose icon or visibility depends on state MCreator's radial can't express:
 * <ul>
 * <li><b>Vampirize / Humanize</b> - every vampire has exactly one of them: the open eye while
 * they're passing as human, the closed eye while the vampire is out. Gold eyes for a Vegan.
 * <li><b>Deselect</b> - whenever a power is selected, to clear it, so a stray click can't fire it.
 * </ul>
 * Picking one is handled in AbilitiesRadialHandler.
 *
 * <p>WIRING: the MCreator overlay calls {@link #addTo} right before it opens the wheel, and
 * {@link #tooltip} from its hovered-wedge switch's default case. If MCreator regenerates the overlay
 * those two calls are lost - lock the AbilitiesRadial element's code, or re-add them.
 */
@OnlyIn(Dist.CLIENT)
public final class RadialExtras {
	private static final ResourceLocation EYE = icon("icon_ability_vampire_eye"), EYE_CLOSED = icon("icon_ability_vampire_eye_closed");
	private static final ResourceLocation VEGAN_EYE = icon("icon_ability_vegan_vampire_eye"), VEGAN_EYE_CLOSED = icon("icon_ability_vegan_vampire_eye_closed");
	private static final ResourceLocation DESELECT = icon("icon_ability_deselect");
	private static final ResourceLocation FONT = ResourceLocation.parse("spis_ui_api:boldpixels");

	private RadialExtras() {
	}

	private static ResourceLocation icon(String name) {
		return ResourceLocation.fromNamespaceAndPath("duality", "textures/screens/" + name + ".png");
	}

	/** Adds whichever of these wedges apply to this player right now. */
	public static void addTo(UIRadialMenuElement.RadialWheel wheel, Player player) {
		if (player == null)
			return;
		if (VampireRank.isVampire(player)) {
			boolean vegan = VampireStatus.clientIsVegan();
			boolean out = VampireMode.isActive(player);
			int glow = vegan ? 0xFFE8B21E : 0xFFCC0000;
			wheel.add(out ? AbilitiesRadialHandler.HUMANIZE : AbilitiesRadialHandler.VAMPIRIZE, UIColorEffects.holographic(-8355712, -12566464),
					UIColorEffects.holographic(glow, -10092544), out ? (vegan ? VEGAN_EYE_CLOSED : EYE_CLOSED) : (vegan ? VEGAN_EYE : EYE), UIColorEffects.solid(0xFFFFFFFF),
					UIColorEffects.solid(0xFFFFFFFF));
		}
		if (hasSelection(player))
			wheel.add(AbilitiesRadialHandler.DESELECT, UIColorEffects.solid(-8355712), UIColorEffects.solid(-5592406), DESELECT, UIColorEffects.solid(0xFFFFFFFF),
					UIColorEffects.solid(0xFFFFFFFF));
	}

	/** Whether a power is selected - MCreator's default for the variable is the two characters "". */
	private static boolean hasSelection(Player player) {
		String selected = player.getData(DualityModVariables.PLAYER_VARIABLES).selected_ability;
		return selected != null && !selected.isBlank() && !selected.equals("\"\"");
	}

	/** Draws the tooltip for one of these wedges if that's what's hovered. */
	public static void tooltip(GuiGraphics g, UIRadialMenuElement.RadialWheel wheel, String hovered, int centerX, int centerY, int radius) {
		String title = switch (hovered) {
			case AbilitiesRadialHandler.VAMPIRIZE -> "Vampirize";
			case AbilitiesRadialHandler.HUMANIZE -> "Humanize";
			case AbilitiesRadialHandler.DESELECT -> "Deselect";
			default -> null;
		};
		if (title == null)
			return;
		int color = AbilitiesRadialHandler.DESELECT.equals(hovered) ? -3355444 : VampireStatus.clientIsVegan() ? 0xFFE8B21E : 0xFFCC0000;
		wheel.setTooltipLines(hovered, UIRadialMenuElement.TooltipLine.of(title, FONT, color));
		UIRadialMenuElement.wedgeHoveredShowTooltip(g, wheel, wheel.getButtons().size(), centerX, centerY, radius, ResourceLocation.parse("minecraft:default"), null, -267386864,
				-10066262, -1);
	}
}
