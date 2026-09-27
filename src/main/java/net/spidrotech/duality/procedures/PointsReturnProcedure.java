package net.spidrotech.duality.procedures;

import net.spidrotech.duality.charactercreation.client.ClientCharacterCreation;
import net.spidrotech.duality.charactercreation.CharacterDraft;

import net.minecraft.world.entity.Entity;

/**
 * "Character Points Remaining" label text in the character creator - what's left to spend, out of
 * the whole pool a character is built from.
 *
 * <p>Deliberately against {@link CharacterDraft#STARTING_POINTS} (the fixed pool), not
 * {@link ClientCharacterCreation#pointsBudget()} (that pool less the race's own cost): a denominator
 * that shrank with the race would make an expensive race look like it never charged you anything -
 * Demon at cost 2 read as "8/8" instead of showing the 2 that a flat 10 would make visible as
 * "8/10". The numerator still reflects the cost and whatever's been spent since; only the
 * denominator needs to stay put.
 *
 * !! MCREATOR MAY OVERWRITE THIS FILE unless the PointsReturn element's code is locked. The real
 * numbers live in ClientCharacterCreation (synced from the server's CharacterDraft), so a
 * regeneration only costs this forwarding call.
 *
 * The entity parameter is unused for the same reason CodexReturnString's is: this describes the
 * client's own creator draft, already synced, not some other player.
 */
public class PointsReturnProcedure {
	public static String execute(Entity entity) {
		return ClientCharacterCreation.pointsRemaining() + "/" + CharacterDraft.STARTING_POINTS + " points";
	}
}
