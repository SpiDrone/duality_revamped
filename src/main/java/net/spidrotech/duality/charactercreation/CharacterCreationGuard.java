package net.spidrotech.duality.charactercreation;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player is untouchable while they're in the character creator - they're stuck in a menu and
 * can't defend themselves. Damage that bypasses invulnerability (the void, /kill) still lands, the
 * same as it would on a creative player, so nobody can get stuck falling forever.
 */
@EventBusSubscriber(modid = "duality")
public final class CharacterCreationGuard {
	private CharacterCreationGuard() {
	}

	@SubscribeEvent
	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && CharacterCreation.isCreating(player) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))
			event.setCanceled(true);
	}
}
