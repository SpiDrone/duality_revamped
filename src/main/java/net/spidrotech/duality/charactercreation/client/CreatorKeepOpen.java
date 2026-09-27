package net.spidrotech.duality.charactercreation.client;

import net.spidrotech.duality.charactercreation.CreationAction;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.Minecraft;

/**
 * Keeps the character creator on screen until the character is finished.
 *
 * <p>Whenever the player is mid-creation with no screen up (they closed the creator, came back
 * from the pause menu, or opened their inventory over it), this asks the server to put the
 * current step's screen back (CreationAction.REOPEN). The client does the asking rather than the
 * server forcing it, because only the client knows when the pause menu is open. Forcing it from the
 * server would pop the creator over the pause menu and leave no way to quit.
 *
 * <p>Waits a few ticks before asking, so the brief gap while the server swaps one page's menu for
 * the next doesn't count as "closed". The server ignores the request anyway if a menu is already open.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class CreatorKeepOpen {
	private static final int GRACE_TICKS = 5;
	private static final int RETRY_TICKS = 20;
	private static int closedTicks;

	private CreatorKeepOpen() {
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		boolean offScreen = mc.screen == null || mc.screen instanceof InventoryScreen || mc.screen instanceof CreativeModeInventoryScreen;
		// Dead: leave the death screen alone so Respawn stays reachable - see CharacterCreatorNavigation#reopen.
		if (mc.player == null || mc.player.isDeadOrDying() || !ClientCharacterCreation.isActive() || !offScreen) {
			closedTicks = 0;
			return;
		}
		closedTicks++;
		if (closedTicks == GRACE_TICKS || (closedTicks > GRACE_TICKS && (closedTicks - GRACE_TICKS) % RETRY_TICKS == 0)) {
			if (mc.screen != null)
				mc.player.closeContainer();
			ClientCharacterCreation.send(CreationAction.REOPEN, "", 0);
		}
	}
}
