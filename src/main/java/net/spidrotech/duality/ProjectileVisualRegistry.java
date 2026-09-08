package net.spidrotech.duality;

import net.spidrotech.duality.entity.AbilityProjectileEntity;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.EntityModel;

import java.util.function.Supplier;
import java.util.function.Function;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Client-side registry of projectile GEOMETRY only, keyed by ProjectileDefinition#modelKey().
 * Each entry also carries a DEFAULT texture (used whenever a definition doesn't set its own via
 * Builder#texture) so a single-skin shape needs no extra per-projectile work, while a whole
 * family of same-shaped, differently-colored bolts can reskin freely via
 * ProjectileDefinition#texture() without ever touching this registry.
 *
 * Register one shape here per distinct Blockbench/MCreator model export - NOT once per
 * projectile power.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "duality", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ProjectileVisualRegistry {
	private record Entry(ResourceLocation key, ModelLayerLocation layer, Supplier<LayerDefinition> layerDefinition, Function<ModelPart, EntityModel<AbilityProjectileEntity>> modelFactory, ResourceLocation defaultTexture) {
	}

	private record Baked(EntityModel<AbilityProjectileEntity> model, ResourceLocation defaultTexture) {
	}

	private static final Map<ResourceLocation, Entry> PENDING = new LinkedHashMap<>();
	private static final Map<ResourceLocation, Baked> BAKED = new LinkedHashMap<>();

	private ProjectileVisualRegistry() {
	}

	/** One call per SHAPE - see AbilityProjectileRenderer / DualityProjectileModels for usage. */
	public static void register(ResourceLocation key, ModelLayerLocation layer, Supplier<LayerDefinition> layerDefinition, Function<ModelPart, EntityModel<AbilityProjectileEntity>> modelFactory, ResourceLocation defaultTexture) {
		if (PENDING.putIfAbsent(key, new Entry(key, layer, layerDefinition, modelFactory, defaultTexture)) != null) {
			throw new IllegalStateException("Duplicate projectile shape key: " + key);
		}
	}

	@SubscribeEvent
	public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
		DualityProjectileModels.registerAll();
		for (Entry entry : PENDING.values()) {
			event.registerLayerDefinition(entry.layer(), entry.layerDefinition());
		}
	}

	private static void bakeAll(EntityRendererProvider.Context context) {
		if (!BAKED.isEmpty())
			return;
		for (Entry entry : PENDING.values()) {
			ModelPart root = context.bakeLayer(entry.layer());
			BAKED.put(entry.key(), new Baked(entry.modelFactory().apply(root), entry.defaultTexture()));
		}
	}

	/** Bakes every registered shape (idempotent - safe to call from multiple renderer
	 *  constructors) and hands back one arbitrary baked model, purely to satisfy MobRenderer's
	 *  constructor requirement for a non-null model up front. MUST be called as part of the
	 *  same expression passed to super(...) - baking on a separate statement AFTER super(...)
	 *  is too late, since super(...) must be the first statement in the constructor and any
	 *  model lookup inside it needs BAKED already populated by then. */
	public static EntityModel<AbilityProjectileEntity> bakeAllAndGetDefault(EntityRendererProvider.Context context) {
		bakeAll(context);
		if (BAKED.isEmpty()) {
			throw new IllegalStateException("No projectile shapes registered - DualityProjectileModels.registerAll() must register at least one before AbilityProjectileEntity can render.");
		}
		return BAKED.values().iterator().next().model();
	}

	public static EntityModel<AbilityProjectileEntity> getModel(ResourceLocation shapeKey) {
		Baked baked = BAKED.get(shapeKey);
		return baked != null ? baked.model() : null;
	}

	public static ResourceLocation getDefaultTexture(ResourceLocation shapeKey) {
		Baked baked = BAKED.get(shapeKey);
		return baked != null ? baked.defaultTexture() : null;
	}
}