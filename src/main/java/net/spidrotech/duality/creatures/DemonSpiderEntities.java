package net.spidrotech.duality.creatures;

import net.spidrotech.duality.DualityMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registration for the demonic spider.
 *
 * <p>Everything here is deliberately independent of MCreator. Registration goes through
 * RegisterEvent instead of a DeferredRegister, because a DeferredRegister needs the mod event bus
 * and the only place to hand it over is the generated mod constructor. The model layer is
 * registered under this mod's own id too, so the spider does not depend on the SpiderQueen element
 * continuing to exist in the workspace.
 *
 * <p>The mesh comes from DemonSpiderMesh, a hand-written copy of the Blockbench export, not from
 * MCreator's Modeldemonic_spider - so updating the model means replacing it there, and MCreator's
 * copy can drift without affecting the spider.
 */
@EventBusSubscriber(modid = "duality")
public final class DemonSpiderEntities {
	public static final ResourceLocation DEMON_SPIDER_ID = ResourceLocation.fromNamespaceAndPath(DualityMod.MODID, "demon_spider");

	/** Our own layer, built from the generated mesh, rather than the one the SpiderQueen element registers. */
	public static final ModelLayerLocation DEMON_SPIDER_LAYER = new ModelLayerLocation(DEMON_SPIDER_ID, "main");

	/**
	 * A handle that looks the type up by id, not the type itself. Building an EntityType claims a
	 * registry holder on the spot, which throws "Registry is already frozen" anywhere outside
	 * registration - and a static field is built whenever this class happens to load, which can be
	 * long before or after that window. So the type is only ever built inside onRegister below.
	 */
	public static final DeferredHolder<EntityType<?>, EntityType<DemonSpiderEntity>> DEMON_SPIDER = DeferredHolder.create(Registries.ENTITY_TYPE, DEMON_SPIDER_ID);

	private DemonSpiderEntities() {
	}

	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
		event.register(Registries.ENTITY_TYPE, DEMON_SPIDER_ID, () -> EntityType.Builder.of(DemonSpiderEntity::new, MobCategory.MONSTER).sized(1.4f, 0.9f)
				.clientTrackingRange(8).updateInterval(3).setShouldReceiveVelocityUpdates(true).build("demon_spider"));
	}

	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(DEMON_SPIDER.get(), DemonSpiderEntity.createAttributes().build());
	}

	@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
	public static final class Client {
		private Client() {
		}

		@SubscribeEvent
		public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
			event.registerLayerDefinition(DEMON_SPIDER_LAYER, DemonSpiderMesh::createBodyLayer);
		}

		@SubscribeEvent
		public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
			event.registerEntityRenderer(DEMON_SPIDER.get(), DemonSpiderRenderer::new);
		}
	}
}
