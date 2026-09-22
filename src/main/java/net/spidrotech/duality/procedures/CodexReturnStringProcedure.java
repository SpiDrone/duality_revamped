package net.spidrotech.duality.procedures;

import net.spidrotech.duality.charactercreation.client.CharacterCodex;

import net.minecraft.world.entity.Entity;

/**
 * The codex panel's heading in the character creator - the selected race's display name.
 *
 * !! MCREATOR WILL OVERWRITE THIS FILE unless the CodexReturnString element's code is locked
 * ("locked_code": false today). The text itself lives in RaceCatalog and the lookup lives in
 * CharacterCodex, so a regeneration costs this one forwarding call rather than any content.
 *
 * The entity parameter is the dependency the element declares and is deliberately unused: the codex
 * describes THIS client's own creator draft, which the client already has synced (see CharacterCodex),
 * so there is no other player to look up. It stays in the signature so MCreator's generated screen
 * call keeps matching.
 */
public class CodexReturnStringProcedure {
	public static String execute(Entity entity) {
		return CharacterCodex.title();
	}
}
