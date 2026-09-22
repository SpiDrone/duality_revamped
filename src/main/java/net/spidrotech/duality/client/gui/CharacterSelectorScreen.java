package net.spidrotech.duality.client.gui;

import net.spidrotech.duality.world.inventory.CharacterSelectorMenu;
import net.spidrotech.duality.procedures.SelectRaceProcedure;
import net.spidrotech.duality.procedures.HasDemonProcedure;
import net.spidrotech.duality.procedures.CodexReturnStringProcedure;
import net.spidrotech.duality.procedures.CodexReturnDescriptionProcedure;
import net.spidrotech.duality.init.DualityModScreens;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.GuiGraphics;

import com.mojang.blaze3d.systems.RenderSystem;

public class CharacterSelectorScreen extends AbstractContainerScreen<CharacterSelectorMenu> implements DualityModScreens.ScreenAccessor {
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
				default -> {
				}
			}
		});
	}

	public CharacterSelectorScreen(CharacterSelectorMenu container, Inventory inventory, Component text) {
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

	private static final ResourceLocation texture = ResourceLocation.parse("duality:textures/screens/character_selector.png");

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		for (net.spidrone.uiapi.UIScrollButtonWidget sua_w : sua_scroll_widgets) {
			sua_w.updateFromRegion();
		}
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		net.spidrone.uiapi.UIScrollRegion.drawScrollbar(guiGraphics, "duality:scrollregion_scroll_region", this.leftPos + -61 + 81, this.topPos + 22 + 0, 5, 130, -16777216, -16737895);
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
		RenderSystem.disableBlend();
	}

	@Override
	public boolean keyPressed(int key, int b, int c) {
		if (key == 256) {
			this.minecraft.player.closeContainer();
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
				int sua_trackX = this.leftPos + -61 + 81;
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
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFontBound(guiGraphics, net.minecraft.resources.ResourceLocation.parse("minecraft:default"), CodexReturnDescriptionProcedure.execute(), '$', sua_label_x, sua_label_y, sua_label_x + 200,
						sua_label_y + 1000, net.spidrone.uiapi.ComponentEffects.solid(-12829636), null, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.LEFT, 1f, 1f);
			}
		}
		{
			int sua_label_x = -60;
			int sua_label_y = 3;
			{
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("duality:boldpixels"), Component.translatable("gui.duality.character_selector.label_choose_starting_race").getString(),
						sua_label_x, sua_label_y, net.spidrone.uiapi.ComponentEffects.solid(-6710887), null, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.LEFT, 0.8f, 0.8f);
			}
		}
		{
			int sua_label_x = -60;
			int sua_label_y = -17;
			{
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("duality:boldpixels"), Component.translatable("gui.duality.character_selector.label_character_creator_15").getString(),
						sua_label_x, sua_label_y, net.spidrone.uiapi.ComponentEffects.pulse(-1, -6710887, 1f, net.spidrone.uiapi.UIEasing.easeInOutSine()), null, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.LEFT, 1f, 1f);
			}
		}
		{
			int sua_label_x = net.spidrone.uiapi.UIScrollRegion.getItemX("duality:scrollregion_scroll_region") - this.leftPos + 40;
			int sua_label_y = net.spidrone.uiapi.UIScrollRegion.getItemY("duality:scrollregion_scroll_region", 0) - this.topPos + 5;
			net.spidrone.uiapi.UIClipRegion.beginClip(guiGraphics, net.spidrone.uiapi.UIScrollRegion.getRegionX("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionY("duality:scrollregion_scroll_region"),
					net.spidrone.uiapi.UIScrollRegion.getRegionWidth("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionHeight("duality:scrollregion_scroll_region"));
			{
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("spis_ui_api:boldpixels"), Component.translatable("gui.duality.character_selector.label_human").getString(), sua_label_x,
						sua_label_y, net.spidrone.uiapi.ComponentEffects.solid(-12829636), null, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.CENTER, 1f, 1f);
			}
			net.spidrone.uiapi.UIClipRegion.endClip(guiGraphics);
		}
		{
			int sua_label_x = net.spidrone.uiapi.UIScrollRegion.getItemX("duality:scrollregion_scroll_region") - this.leftPos + 40;
			int sua_label_y = net.spidrone.uiapi.UIScrollRegion.getItemY("duality:scrollregion_scroll_region", 1) - this.topPos + 5;
			net.spidrone.uiapi.UIClipRegion.beginClip(guiGraphics, net.spidrone.uiapi.UIScrollRegion.getRegionX("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionY("duality:scrollregion_scroll_region"),
					net.spidrone.uiapi.UIScrollRegion.getRegionWidth("duality:scrollregion_scroll_region"), net.spidrone.uiapi.UIScrollRegion.getRegionHeight("duality:scrollregion_scroll_region"));
			{
				guiGraphics.drawString(this.font, Component.translatable("gui.duality.character_selector.label_demon"), sua_label_x, sua_label_y, -12829636, false);
			}
			net.spidrone.uiapi.UIClipRegion.endClip(guiGraphics);
		}
	}

	@Override
	public void init() {
		super.init();
		net.spidrone.uiapi.UIScrollRegion.defineRegion("duality:scrollregion_scroll_region", this.leftPos + -61, this.topPos + 22, 81, 130);
		net.spidrone.uiapi.UIScrollRegion.setContentStart("duality:scrollregion_scroll_region", 0, 0);
		net.spidrone.uiapi.UIScrollRegion.setSmoothScroll("duality:scrollregion_scroll_region", true);
		net.spidrone.uiapi.UIScrollRegion.setItemLayout("duality:scrollregion_scroll_region", 20, 2, (true ? 1 : 0) + (HasDemonProcedure.execute() ? 1 : 0));
		{
			int sua_idx_race_scroll = 0;
			if (true) {
				int sua_h_race_scroll = 16;
				int sua_w_race_scroll = 81;
				net.spidrone.uiapi.UIScrollRegion.setItemHeightOverride("duality:scrollregion_scroll_region", sua_idx_race_scroll, sua_h_race_scroll);
				sua_scroll_widgets.add(this.addRenderableWidget(new net.spidrone.uiapi.UIScrollButtonWidget(net.spidrone.uiapi.UIScrollRegion.getItemX("duality:scrollregion_scroll_region"),
						net.spidrone.uiapi.UIScrollRegion.getItemY("duality:scrollregion_scroll_region", sua_idx_race_scroll), sua_w_race_scroll, sua_h_race_scroll, "duality:scrollregion_scroll_region", "duality:race_scroll", "human",
						sua_idx_race_scroll, ResourceLocation.parse("duality:textures/screens/button0.png"), ResourceLocation.parse("duality:textures/screens/button1.png"), ResourceLocation.parse("duality:textures/screens/button2.png"), () -> {
							net.spidrone.uiapi.UIButtonElement.setSelected("duality:race_scroll", "human");
							net.spidrone.uiapi.UIButtonElement.sendClickToServer("duality:race_scroll", "human");
						})));
				sua_idx_race_scroll++;
			}
			if (HasDemonProcedure.execute()) {
				int sua_h_race_scroll = 16;
				int sua_w_race_scroll = 81;
				net.spidrone.uiapi.UIScrollRegion.setItemHeightOverride("duality:scrollregion_scroll_region", sua_idx_race_scroll, sua_h_race_scroll);
				sua_scroll_widgets.add(this.addRenderableWidget(new net.spidrone.uiapi.UIScrollButtonWidget(net.spidrone.uiapi.UIScrollRegion.getItemX("duality:scrollregion_scroll_region"),
						net.spidrone.uiapi.UIScrollRegion.getItemY("duality:scrollregion_scroll_region", sua_idx_race_scroll), sua_w_race_scroll, sua_h_race_scroll, "duality:scrollregion_scroll_region", "duality:race_scroll", "demon",
						sua_idx_race_scroll, ResourceLocation.parse("duality:textures/screens/button0.png"), ResourceLocation.parse("duality:textures/screens/button1.png"), ResourceLocation.parse("duality:textures/screens/button2.png"), () -> {
							net.spidrone.uiapi.UIButtonElement.setSelected("duality:race_scroll", "demon");
							net.spidrone.uiapi.UIButtonElement.sendClickToServer("duality:race_scroll", "demon");
						})));
				sua_idx_race_scroll++;
			}
		}
	}
}