package net.spidrotech.duality.procedures;

import net.spidrotech.duality.init.DualityModAttributes;
import net.spidrotech.duality.DualityMod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

public class AssignBloodQualityProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		double bloodQuality = 0;
		if (entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("duality:blood_weak")))) {
			bloodQuality = Mth.nextInt(RandomSource.create(), 1, 15);
		} else if (entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("duality:blood_medium")))) {
			bloodQuality = Mth.nextInt(RandomSource.create(), 15, 35);
		} else if (entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("duality:blood_strong")))) {
			bloodQuality = Mth.nextInt(RandomSource.create(), 35, 50);
		}
		if (entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("duality:blood_toxic")))) {
			bloodQuality = bloodQuality * (-1);
		}
		if (entity instanceof LivingEntity _livingEntity7 && _livingEntity7.getAttributes().hasAttribute(DualityModAttributes.BLOOD_QUALITY))
			_livingEntity7.getAttribute(DualityModAttributes.BLOOD_QUALITY).setBaseValue(bloodQuality);
		DualityMod.LOGGER.info(entity);
		DualityMod.LOGGER.info(entity instanceof LivingEntity _livingEntity8 && _livingEntity8.getAttributes().hasAttribute(DualityModAttributes.BLOOD_QUALITY) ? _livingEntity8.getAttribute(DualityModAttributes.BLOOD_QUALITY).getValue() : 0);
	}
}