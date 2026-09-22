package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.registries.Registries;

/**
 * SHIMMERING is a marker effect, not a gameplay one - no attribute modifiers, no tick logic. Its
 * only job is to be a piece of state every nearby client already gets for free (mob effects sync
 * to observers the same way any other entity data does), so client.ShimmerRenderer and
 * mixin.LivingEntityRendererMixin can ask "is this entity mid-Shimmer-charge right now" without
 * any networking of their own - the same trick ShimmerAbility used to lean on vanilla's own
 * Invisibility effect for (see that class's history), just with a dedicated effect instead of a
 * borrowed one. Borrowing Invisibility doesn't work for this: PLENTY of other things apply real
 * Invisibility (VanishAbility, a vanilla potion), and keying the translucent-render mixin off
 * "has Invisibility" would incorrectly reskin every one of those too.
 *
 * Kept out of DualityModMobEffects (MCreator-owned, regenerated on every MCreator build) so this
 * survives a regenerate - registered by hand here and wired to the mod bus in DualityMod's
 * preserved constructor code block, the same pattern DualityModAttributes already uses for its
 * own hand-authored attributes.
 *
 * Always applied with showParticles=false, showIcon=false (see ShimmerAbility) - it has no icon
 * or particle worth showing, so there's nothing here to hide via IClientMobEffectExtensions the
 * way OrbingEffectMobEffect does.
 */
public final class ShimmerEffects {
	public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(Registries.MOB_EFFECT, DualityMod.MODID);
	public static final DeferredHolder<MobEffect, MobEffect> SHIMMERING = REGISTRY.register("shimmering", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xFFFFFF) {
	});

	private ShimmerEffects() {
	}
}
