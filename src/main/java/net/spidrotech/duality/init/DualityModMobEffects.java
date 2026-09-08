/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.spidrotech.duality.init;

import net.spidrotech.duality.potion.OrbingEffectMobEffect;
import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.registries.Registries;

public class DualityModMobEffects {
	public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(Registries.MOB_EFFECT, DualityMod.MODID);
	public static final DeferredHolder<MobEffect, MobEffect> ORBING_EFFECT = REGISTRY.register("orbing_effect", () -> new OrbingEffectMobEffect());
}