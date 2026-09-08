package net.spidrotech.duality.procedures;

import net.spidrotech.duality.init.DualityModAttributes;

import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.Event;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import javax.annotation.Nullable;

@EventBusSubscriber
public class EntitySpawnedProcedure {
	@SubscribeEvent
	public static void onEntitySpawned(EntityJoinLevelEvent event) {
		execute(event, event.getLevel(), event.getEntity());
	}

	public static void execute(LevelAccessor world, Entity entity) {
		execute(null, world, entity);
	}

	private static void execute(@Nullable Event event, LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		if (!world.isClientSide()) {
			if (entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("duality:blood")))
					&& (entity instanceof LivingEntity _livingEntity2 && _livingEntity2.getAttributes().hasAttribute(DualityModAttributes.BLOOD_QUALITY) ? _livingEntity2.getAttribute(DualityModAttributes.BLOOD_QUALITY).getValue() : 0) == 0) {
				AssignBloodQualityProcedure.execute(entity);
			}
		}
	}
}