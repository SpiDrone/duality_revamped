/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.spidrotech.duality.init;

import net.spidrotech.duality.entity.StagnantVisualEntity;
import net.spidrotech.duality.entity.SpiderQueenEntity;
import net.spidrotech.duality.entity.LightningVisualEntity;
import net.spidrotech.duality.entity.AbilityProjectileEntity;
import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.Registries;

@EventBusSubscriber
public class DualityModEntities {
	public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, DualityMod.MODID);
	public static final DeferredHolder<EntityType<?>, EntityType<AbilityProjectileEntity>> ABILITY_PROJECTILE = register("ability_projectile",
			EntityType.Builder.<AbilityProjectileEntity>of(AbilityProjectileEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).fireImmune()

					.sized(0.2f, 0.2f));
	public static final DeferredHolder<EntityType<?>, EntityType<LightningVisualEntity>> LIGHTNING_VISUAL = register("lightning_visual",
			EntityType.Builder.<LightningVisualEntity>of(LightningVisualEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3).fireImmune().ridingOffset(-0.6f).sized(0.6f, 1.8f));
	public static final DeferredHolder<EntityType<?>, EntityType<StagnantVisualEntity>> STAGNANT_VISUAL = register("stagnant_visual",
			EntityType.Builder.<StagnantVisualEntity>of(StagnantVisualEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.sized(0.1f, 0.1f));
	public static final DeferredHolder<EntityType<?>, EntityType<SpiderQueenEntity>> SPIDER_QUEEN = register("spider_queen",
			EntityType.Builder.<SpiderQueenEntity>of(SpiderQueenEntity::new, MobCategory.MONSTER).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(3)

					.sized(0.6f, 1.8f));

	// Start of user code block custom entities
	// End of user code block custom entities
	private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String registryname, EntityType.Builder<T> entityTypeBuilder) {
		return REGISTRY.register(registryname, () -> (EntityType<T>) entityTypeBuilder.build(registryname));
	}

	@SubscribeEvent
	public static void init(RegisterSpawnPlacementsEvent event) {
		AbilityProjectileEntity.init(event);
		LightningVisualEntity.init(event);
		StagnantVisualEntity.init(event);
		SpiderQueenEntity.init(event);
	}

	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(ABILITY_PROJECTILE.get(), AbilityProjectileEntity.createAttributes().build());
		event.put(LIGHTNING_VISUAL.get(), LightningVisualEntity.createAttributes().build());
		event.put(STAGNANT_VISUAL.get(), StagnantVisualEntity.createAttributes().build());
		event.put(SPIDER_QUEEN.get(), SpiderQueenEntity.createAttributes().build());
	}
}