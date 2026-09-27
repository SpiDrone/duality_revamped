package net.spidrotech.duality.client.gui;

import net.spidrotech.duality.world.inventory.CharacterSelectorPage5Menu;
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

public class CharacterSelectorPage5Screen extends AbstractContainerScreen<CharacterSelectorPage5Menu> implements DualityModScreens.ScreenAccessor {
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

	public CharacterSelectorPage5Screen(CharacterSelectorPage5Menu container, Inventory inventory, Component text) {
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

	private static final ResourceLocation texture = ResourceLocation.parse("duality:textures/screens/character_selector_page_5.png");
	// Hand-written below this line: MCreator regenerating this screen replaces everything here with a
	// copy of page one. The editor itself lives in charactercreation.client.AppearanceEditor.
	private static final int NAME_MAX_LENGTH = 24;
	/** Body-part columns, preview and tint picker - see AppearanceEditor. */
	private final net.spidrotech.duality.charactercreation.client.AppearanceEditor editor = new net.spidrotech.duality.charactercreation.client.AppearanceEditor();
	private net.minecraft.client.gui.components.EditBox nameBox;

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		// Red for a malformed name, and for one the server says a living character already has.
		net.spidrotech.duality.charactercreation.DraftView view = net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.view();
		String typed = nameBox.getValue().trim();
		boolean usable = net.spidrotech.duality.charactercreation.CharacterDraft.isNameUsable(typed) && !(typed.equals(view.name()) && !view.nameUsable());
		nameBox.setTextColor(usable || nameBox.getValue().isEmpty() ? 0xFFFFFFFF : 0xFFFF5555);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		editor.render(guiGraphics, this.font, mouseX, mouseY);
		renderConfirm(guiGraphics, mouseX, mouseY);
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
		guiGraphics.blit(ResourceLocation.parse("duality:textures/screens/page_indicator-1.png"), this.leftPos + 224, this.topPos + -18, 0, 0, 12, 12, 12, 12);
		RenderSystem.disableBlend();
	}

	/** Typing goes to the name box first - the AnvilScreen pattern - so a letter bound to the
	 *  inventory key (E) types instead of closing the screen. Escape closes an open colour picker
	 *  first; otherwise it goes to the pause menu, and the creator comes back when that closes
	 *  (see CreatorKeepOpen). */
	@Override
	public boolean keyPressed(int key, int b, int c) {
		if (key == 256) {
			if (editor.closePicker())
				return true;
			this.minecraft.player.closeContainer();
			if (net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.isActive())
				this.minecraft.pauseGame(false);
			return true;
		}
		if (nameBox.isFocused())
			return nameBox.keyPressed(key, b, c) || nameBox.canConsumeInput() || super.keyPressed(key, b, c);
		return super.keyPressed(key, b, c);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (editor.mouseDragged(mouseX, mouseY))
			return true;
		return (this.getFocused() != null && this.isDragging() && button == 0) ? this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY) : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// Everything hand-drawn goes BEFORE super: AbstractContainerScreen#mouseClicked returns true
		// for every click, so anything after it never runs.
		if (button == 0 && !editor.isPickerOpen() && overConfirm(mouseX, mouseY)) {
			if (net.spidrotech.duality.charactercreation.client.AppearanceEditor.confirmBlockers(nameBox.getValue()).isEmpty()) {
				net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.commit();
				this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
			}
			return true;
		}
		if (editor.mouseClicked(mouseX, mouseY, button)) {
			nameBox.setFocused(false);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (editor.mouseReleased())
			return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (editor.mouseScrolled(mouseX, mouseY, scrollY))
			return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		{
			int sua_label_x = -60;
			int sua_label_y = 3;
			{
				net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, net.minecraft.resources.ResourceLocation.parse("duality:boldpixels"),
						"Character Appearance", sua_label_x, sua_label_y, net.spidrone.uiapi.ComponentEffects.solid(-6710887), null, false,
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
	}

	@Override
	public void init() {
		super.init();
		editor.init(this.leftPos, this.topPos);
		// The name box sits where the other pages show points remaining - nothing's spent here.
		String typed = nameBox != null ? nameBox.getValue() : net.spidrotech.duality.charactercreation.client.ClientCharacterCreation.view().name();
		nameBox = new net.minecraft.client.gui.components.EditBox(this.font, this.leftPos + 41, this.topPos + 172, 100, 16, Component.literal("Name"));
		nameBox.setMaxLength(NAME_MAX_LENGTH);
		nameBox.setHint(Component.literal("Your name...").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		nameBox.setValue(typed);
		nameBox.setResponder(net.spidrotech.duality.charactercreation.client.ClientCharacterCreation::setName);
		this.addRenderableWidget(nameBox);
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
	}

	// ----------------------------------------------------------------------------- confirm
	// Where the other pages' "next" arrow would be, widened to fit a word. Live only once
	// AppearanceEditor#confirmBlockers comes back empty; hovering it while it isn't lists what's missing.
	private static final int CONFIRM_X = 182, CONFIRM_Y = 172, CONFIRM_W = 60, CONFIRM_H = 16;

	private boolean overConfirm(double mouseX, double mouseY) {
		int x = this.leftPos + CONFIRM_X, y = this.topPos + CONFIRM_Y;
		return mouseX >= x && mouseX < x + CONFIRM_W && mouseY >= y && mouseY < y + CONFIRM_H;
	}

	private void renderConfirm(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		java.util.List<String> blockers = net.spidrotech.duality.charactercreation.client.AppearanceEditor.confirmBlockers(nameBox.getValue());
		boolean ready = blockers.isEmpty();
		boolean hover = !editor.isPickerOpen() && overConfirm(mouseX, mouseY);
		int x = this.leftPos + CONFIRM_X, y = this.topPos + CONFIRM_Y;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		if (!ready)
			guiGraphics.setColor(0.5f, 0.5f, 0.5f, 1f);
		guiGraphics.blit(ResourceLocation.parse(ready && hover ? "duality:textures/screens/button1.png" : "duality:textures/screens/button0.png"), x, y, CONFIRM_W, CONFIRM_H, 0, 0, 163, 32, 163, 32);
		guiGraphics.setColor(1f, 1f, 1f, 1f);
		RenderSystem.disableBlend();
		net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(guiGraphics, ResourceLocation.parse("spis_ui_api:boldpixels"), "Confirm", x + CONFIRM_W / 2, y + 5, ready ? -12829636 : 0xFF6A6A6A, false,
				net.spidrone.uiapi.UIGraphicsHelper.TextAlign.CENTER, 0.7f, 0.7f);
		if (hover && !ready) {
			java.util.List<Component> lines = new java.util.ArrayList<>();
			lines.add(Component.literal("Still needed:").withStyle(net.minecraft.ChatFormatting.RED));
			for (String blocker : blockers) {
				lines.add(Component.literal(" - " + blocker).withStyle(net.minecraft.ChatFormatting.GRAY));
			}
			guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
		}
	}
}
