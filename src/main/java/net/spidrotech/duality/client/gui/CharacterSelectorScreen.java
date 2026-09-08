package net.spidrotech.duality.client.gui;

import org.checkerframework.checker.units.qual.h;

import net.spidrotech.duality.world.inventory.CharacterSelectorMenu;
import net.spidrotech.duality.init.DualityModScreens;

import net.spidrone.uiapi.UIGraphicsHelper;

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
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		this.renderFg(guiGraphics, partialTicks, mouseX, mouseY);
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
		guiGraphics.blit(ResourceLocation.parse("duality:textures/screens/cc_background_circles_empty.png"), this.leftPos + 171, this.topPos + -18, 0, 0, 72, 12, 72, 12);
		guiGraphics.blit(ResourceLocation.parse("duality:textures/screens/cc_background_circles_full.png"), this.leftPos + 171, this.topPos + -18, 0, 0, 12, 12, 12, 12);
		guiGraphics.blit(ResourceLocation.parse("duality:textures/screens/cc_foreground_button_0.png"), this.leftPos + -61, this.topPos + 22, 0, 0, 81, 16, 81, 16);
		RenderSystem.disableBlend();
	}

	protected void renderFg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		//sua
		GuiGraphics graphics = guiGraphics;
		int w = this.width;
		int h = this.height;
		String testText = "Character Creator  (Step 1/5)";
		UIGraphicsHelper.drawCustomFont(graphics, UIGraphicsHelper.BOLD_PIXELS_FONT, testText, this.leftPos - 59, this.topPos - 16, 0xFFFFFFFF, false, UIGraphicsHelper.TextAlign.LEFT);
		//
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
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, Component.translatable("gui.duality.character_selector.label_screentitle"), -59, -16, -6710887, false);
		guiGraphics.drawString(this.font, Component.translatable("gui.duality.character_selector.label_codex"), 131, 11, -12829636, false);
		guiGraphics.drawString(this.font, Component.translatable("gui.duality.character_selector.label_codex_description"), 131, 26, -12829636, false);
		guiGraphics.drawString(this.font, Component.translatable("gui.duality.character_selector.label_choose_starting_race"), -61, 3, -6710887, false);
	}

	@Override
	public void init() {
		super.init();
	}
}