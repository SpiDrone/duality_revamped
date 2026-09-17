package net.spidrotech.duality.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

/**
 * Hand-written (not MCreator-generated) so it survives regeneration of init/.
 *
 * MCreator's own "Key binding" element wizard only offers a fixed dropdown of
 * default keys, and Left Alt isn't one of the options — hence a real
 * {@link KeyMapping} registered here instead. Because it's a proper
 * KeyMapping (not a hardcoded GLFW check), it's listed in the vanilla
 * Controls screen and players can rebind it to anything.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class DualityKeyMappings {
	public static final String CATEGORY = "key.categories.duality";

	public static final KeyMapping RADIAL_MENU = new KeyMapping("key.duality.radial_menu", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

	@SubscribeEvent
	public static void register(RegisterKeyMappingsEvent event) {
		event.register(RADIAL_MENU);
	}
}
