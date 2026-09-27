package net.spidrotech.duality.client.gui;

import net.spidrotech.duality.world.inventory.CharacterSelectorPage3Menu;
import net.spidrotech.duality.procedures.*;
import net.spidrotech.duality.init.DualityModScreens;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphics;

import com.mojang.blaze3d.systems.RenderSystem;

public class CharacterSelectorPage3Screen extends AbstractContainerScreen<CharacterSelectorPage3Menu> implements DualityModScreens.ScreenAccessor {
	private final Level world;
	private final int x, y, z;
	private final Player entity;
	private boolean menuStateUpdateActive = false;
	private final java.util.List<net.spidrone.uiapi.UIScrollButtonWidget> sua_scroll_widgets = new java.util.ArrayList<>();
	static {
		net.spidrone.uiapi.UIButtonElement.registerServerHandler("duality:race_scroll", (serverPlayer, selectionId) -> {
			net.minecraft.world.entity.Entity entity = serverPlayer;
			double x = entity.getX();
			double y = entity.getY();
			double z = entity.getZ();
			net.minecraft.world.level.Level world = entity.level();
			switch (selectionId) {
				case "human" -> {
					if (true) {
						String item_value = "race_human";
						SelectRaceProcedure.execute(item_value);
					}
				}
				case "demon" -> {
					if (HasDemonProcedure.execute()) {
						String item_value = "race_demon";
						SelectRaceProcedure.execute(item_value);
					}
				}
				case "angelic" -> {
					if (HasAngelProcedure.execute()) {
						String item_value = "race_angelic";
						SelectRaceProcedure.execute(item_value);
					}
				}
				default -> {
				}
			}
		});
	}

	public CharacterSelectorPage3Screen(CharacterSelectorPage3Menu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
		this.imageWidth = 176;
		this.imageHeight = 166;
	}

	@Override
	public void updateMenuState(int elementType, String name, Object elementState) {
		menuStateUpdateActive = true;
		menuStateUpdateActive = false;
	}

	private static final ResourceLocation texture = ResourceLocation.parse("duality:textures/screens/character_selector_page_3.png");

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		for (net.spidrone.uiapi.UIScrollButtonWidget sua_w : sua_scroll_widgets) {
			sua_w.updateFromRegion();
		}
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		net.spidrone.uiapi.UIScrollRegion.drawScrollbar(guiGraphics, "duality:scrollregion_scroll_region", this.leftPos + -61 + 85, this.topPos + 22 + 0, 5, 130, -16777216, -16737895);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		guiGraphics.blit(texture, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
		guiGraphics.blit(ResourceLocation.parse("duality:textures/screens/cc_background_1.png"), this.leftPos + -69, this.topPos + -26, 0, 0, 320, 220, 320, 220);
		guiGraphics.blit(ResourceLocation.parse("duality:textures/screens/cc_background_codex.png"), this.leftPos + 126, this.topPos + 8, 0, 0, 116, 114, 116, 114);
		guiGraphics.blit(ResourceLocation.parse("duality:textures/screens/cc_background_circles_empty.png"), this.leftPos + 164, this.topPos + -18, 0, 0, 72, 12, 72, 12);
		guiGraphics.blit(ResourceLocation.parse("duality:textures/screens/page_indicator-1.png"), this.leftPos + 194, this.topPos + -18, 0, 0, 12, 12, 12, 12);
		RenderSystem.disableBlend();
	}

