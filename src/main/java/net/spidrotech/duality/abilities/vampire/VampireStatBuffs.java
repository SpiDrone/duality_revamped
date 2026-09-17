package net.spidrotech.duality.abilities.vampire;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;

/**
 * Passive strength/agility buff, scaling with VampireRank - no cast, no keybind, just an innate
 * trait every vampire has (Thralls included; even a mindless one is stronger than a human).
 *
 * There's no dedicated stats system yet, so this rides on vanilla ATTACK_DAMAGE ("strength") and
 * MOVEMENT_SPEED ("agility") as percentage modifiers. Swap resolveDamageBonus/resolveSpeedBonus
 * to read from a real stat system once one exists - everything else here stays the same.
 *
 * Re-applies every second rather than reacting to an attribute-change event (there isn't a clean
 * one for a plain RangedAttribute), which is cheap enough at this rate and self-heals if
 * something else ever clobbers the modifier.
 */
@EventBusSubscriber(modid = "duality")
public final class VampireStatBuffs {
	private static final ResourceLocation DAMAGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("duality", "vampire_strength");
	private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("duality", "vampire_agility");
	private static final int REFRESH_INTERVAL_TICKS = 20;

	// ---- tunable knobs: % bonus at each rank (index 0 = Thrall .. 3 = Queen) ----
	private static final double[] DAMAGE_BONUS_PER_RANK = { 0.05, 0.10, 0.18, 0.30 };
	private static final double[] SPEED_BONUS_PER_RANK = { 0.04, 0.08, 0.14, 0.22 };

	private VampireStatBuffs() {
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity().level().isClientSide())
			return;
		if (event.getEntity().level().getGameTime() % REFRESH_INTERVAL_TICKS != 0)
			return;
		boolean isVampire = VampireRank.isVampire(event.getEntity());
		int rank = isVampire ? VampireRank.fromAttribute(event.getEntity()).stepsAboveThrall() : -1;
		applyModifier(event.getEntity().getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, isVampire ? DAMAGE_BONUS_PER_RANK[rank] : 0);
		applyModifier(event.getEntity().getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, isVampire ? SPEED_BONUS_PER_RANK[rank] : 0);
	}

	private static void applyModifier(AttributeInstance instance, ResourceLocation id, double percent) {
		if (instance == null)
			return;
		AttributeModifier existing = instance.getModifier(id);
		if (existing != null && existing.amount() == percent)
			return; // already correct - avoid churning the modifier every second for no reason
		instance.removeModifier(id);
		if (percent != 0) {
			instance.addPermanentModifier(new AttributeModifier(id, percent, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}
}
