package net.spidrotech.duality.procedures;

import net.spidrotech.duality.charactercreation.CharacterCreatorScreenHandler;
import net.spidrotech.duality.DualityMod;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Picks a race for the player's character-creator draft. Called when a race row in the creator
 * screen is clicked - see CharacterCreatorScreenHandler, which receives that click on the server
 * and hands it here.
 *
 * !! MCREATOR WILL OVERWRITE THIS FILE unless the SelectRace element's code is locked. It is
 * currently "locked_code": false in duality.mcreator, which means the next MCreator build
 * regenerates this class from its blockly and throws this logic away. To keep it: right-click the
 * SelectRace procedure in MCreator and lock its code. Until then, treat CharacterCreatorScreenHandler
 * as the file that matters - the real work lives there, and this class only forwards to it, so even
 * a regeneration costs the forwarding call rather than the behaviour.
 *
 * WHY THE TWO OVERLOADS: the entity one is the real entry point, and is what MCreator would
 * generate for this procedure if the SelectRace element declared an `entity` dependency alongside
 * its existing `item_value` one. It does not today, so MCreator's own generated screen code calls
 * the string-only version - kept below purely so that generated call keeps compiling.
 */
public class SelectRaceProcedure {

	/** The real one. item_value is the clicked row's id, with or without the screen's "race_"
	 *  prefix - see CharacterCreatorScreenHandler#applyRace, which normalises it either way. */
	public static void execute(Entity entity, String item_value) {
		if (item_value == null)
			return;
		if (!(entity instanceof ServerPlayer player)) {
			// Race selection is server-authoritative (it validates against what this player has
			// actually earned), so a client-side or non-player caller has nothing to act on.
			return;
		}
		CharacterCreatorScreenHandler.applyRace(player, item_value);
	}

	/**
	 * Compile-compatibility shim for MCreator's generated screen code, which passes only the string
	 * because the SelectRace element declares no entity dependency yet. Nothing reaches this in
	 * practice - CharacterCreatorScreenHandler claims the button group's server handler first and
	 * calls the entity overload above - so if this ever logs, the generated handler won the
	 * registration race instead and the screen needs that dependency added.
	 */
	public static void execute(String item_value) {
		DualityMod.LOGGER.warn("[duality] SelectRaceProcedure called with no player (item_value={}) - add an `entity` dependency to the SelectRace procedure in MCreator so it can act on the clicker", item_value);
	}
}
