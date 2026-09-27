package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.world.inventory.CharacterSelectorMenu;
import net.spidrotech.duality.world.inventory.CharacterSelectorPage2Menu;
import net.spidrotech.duality.world.inventory.CharacterSelectorPage3Menu;
import net.spidrotech.duality.world.inventory.CharacterSelectorPage4Menu;
import net.spidrotech.duality.world.inventory.CharacterSelectorPage5Menu;

import net.spidrone.uiapi.UIButtonElement;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.MenuProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;

import io.netty.buffer.Unpooled;

/**
 * The character creator's five screens are five separate MCreator GUIs (CharacterSelector,
 * ...Page2..5), not one screen with panels, so "next" and "back" mean physically swapping which
 * menu the player has open - this is the class that does that swap, on top of whatever
 * {@link CharacterCreation} decides about the draft itself.
 *
 * <p>Screen one's next/back buttons and every later screen's are the SAME spis_ui_api button group
 * ("duality:scrollbuttons_scroll_buttons1" for next, "duality:scrollbuttons_scroll_buttons" for
 * back) - they were built by cloning screen one, and neither the id nor the click wiring was
 * renamed per screen. That's exactly why this lives in one place instead of five: registering
 * per-screen handlers on shared ids would just mean the last one registered wins (server handlers
 * here are first-registered-wins, same as the race buttons - see CharacterCreatorScreenHandler),
 * silently dropping the other four screens' clicks.
 *
 * <p>Screen five (appearance) is deliberately left alone. {@link ShowRightButtonProcedure} already
 * hides the shared next button once the draft reaches that step, and {@link #onNext} below ignores
 * a click that somehow still arrives on it - the whole screen is being wired up separately.
 */
@EventBusSubscriber(modid = "duality")
public final class CharacterCreatorNavigation {
	private CharacterCreatorNavigation() {
	}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			UIButtonElement.registerServerHandler("duality:scrollbuttons_scroll_buttons1", CharacterCreatorNavigation::onNext);
			UIButtonElement.registerServerHandler("duality:scrollbuttons_scroll_buttons", CharacterCreatorNavigation::onBack);
		});
	}

	/** The shared "next" button. Advances the draft one step and, if that was accepted, opens
	 *  whichever screen the new step belongs to - refused (a locked race, an unchosen lineage) and
	 *  the player stays exactly where they are, same screen, same draft. */
	private static void onNext(ServerPlayer player, String selectionId) {
		if (player == null || !CharacterCreation.isCreating(player))
			return;
		if (CharacterCreation.draft(player).step() == CreationStep.APPEARANCE)
			return; // screen five's own button, not this one's to act on
		if (CharacterCreation.act(player, CreationAction.GOTO_STEP, "", 0).ok())
			openMenuForStep(player, CharacterCreation.draft(player).step());
	}

	/** The shared "back" button. Undoes the step being left (see CharacterCreation#goBack) and opens
	 *  the previous screen - a no-op on screen one, where there's nothing to go back to. */
	private static void onBack(ServerPlayer player, String selectionId) {
		if (player == null || !CharacterCreation.isCreating(player))
			return;
		if (CharacterCreation.act(player, CreationAction.GO_BACK, "", 0).ok())
			openMenuForStep(player, CharacterCreation.draft(player).step());
	}

	/** Puts the creator back on screen for a player who's mid-creation with nothing open - they
	 *  closed it, or it was never opened. Does nothing if some menu is already up (a page switch in
	 *  flight included), so a stray request can't stack a second copy. */
	public static void reopen(ServerPlayer player) {
		// Never over a dead player: the creator would sit on top of the death screen and leave them
		// unable to press Respawn. Creation picks back up once they respawn.
		if (player == null || !player.isAlive() || !CharacterCreation.isCreating(player) || player.containerMenu != player.inventoryMenu)
			return;
		openMenuForStep(player, CharacterCreation.draft(player).step());
	}

	/** Swaps the player onto the GUI for the given step. Every step but APPEARANCE/READY has its own
	 *  menu; those last two share screen five's, which this class never opens on its own (see the
	 *  class doc) - included only so the switch is exhaustive. */
	private static void openMenuForStep(ServerPlayer player, CreationStep step) {
		BlockPos pos = player.blockPosition();
		player.openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return Component.literal("Character Creator");
			}

			@Override
			public boolean shouldTriggerClientSideContainerClosingOnOpen() {
				return false;
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos);
				return switch (step) {
					case RACE -> new CharacterSelectorMenu(id, inventory, buf);
					case SUBRACE -> new CharacterSelectorPage2Menu(id, inventory, buf);
					case ABILITIES -> new CharacterSelectorPage3Menu(id, inventory, buf);
					case SKILLS -> new CharacterSelectorPage4Menu(id, inventory, buf);
					case APPEARANCE, READY -> new CharacterSelectorPage5Menu(id, inventory, buf);
				};
			}
		}, pos);
	}
}
