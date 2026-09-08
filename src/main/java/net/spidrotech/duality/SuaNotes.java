/*
====================================
Defining
====================================
GuiGraphics graphics = event.getGuiGraphics();
====================================
For revealing text
====================================
if (true) {
			GuiGraphics graphics = event.getGuiGraphics();
			UIAnimationManager.drawGif(graphics, "spis_ui_api:textures/screens/button_anim_blue.png", 30, 30, 30, 30, 4, 4);
			// =========================================================================================
			// Advanced Typewriter Reveal Example
			// =========================================================================================
			String revealId = "test";
			String rawMessage = "Oh Hi, Spi's UI API can do this now | Not only will the text auto wrap and type out, but it can now reveal its self dynamically.  There is no on tick procedure!  Infact there is only one input string!  - Dio";
			double charsPerSecond = 25.0;
			// 1. Reset logic: If the dialogue string changes, clear the cache so it types out from the start
			if (!rawMessage.equals(lastMessage)) {
				UIGraphicsHelper.resetReveal(revealId);
				lastMessage = rawMessage;
			}
			// 2. Get the currently active typed substring
			String visibleSegment = UIGraphicsHelper.revealedSubstring(revealId, rawMessage, charsPerSecond);
			// 3. Render Option A: Standard reveal with specialized styles (Color effects + Offsets)
			// Useful for active cutscenes or magical text effects
			int styleX = w / 2;
			int styleY = h - 80;
			int boxPaddingX = 40;
			int boxX1 = boxPaddingX;
			int boxX2 = w - boxPaddingX;
			int boxY1 = h - 70;
			int boxY2 = h - 20;
			int baseColor = 0xFFFFFFFF; // Clean White
			// Using '|' as the custom split character defined in your bounding methods
			UIGraphicsHelper.drawCustomFontBound(graphics, UIGraphicsHelper.BOLD_PIXELS_FONT, visibleSegment, '|', boxX1, boxY1, boxX2, boxY2, baseColor, true, UIGraphicsHelper.TextAlign.LEFT);
			// 5. Completion Prompt: Display contextual actions when the string is fully typed
			if (UIGraphicsHelper.isRevealComplete(revealId, rawMessage, charsPerSecond)) {
				// Fixed: Explicitly passed Style.DEFAULT_FONT to match the 8-parameter drawCustomFont overload signature
				UIGraphicsHelper.drawCustomFont(graphics, Style.DEFAULT_FONT, "Call [The Boss Baby at 2am] to advance...", w - boxPaddingX, h - 15, 0xFFAAAAAA, true, UIGraphicsHelper.TextAlign.RIGHT);
			}
		}
====================================
Component Testing
====================================
if (true) {
            // === ComponentEffects test block ===
            ResourceLocation testFont = UIGraphicsHelper.BOLD_PIXELS_FONT;
            int lineX = 10;
            int lineY = 10;
            int lineSpacing = 14;
            UIGraphicsHelper.drawCustomFont(graphics, testFont, "Solid White", lineX, lineY, ComponentEffects.solid(0xFFFFFFFF), false);
            lineY += lineSpacing;
            UIGraphicsHelper.drawCustomFont(graphics, testFont, "Gradient Gold to Red", lineX, lineY, ComponentEffects.gradient(0xFFFFD700, 0xFFFF0000), false);
            lineY += lineSpacing;
            UIGraphicsHelper.drawCustomFont(graphics, testFont, "Rainbow Animated Text", lineX, lineY, ComponentEffects.rainbow(), false);
            lineY += lineSpacing;
            UIGraphicsHelper.drawCustomFont(graphics, testFont, "Rainbow Fast Speed", lineX, lineY, ComponentEffects.rainbow(3f), false);
            lineY += lineSpacing;
            UIGraphicsHelper.drawCustomFont(graphics, testFont, "Holographic Cyan Pink", lineX, lineY, ComponentEffects.holographic(0xFF00FFFF, 0xFFFF00FF), false);
            lineY += lineSpacing;
            // Alignment + effect combined, anchored to right edge of screen
            UIGraphicsHelper.drawCustomFont(graphics, testFont, "Right Aligned Rainbow", w - 10, lineY, ComponentEffects.rainbow(), false, TextAlign.RIGHT);
        }
====================================
Alignment Testing
====================================
         UIGraphicsHelper.drawCustomFont(graphics, UIGraphicsHelper.BOLD_PIXELS_FONT, testText, 10, h / 2 - 30, 0xFFFFFFFF, false, TextAlign.LEFT);
        // Center aligned - custom font
        UIGraphicsHelper.drawCustomFont(graphics, UIGraphicsHelper.BOLD_PIXELS_FONT, testText, w / 2, h / 2, 0xFFFFFFFF, false, TextAlign.CENTER);
        // Right aligned - default/vanilla font
        UIGraphicsHelper.drawCustomFont(graphics, Style.DEFAULT_FONT, testText, w - 10, h / 2 + 30, 0xFFFFFFFF, false, TextAlign.RIGHT);
====================================
private static String lastMessage = "";
		GuiGraphics graphics = event.getGuiGraphics();
		if (true) {
			// === ComponentEffects test block ===
			ResourceLocation testFont = UIGraphicsHelper.BOLD_PIXELS_FONT;
			int lineX = 10;
			int lineY = 10;
			int lineSpacing = 14;
			UIGraphicsHelper.drawCustomFont(graphics, testFont, "Solid White", lineX, lineY, ComponentEffects.solid(0xFFFFFFFF), false);
			lineY += lineSpacing;
			UIGraphicsHelper.drawCustomFont(graphics, testFont, "Gradient Gold to Red", lineX, lineY, ComponentEffects.gradient(0xFFFFD700, 0xFFFF0000), false);
			lineY += lineSpacing;
			UIGraphicsHelper.drawCustomFont(graphics, testFont, "Rainbow Animated Text", lineX, lineY, ComponentEffects.rainbow(), false);
			lineY += lineSpacing;
			UIGraphicsHelper.drawCustomFont(graphics, testFont, "Rainbow Fast Speed", lineX, lineY, ComponentEffects.rainbow(3f), false);
			lineY += lineSpacing;
			UIGraphicsHelper.drawCustomFont(graphics, testFont, "Holographic Cyan Pink", lineX, lineY, ComponentEffects.holographic(0xFF00FFFF, 0xFFFF00FF), false);
			lineY += lineSpacing;
			// Alignment + effect combined, anchored to right edge of screen
			UIGraphicsHelper.drawCustomFont(graphics, testFont, "Right Aligned Rainbow", w - 10, lineY, ComponentEffects.rainbow(), false, UIGraphicsHelper.TextAlign.RIGHT);
		}
		if (true) {
			UIAnimationManager.drawGif(graphics, "spis_ui_api:textures/screens/button_anim_blue.png", 30, 30, 30, 30, 4, 4);
			// =========================================================================================
			// Advanced Typewriter Reveal Example
			// =========================================================================================
			String revealId = "test";
			String rawMessage = "Oh Hi, Spi's UI API can do this now | Not only will the text auto wrap and type out, but it can now reveal its self dynamically.  There is no on tick procedure!  Infact there is only one input string!  - Dio";
			double charsPerSecond = 25.0;
			// 1. Reset logic: If the dialogue string changes, clear the cache so it types out from the start
			if (!rawMessage.equals(lastMessage)) {
				UIGraphicsHelper.resetReveal(revealId);
				lastMessage = rawMessage;
			}
			// 2. Get the currently active typed substring
			String visibleSegment = UIGraphicsHelper.revealedSubstring(revealId, rawMessage, charsPerSecond);
			// 3. Render Option A: Standard reveal with specialized styles (Color effects + Offsets)
			// Useful for active cutscenes or magical text effects
			int styleX = w / 2;
			int styleY = h - 80;
			int boxPaddingX = 40;
			int boxX1 = boxPaddingX;
			int boxX2 = w - boxPaddingX;
			int boxY1 = h - 70;
			int boxY2 = h - 20;
			int baseColor = 0xFFFFFFFF; // Clean White
			// Using '|' as the custom split character defined in your bounding methods
			UIGraphicsHelper.drawCustomFontBound(graphics, UIGraphicsHelper.BOLD_PIXELS_FONT, visibleSegment, '|', boxX1, boxY1, boxX2, boxY2, baseColor, true, UIGraphicsHelper.TextAlign.LEFT);
			// 5. Completion Prompt: Display contextual actions when the string is fully typed
			if (UIGraphicsHelper.isRevealComplete(revealId, rawMessage, charsPerSecond)) {
				// Fixed: Explicitly passed Style.DEFAULT_FONT to match the 8-parameter drawCustomFont overload signature
				UIGraphicsHelper.drawCustomFont(graphics, Style.DEFAULT_FONT, "Call [The Boss Baby at 2am] to advance...", w - boxPaddingX, h - 15, 0xFFAAAAAA, true, UIGraphicsHelper.TextAlign.RIGHT);
			}
		}
		if (true) {
			// =========================================================================================
			// Wavy Typewriter Reveal Example
			// =========================================================================================
			String wavyRevealId = "wavy_test_node";
			String wavyMessage = "I am wavy text revealing itself over time...";
			double wavySpeed = 15.0; // Characters per second
			// 1. Get the currently active typed substring
			String wavyVisibleSegment = UIGraphicsHelper.revealedSubstring(wavyRevealId, wavyMessage, wavySpeed);
			// 2. Draw it with both a Color Effect and an Offset (Wave) Effect
			// Note: If your wave offset is stored as an enum (e.g., TextOffset.WAVE), 
			// replace `ComponentEffects.wave()` with that exact reference.
			UIGraphicsHelper.drawCustomFont(graphics, UIGraphicsHelper.BOLD_PIXELS_FONT, wavyVisibleSegment, w / 2, // Centered horizontally
					h / 2, // Centered vertically
					ComponentEffects.solid(0xFF00FFFF), // Cyan text (or use ComponentEffects.rainbow())
					ComponentEffects.wave(), // <--- The wave animation offset
					true, // Drop shadow
					UIGraphicsHelper.TextAlign.CENTER // Alignment
			);
		}
		if (false) {
			//public static int drawCustomFontBound(GuiGraphics graphics, ResourceLocation fontLocation, String message, char splitChar, int x1, int y1, int x2, int y2, int color, boolean dropShadow, TextAlign align) {
			GuiGraphics graphics = event.getGuiGraphics();
			int w = event.getGuiGraphics().guiWidth();
			int h = event.getGuiGraphics().guiHeight();
			// 1. Define the dimensions and positioning of the bounding box
			int boxWidth = 200;
			int boxHeight = 80;
			int x1 = (w / 2) - (boxWidth / 2);
			int y1 = (h / 2) - (boxHeight / 2);
			int x2 = (w / 2) + (boxWidth / 2);
			int y2 = (h / 2) + (boxHeight / 2);
			// 2. Prepare the full text message and line-break rule
			String boundWavyText = "This paragraph automatically wraps inside the box boundaries while waving!|Forced breaks work too.";
			char splitChar = '|';
			// 3. Compute the active typewriter visible segment for this specific text
			String wavyRevealId = "test";
			double wavySpeed = 20.0; // Characters per second
			String wavyVisibleSegment = UIGraphicsHelper.revealedSubstring(wavyRevealId, boundWavyText, wavySpeed);
			// 4. Render the currently revealed segment with wrapping, color, and motion animations
			int linesRendered = UIGraphicsHelper.drawCustomFontBound(graphics, UIGraphicsHelper.BOLD_PIXELS_FONT, wavyVisibleSegment, // Passes the changing substring dynamically
					splitChar, x1, y1, x2, y2, ComponentEffects.solid(0xFF00FFFF), // TextEffect (Cyan)
					ComponentEffects.wave(), // TextOffset (Wavy Motion)
					true, // Drop shadow
					UIGraphicsHelper.TextAlign.CENTER // Alignment within the box width
			);
		}
		// =========================================================================================
			// Radial Test
			// =========================================================================================
        
package net.spidrone.uiapi;

import org.lwjgl.glfw.GLFW;

import net.spidrone.uiapi.RadialMenuNetwork.RadialMenuSelectionPayload;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.platform.InputConstants;
@EventBusSubscriber(Dist.CLIENT)
public class RadialMenuTestProcedure {
	// Tracks the state so we only open/close exactly when the key is pressed/released
	private static boolean wasAltDown = false;
	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		// Don't run if the player isn't in a world
		if (mc.player == null || mc.getWindow() == null) {
			return;
		}
		// Check if Left Alt (GLFW_KEY_LEFT_ALT) is physically held down
		long windowHandle = mc.getWindow().getWindow();
		boolean isAltDown = InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_ALT);
		if (isAltDown && !wasAltDown) {
			// KEY PRESSED: Build and open the test menu
			UIVisualAssetsManager.RadialWheel testWheel = new UIVisualAssetsManager.RadialWheel();
			// Using fromNamespaceAndPath instead of 'new ResourceLocation'
			testWheel.add("apple_button", 0xFF444444, 0xFFFF0000, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/apple.png"));
			testWheel.add("diamond_button", 0xFF444444, 0xFF00FFFF, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/diamond.png"));
			testWheel.add("ingot_button", 0xFF444444, 0xFFEEEEEE, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_ingot.png"));
			// Testing your custom effects on the 4th button
			testWheel.add("magic_button", UIVisualAssetsManager.rainbow(1.0f), UIVisualAssetsManager.holographic(0xFFFFFFFF, 0xFF000000), ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/blaze_powder.png"));
			// Open the radial menu with a max of 4 buttons
			UIVisualAssetsManager.openRadialMenu(testWheel, 4);
			wasAltDown = true;
		} else if (!isAltDown && wasAltDown) {
			// KEY RELEASED: Close the menu and read the result
			String selectedID = UIVisualAssetsManager.closeRadialMenuAndGetSelection();
			wasAltDown = false;
			if (selectedID != null) {
				// Print the selection to the local client chat
				mc.player.displayClientMessage(Component.literal("§aRadial Menu Selection: §f" + selectedID), false);
				// SEND TO SERVER: Sync the selection so the server can generate the item
				PacketDistributor.sendToServer(new RadialMenuSelectionPayload(selectedID));
			}
		}
	}
}
*/