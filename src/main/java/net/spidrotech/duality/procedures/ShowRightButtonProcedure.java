package net.spidrotech.duality.procedures;

import net.spidrotech.duality.charactercreation.client.ClientCharacterCreation;
import net.spidrotech.duality.charactercreation.CreationStep;

import net.minecraft.world.entity.Entity;

/**
 * Whether a creator page shows its "next" arrow: only once the step's question is answered, and
 * never on the last page, which has Confirm instead. Hand-written - if MCreator regenerates this
 * procedure it comes back as "always true", and the arrow shows before anything's been picked.
 */
public class ShowRightButtonProcedure {
	public static boolean execute(Entity entity) {
		CreationStep step = ClientCharacterCreation.step();
		if (step == CreationStep.APPEARANCE || step == CreationStep.READY)
			return false;
		return ClientCharacterCreation.canAdvance();
	}
}
