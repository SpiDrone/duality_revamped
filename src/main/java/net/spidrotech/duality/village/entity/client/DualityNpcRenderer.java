package net.spidrotech.duality.village.entity.client;

import net.spidrotech.duality.village.entity.DualityNpcEntities;
import net.spidrotech.duality.village.entity.DualityNpcEntity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws village NPCs. Deliberately borrows vanilla's villager model and texture: the point of this
 * pass is that records stop resolving to actual vanilla villagers and start resolving to an entity
 * this mod controls, which is a behavioural change, not a visual one. Nothing here needs a new
 * asset, and swapping in a duality model later touches only this class.
 *
 * <p>When per-species or per-job looks arrive, switch on {@link DualityNpcEntity#species()} /
 * {@link DualityNpcEntity#jobId()} in getTextureLocation - both are synced for exactly that.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public class DualityNpcRenderer extends MobRenderer<DualityNpcEntity, VillagerModel<DualityNpcEntity>> {
	private static final ResourceLocation TEXTURE = ResourceLocation.parse("minecraft:textures/entity/villager/villager.png");

	public DualityNpcRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(DualityNpcEntity entity) {
		return TEXTURE;
	}

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(DualityNpcEntities.NPC.get(), DualityNpcRenderer::new);
	}
}