	@Override
	public boolean keyPressed(int key, int b, int c) {
		if (key == 256) {
			// Mid-creation, Escape goes to the pause menu; the creator comes back once it closes (see CreatorKeepOpen).
			this.minecraft.player.closeContainer();
			if (net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.isActive())
				this.minecraft.pauseGame(false);
			return true;
		}
		return super.keyPressed(key, b, c);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (net.spidrone.uiapi.UIScrollRegion.isDraggingThumb("duality:scrollregion_scroll_region")) {
			net.spidrone.uiapi.UIScrollRegion.dragThumb("duality:scrollregion_scroll_region", (int) mouseY, this.topPos + 22 + 0, 130);
			return true;
		}
		return (this.getFocused() != null && this.isDragging() && button == 0) ? this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY) : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		{
			String sua_region = "duality:scrollregion_scroll_region";
			if (net.spidrone.uiapi.UIScrollRegion.isThumbNeeded(sua_region)) {
				int sua_trackX = this.leftPos + -61 + 85;
				int sua_trackY = this.topPos + 22 + 0;
				if (net.spidrone.uiapi.UIScrollRegion.beginThumbDrag(sua_region, (int) mouseX, (int) mouseY, sua_trackX, sua_trackY, 5, 130)) {
					return true;
				}
				if (net.spidrone.uiapi.UIScrollRegion.handleTrackClick(sua_region, (int) mouseX, (int) mouseY, sua_trackX, sua_trackY, 5, 130)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		net.spidrone.uiapi.UIScrollRegion.endThumbDrag("duality:scrollregion_scroll_region");
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (net.spidrone.uiapi.UIScrollRegion.handleMouseScroll("duality:scrollregion_scroll_region", (int) mouseX, (int) mouseY, scrollY, 14)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		{
			int sua_label_x = 131;
			int sua_label_y = 11;
			{
				guiGraphics.drawString(this.font, CodexReturnStringProcedure.execute(entity), sua_label_x, sua_label_y, -12829636, false);
			}
		}
		{
			int sua_label_x = 131;
			int sua_label_y = 25;
			{
				int sua_desc_lines = net.spidrone.uiapi.UIGraphicsHelper.drawCustomFontBound(guiGraphics, net.minecraft.resources.ResourceLocation.parse("minecraft:default"), CodexReturnDescriptionProcedure.execute(), '$',
						sua_label_x, sua_label_y, sua_label_x + 106, sua_label_y + 1000, -12829636, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.LEFT, 0.8f, 0.8f);
				String sua_warning = net.spidrotech.duality.charactercreation.client.CharacterCodex.warningLine();
				if (!sua_warning.isEmpty()) {
					net.spidrone.uiapi.UIGraphicsHelper.drawCustomFontBound(guiGraphics, net.minecraft.resources.ResourceLocation.parse("minecraft:default"), sua_warning, '$', sua_label_x,
							sua_label_y + Math.round(sua_desc_lines * this.font.lineHeight * 0.8f) + 4, sua_label_x + 106, sua_label_y + 1000, 0xFFFF5555, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.LEFT, 0.8f, 0.8f);
				}
			}
		}
		{
			int sua_label_x = -60;
			int sua_label_y = 3;
			{
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("duality:boldpixels"),
						"Choose Abilities", sua_label_x, sua_label_y, net.spidrone.uiapi.ComponentEffects.solid(-6710887), null, false,
						net.spidrone.uiapi.UIGraphicsHelper.TextAlign.LEFT, 0.8f, 0.8f);
			}
		}
		{
			int sua_label_x = -60;
			int sua_label_y = -17;
			{
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("duality:boldpixels"),
						"Character Creator", sua_label_x, sua_label_y,
						net.spidrone.uiapi.ComponentEffects.pulse(-1, -6710887, 1f, net.spidrone.uiapi.UIEasing.easeInOutSine()), null, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.LEFT, 1f, 1f);
			}
		}
		{
			// See init()'s matching loop: granted powers first (their own cost is always 0 - they're
			// free), then everything still purchasable, each with its cost in white after the name -
			// same scheme screens one and two use.
			java.util.List<net.spidrotech.duality.charactercreation.AbilityDefinition> sua_granted = net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.grantedAbilities();
			java.util.List<net.spidrotech.duality.charactercreation.AbilityDefinition> sua_choices = net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.abilityChoices();
			int sua_idx_race_scroll = 0;
			for (net.spidrotech.duality.charactercreation.AbilityDefinition sua_ability : sua_granted) {
				int sua_label_x = net.spidrone.uiapi.UIScrollRegion.getItemX("duality:scrollregion_scroll_region") - this.leftPos + 40;
				int sua_label_y = net.spidrone.uiapi.UIScrollRegion.getItemY("duality:scrollregion_scroll_region", sua_idx_race_scroll) - this.topPos + 5;
				net.spidrone.uiapi.UIClipRegion.beginClip(guiGraphics, net.spidrone.uiapi.UIScrollRegion.getRegionX("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionY("duality:scrollregion_scroll_region"),
						net.spidrone.uiapi.UIScrollRegion.getRegionWidth("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionHeight("duality:scrollregion_scroll_region"));
				{
					net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"), sua_ability.displayName() + " (free)", sua_label_x, sua_label_y,
							net.spidrone.uiapi.ComponentEffects.solid(-12829636), null, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.CENTER, 0.6f, 0.6f);
				}
				net.spidrone.uiapi.UIClipRegion.endClip(guiGraphics);
				sua_idx_race_scroll++;
			}
			for (net.spidrotech.duality.charactercreation.AbilityDefinition sua_ability : sua_choices) {
				int sua_label_x = net.spidrone.uiapi.UIScrollRegion.getItemX("duality:scrollregion_scroll_region") - this.leftPos + 40;
				int sua_label_y = net.spidrone.uiapi.UIScrollRegion.getItemY("duality:scrollregion_scroll_region", sua_idx_race_scroll) - this.topPos + 5;
				net.spidrone.uiapi.UIClipRegion.beginClip(guiGraphics, net.spidrone.uiapi.UIScrollRegion.getRegionX("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionY("duality:scrollregion_scroll_region"),
						net.spidrone.uiapi.UIScrollRegion.getRegionWidth("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionHeight("duality:scrollregion_scroll_region"));
				{
					String sua_name = sua_ability.displayName();
					int sua_cost = sua_ability.pointCost();
					int sua_split = sua_name.length();
					net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"), sua_name + " (" + sua_cost + ")", sua_label_x, sua_label_y,
							(index, length, time) -> index < sua_split ? -12829636 : 0xFFFFFFFF, null, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.CENTER, 0.6f, 0.6f);
				}
				net.spidrone.uiapi.UIClipRegion.endClip(guiGraphics);
				sua_idx_race_scroll++;
			}
		}
		{
			int sua_label_x = 91;
			int sua_label_y = 178;
			{
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"), PointsReturnProcedure.execute(entity), sua_label_x, sua_label_y,
						net.spidrone.uiapi.ComponentEffects.holographic(-3355444, -154), null, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.CENTER, 0.9f, 0.9f);
			}
		}
	}

	@Override
	public void init() {
		super.init();
		net.spidrone.uiapi.UIScrollRegion.defineRegion("duality:scrollregion_scroll_region", this.leftPos + -61, this.topPos + 22, 83, 130);
		net.spidrone.uiapi.UIScrollRegion.setContentStart("duality:scrollregion_scroll_region", 0, 0);
		net.spidrone.uiapi.UIScrollRegion.setSmoothScroll("duality:scrollregion_scroll_region", true);
		// Granted (free, non-clickable) powers first, then everything this lineage's AbilityBracket
		// can still buy - see ClientCharacterCreation#grantedAbilities/#abilityChoices. Unlike screens
		// one and two this list is variable in BOTH sections at once, not just one.
		java.util.List<net.spidrotech.duality.charactercreation.AbilityDefinition> sua_granted = net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.grantedAbilities();
		java.util.List<net.spidrotech.duality.charactercreation.AbilityDefinition> sua_choices = net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.abilityChoices();
		net.spidrone.uiapi.UIScrollRegion.setItemLayout("duality:scrollregion_scroll_region", 16, 2, sua_granted.size() + sua_choices.size());
		{
			int sua_idx_race_scroll = 0;
			for (net.spidrotech.duality.charactercreation.AbilityDefinition sua_granted_ability : sua_granted) {
				int sua_h_race_scroll = 16;
				int sua_w_race_scroll = 82;
				net.spidrone.uiapi.UIScrollRegion.setItemHeightOverride("duality:scrollregion_scroll_region", sua_idx_race_scroll, sua_h_race_scroll);
				// Granted powers aren't a choice - shown permanently in the "selected" art. A click
				// still updates the codex (so it's not a dead button to look at), but never reaches the
				// server: there's nothing to toggle.
				String sua_granted_id = sua_granted_ability.id();
				sua_scroll_widgets.add(this.addRenderableWidget(new net.spidrone.uiapi.UIScrollButtonWidget(net.spidrone.uiapi.UIScrollRegion.getItemX("duality:scrollregion_scroll_region"),
						net.spidrone.uiapi.UIScrollRegion.getItemY("duality:scrollregion_scroll_region", sua_idx_race_scroll), sua_w_race_scroll, sua_h_race_scroll, "duality:scrollregion_scroll_region", "duality:ability_scroll",
						sua_granted_id, sua_idx_race_scroll, ResourceLocation.parse("duality:textures/screens/button2.png"), ResourceLocation.parse("duality:textures/screens/button2.png"),
						ResourceLocation.parse("duality:textures/screens/button2.png"), () -> {
							net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.previewAbility(sua_granted_id);
						})));
				sua_idx_race_scroll++;
			}
			for (net.spidrotech.duality.charactercreation.AbilityDefinition sua_ability : sua_choices) {
				int sua_h_race_scroll = 16;
				int sua_w_race_scroll = 82;
				net.spidrone.uiapi.UIScrollRegion.setItemHeightOverride("duality:scrollregion_scroll_region", sua_idx_race_scroll, sua_h_race_scroll);
				String sua_ability_id = sua_ability.id();
				// Every chosen power keeps showing its OWN "selected" art independently - not just the
				// last one clicked - by picking the texture set from this ability's own chosen state
				// at build time, rather than leaning on UIButtonElement's single-select tracking (which
				// only ever remembers one selected id per group).
				boolean sua_chosen = net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.hasChosen(sua_ability_id);
				ResourceLocation sua_normal_tex = ResourceLocation.parse(sua_chosen ? "duality:textures/screens/button2.png" : "duality:textures/screens/button0.png");
				ResourceLocation sua_hover_tex = ResourceLocation.parse(sua_chosen ? "duality:textures/screens/button2.png" : "duality:textures/screens/button1.png");
				sua_scroll_widgets.add(this.addRenderableWidget(new net.spidrone.uiapi.UIScrollButtonWidget(net.spidrone.uiapi.UIScrollRegion.getItemX("duality:scrollregion_scroll_region"),
						net.spidrone.uiapi.UIScrollRegion.getItemY("duality:scrollregion_scroll_region", sua_idx_race_scroll), sua_w_race_scroll, sua_h_race_scroll, "duality:scrollregion_scroll_region", "duality:ability_scroll",
						sua_ability_id, sua_idx_race_scroll, sua_normal_tex, sua_hover_tex, ResourceLocation.parse("duality:textures/screens/button2.png"), () -> {
							// previewAbility only updates the codex - it doesn't send anything (see its
							// doc); sendClickToServer is what actually toggles the power, same one
							// network path every other creator button already uses.
							net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.previewAbility(sua_ability_id);
							net.spidrone.uiapi.UIButtonElement.sendClickToServer("duality:ability_scroll", sua_ability_id);
						})));
				sua_idx_race_scroll++;
			}
		}
		{
			int sua_y_scrollbuttons_scroll_buttons = this.topPos + 172;
			if (ShowLeftButtonProcedure.execute(entity)) {
				int sua_h_scrollbuttons_scroll_buttons = 16;
				int sua_w_scrollbuttons_scroll_buttons = 15;
				this.addRenderableWidget(new net.spidrone.uiapi.UIScrollButtonWidget(this.leftPos + -63, sua_y_scrollbuttons_scroll_buttons, sua_w_scrollbuttons_scroll_buttons, sua_h_scrollbuttons_scroll_buttons, null,
						"duality:scrollbuttons_scroll_buttons", "button_0", 0, ResourceLocation.parse("duality:textures/screens/cc_left0.png"), ResourceLocation.parse("duality:textures/screens/cc_left1.png"),
						ResourceLocation.parse("duality:textures/screens/cc_left1.png"), () -> {
							net.spidrone.uiapi.UIButtonElement.setSelected("duality:scrollbuttons_scroll_buttons", "button_0");
							net.spidrone.uiapi.UIButtonElement.sendClickToServer("duality:scrollbuttons_scroll_buttons", "button_0");
						}));
			}
		}
		{
			int sua_y_scrollbuttons_scroll_buttons1 = this.topPos + 172;
			if (ShowRightButtonProcedure.execute(entity)) {
				int sua_h_scrollbuttons_scroll_buttons1 = 16;
				int sua_w_scrollbuttons_scroll_buttons1 = 15;
				this.addRenderableWidget(new net.spidrone.uiapi.UIScrollButtonWidget(this.leftPos + 230, sua_y_scrollbuttons_scroll_buttons1, sua_w_scrollbuttons_scroll_buttons1, sua_h_scrollbuttons_scroll_buttons1, null,
						"duality:scrollbuttons_scroll_buttons1", "button_1", 0, ResourceLocation.parse("duality:textures/screens/cc_right0.png"), ResourceLocation.parse("duality:textures/screens/cc_right1.png"),
						ResourceLocation.parse("duality:textures/screens/cc_right1.png"), () -> {
							net.spidrone.uiapi.UIButtonElement.setSelected("duality:scrollbuttons_scroll_buttons1", "button_1");
							net.spidrone.uiapi.UIButtonElement.sendClickToServer("duality:scrollbuttons_scroll_buttons1", "button_1");
						}));
				sua_y_scrollbuttons_scroll_buttons1 += sua_h_scrollbuttons_scroll_buttons1 + 2;
			}
		}
	}
}