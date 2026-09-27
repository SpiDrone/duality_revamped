package net.spidrotech.duality.client.gui;

import net.spidrotech.duality.world.inventory.CharacterSelectorPage4Menu;
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

public class CharacterSelectorPage4Screen extends AbstractContainerScreen<CharacterSelectorPage4Menu> implements DualityModScreens.ScreenAccessor {
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

	public CharacterSelectorPage4Screen(CharacterSelectorPage4Menu container, Inventory inventory, Component text) {
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

	private static final ResourceLocation texture = ResourceLocation.parse("duality:textures/screens/character_selector_page_4.png");

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
		guiGraphics.blit(ResourceLocation.parse("duality:textures/screens/page_indicator-1.png"), this.leftPos + 209, this.topPos + -18, 0, 0, 12, 12, 12, 12);
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
		// Checked BEFORE super: AbstractContainerScreen#mouseClicked returns true for every click, so
		// anything after it never runs.
		if (button == 0 && clickStatRow(mouseX, mouseY)) {
			return true;
		}
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
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFontBound(guiGraphics, net.minecraft.resources.ResourceLocation.parse("minecraft:default"), CodexReturnDescriptionProcedure.execute(), '$', sua_label_x, sua_label_y,
						sua_label_x + 106, sua_label_y + 1000, -12829636, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.LEFT, 0.8f, 0.8f);
			}
		}
		{
			int sua_label_x = -60;
			int sua_label_y = 3;
			{
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("duality:boldpixels"),
						"Character Stats", sua_label_x, sua_label_y, net.spidrone.uiapi.ComponentEffects.solid(-6710887), null, false,
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
			net.spidrotech.duality.charactercreation.SkillType[] sua_skills = net.spidrotech.duality.charactercreation.SkillType.values();
			net.spidrone.uiapi.UIClipRegion.beginClip(guiGraphics, net.spidrone.uiapi.UIScrollRegion.getRegionX("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionY("duality:scrollregion_scroll_region"),
					net.spidrone.uiapi.UIScrollRegion.getRegionWidth("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionHeight("duality:scrollregion_scroll_region"));
			for (int sua_i = 0; sua_i < sua_skills.length; sua_i++) {
				renderStatRow(guiGraphics, sua_skills[sua_i], sua_i, mouseX, mouseY);
			}
			net.spidrone.uiapi.UIClipRegion.endClip(guiGraphics);
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
		// One row per skill, each a whole cc_stat panel (see renderStatRow/clickStatRow below) - drawn
		// and hit-tested by hand rather than as widgets, so the panel, its bars and its -/+ buttons all
		// scroll as one piece at the art's exact 0.5 scale.
		net.spidrone.uiapi.UIScrollRegion.setItemLayout("duality:scrollregion_scroll_region", STAT_ROW_HEIGHT, 2, net.spidrotech.duality.charactercreation.SkillType.values().length);
		for (int sua_i = 0; sua_i < net.spidrotech.duality.charactercreation.SkillType.values().length; sua_i++) {
			net.spidrone.uiapi.UIScrollRegion.setItemHeightOverride("duality:scrollregion_scroll_region", sua_i, STAT_ROW_HEIGHT);
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

	// ----------------------------------------------------------------------------- stat rows
	// Everything below lays out cc_stat.png (104x44) in its own source pixels, drawn at STAT_SCALE:
	// the minus slot at (0,9), the plus slot at (89,9), both 15x32, and five 12px bars with 1px gaps
	// starting at (20,15), 26px tall - exactly what cc_stat_selected.png (64x26) covers.
	private static final String STAT_REGION = "duality:scrollregion_scroll_region";
	private static final float STAT_SCALE = 0.5f;
	/** Row pitch, not panel height: the 22px panels (at 2px gaps) overlap the row above by 4px, stacking them tightly.
	 *  Set on every row explicitly - the scroll region id is shared by all five creator screens, and a
	 *  height override left behind by an earlier screen would otherwise leak into these rows. */
	private static final int STAT_ROW_HEIGHT = 16;
	/** Centres the 52px-wide scaled panel in the 82px-wide scroll row. */
	private static final int STAT_OFFSET_X = 15;
	private static final int STAT_MINUS_X = 0, STAT_PLUS_X = 89, STAT_BUTTON_Y = 9, STAT_BUTTON_W = 15, STAT_BUTTON_H = 32;
	private static final int STAT_BARS_X = 20, STAT_BARS_Y = 15, STAT_BAR_W = 12, STAT_BAR_STRIDE = 13, STAT_BARS_H = 26;
	private static final ResourceLocation STAT_BG = ResourceLocation.parse("duality:textures/screens/cc_stat.png");
	private static final ResourceLocation STAT_FULL = ResourceLocation.parse("duality:textures/screens/cc_stat_selected.png");
	private static final ResourceLocation STAT_MINUS = ResourceLocation.parse("duality:textures/screens/cc_stat_minus.png");
	private static final ResourceLocation STAT_MINUS_HOVER = ResourceLocation.parse("duality:textures/screens/cc_stat_minus1.png");
	private static final ResourceLocation STAT_PLUS = ResourceLocation.parse("duality:textures/screens/cc_stat_plus.png");
	private static final ResourceLocation STAT_PLUS_HOVER = ResourceLocation.parse("duality:textures/screens/cc_stat_plus1.png");
	private static final ResourceLocation STAT_FONT = ResourceLocation.parse("spis_ui_api:boldpixels");

	private float statRowX() {
		return net.spidrone.uiapi.UIScrollRegion.getItemX(STAT_REGION) + STAT_OFFSET_X;
	}

	private float statRowY(int index) {
		return net.spidrone.uiapi.UIScrollRegion.getItemY(STAT_REGION, index);
	}

	/** Whether the mouse (absolute screen coordinates) is over one of a row's buttons, given that
	 *  button's source x - only while the row is scrolled into view and the mouse is inside it. */
	private boolean overStatButton(double mouseX, double mouseY, int index, int sourceX) {
		if (!net.spidrone.uiapi.UIScrollRegion.isItemVisible(STAT_REGION, index) || !net.spidrone.uiapi.UIScrollRegion.isPointInRegion(STAT_REGION, (int) mouseX, (int) mouseY))
			return false;
		double left = statRowX() + sourceX * STAT_SCALE;
		double top = statRowY(index) + STAT_BUTTON_Y * STAT_SCALE;
		return mouseX >= left && mouseX < left + STAT_BUTTON_W * STAT_SCALE && mouseY >= top && mouseY < top + STAT_BUTTON_H * STAT_SCALE;
	}

	/**
	 * One skill's panel. Each bar is one point of the 5-point creation cap (SkillType.MAX); points the
	 * race and lineage granted for free draw dimmer than points the player bought.
	 */
	private void renderStatRow(GuiGraphics guiGraphics, net.spidrotech.duality.charactercreation.SkillType skill, int index, int mouseX, int mouseY) {
		float x = statRowX() - this.leftPos;
		float y = statRowY(index) - this.topPos;
		int value = net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.skill(skill);
		int base = net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.baseSkill(skill);
		boolean hoverMinus = overStatButton(mouseX, mouseY, index, STAT_MINUS_X);
		boolean hoverPlus = overStatButton(mouseX, mouseY, index, STAT_PLUS_X);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y, 0);
		guiGraphics.pose().scale(STAT_SCALE, STAT_SCALE, 1f);
		guiGraphics.blit(STAT_BG, 0, 0, 0, 0, 104, 44, 104, 44);
		for (int point = 0; point < Math.min(value, 5); point++) {
			int sourceX = point * STAT_BAR_STRIDE;
			if (point < base)
				guiGraphics.setColor(0.55f, 0.55f, 0.55f, 1f);
			guiGraphics.blit(STAT_FULL, STAT_BARS_X + sourceX, STAT_BARS_Y, sourceX, 0, STAT_BAR_W, STAT_BARS_H, 64, 26);
			guiGraphics.setColor(1f, 1f, 1f, 1f);
		}
		guiGraphics.blit(hoverMinus ? STAT_MINUS_HOVER : STAT_MINUS, STAT_MINUS_X, STAT_BUTTON_Y, 0, 0, STAT_BUTTON_W, STAT_BUTTON_H, STAT_BUTTON_W, STAT_BUTTON_H);
		guiGraphics.blit(hoverPlus ? STAT_PLUS_HOVER : STAT_PLUS, STAT_PLUS_X, STAT_BUTTON_Y, 0, 0, STAT_BUTTON_W, STAT_BUTTON_H, STAT_BUTTON_W, STAT_BUTTON_H);
		guiGraphics.pose().popPose();
		RenderSystem.disableBlend();
		// The name sits in the panel's top strip (source x 18-85, y 1-14: 34x7 once scaled), shrunk
		// further if a long name would otherwise run past it.
		String name = skill.displayName();
		int nameWidth = this.font.width(Component.literal(name).withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(STAT_FONT)));
		float textScale = Math.min(0.6f, 32f / Math.max(1, nameWidth));
		net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, STAT_FONT, name, Math.round(x + 51.5f * STAT_SCALE), Math.round(y + 1 + (7 - 8 * textScale) / 2f),
				net.spidrone.uiapi.ComponentEffects.solid(0xFFFFFFFF), null, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.CENTER, textScale, textScale);
	}

	/** A click on any row's -/+ spends or refunds one point on that skill (see
	 *  CharacterCreatorScreenHandler#applySkill) and puts the skill in the codex. */
	private boolean clickStatRow(double mouseX, double mouseY) {
		net.spidrotech.duality.charactercreation.SkillType[] skills = net.spidrotech.duality.charactercreation.SkillType.values();
		for (int index = 0; index < skills.length; index++) {
			String group = overStatButton(mouseX, mouseY, index, STAT_MINUS_X) ? "duality:skill_minus" : overStatButton(mouseX, mouseY, index, STAT_PLUS_X) ? "duality:skill_plus" : null;
			if (group == null)
				continue;
			net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.previewSkill(skills[index]);
			net.spidrone.uiapi.UIButtonElement.sendClickToServer(group, skills[index].id());
			this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return false;
	}
}
