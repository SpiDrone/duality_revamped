package net.spidrotech.duality.procedures;

import net.spidrotech.duality.charactercreation.CreationStep;
import net.spidrotech.duality.charactercreation.client.ClientCharacterCreation;

import net.minecraft.world.entity.Entity;

/**
 * Whether the creator's shared "back" button should show - every screen except the first, since
 * there's nothing before race selection to go back to.
 *
 * !! MCREATOR MAY OVERWRITE THIS FILE unless the ShowLeftButton element's code is locked.
 */
public class ShowLeftButtonProcedure {
	public static boolean execute(Entity entity) {
		return ClientCharacterCreation.step() != CreationStep.RACE;
	}
}
