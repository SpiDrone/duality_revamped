package net.spidrotech.duality.mixin;

import net.spidrotech.duality.abilities.demon.client.ShimmerRenderer;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.EntityModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Makes a shimmering caster (see ShimmerEffects.SHIMMERING) actually render translucent. Two
 * injections are needed, because either one alone still draws a fully opaque player:
 *
 *   1. getRenderType - vanilla's normal entity type is alpha-TESTED (the shader discards below
 *      0.1 alpha and writes everything else at full opacity), so an alpha of 0.6 through it is
 *      indistinguishable from 1.0. Forced to RenderType.itemEntityTranslucentCull, which is a
 *      real TRANSLUCENT_TRANSPARENCY blending type - and is the very type vanilla's own render()
 *      reaches for when it draws an invisible entity as a translucent ghost for a teammate who
 *      can see through Invisibility, so this rides a proven path rather than a hand-built one.
 *   2. the color argument to the model's renderToBuffer - that color is the vertex alpha, and
 *      vanilla hardcodes it to -1 (fully opaque) for any entity that isn't in that teammate-ghost
 *      case. Left alone, step 1 just gives us opaque geometry drawn through a blending pipeline.
 *      See ShimmerRenderer for why the alpha has to ride along as a vertex color rather than
 *      being applied around the call with RenderSystem.setShaderColor.
 *
 * TARGETING NOTE: the `render` target carries its full descriptor on purpose. LivingEntityRenderer
 * also holds a synthetic bridge `render(Entity, ...)` from erasing EntityRenderer<T>, and a bare
 * "render" would match that bridge too - which contains no renderToBuffer call, so the injector
 * would fail to find its injection point there and take the whole game down at boot (this config
 * runs with injectors.defaultRequire = 1). The renderToBuffer owner is EntityModel, not Model,
 * because that is the static type of LivingEntityRenderer's own `model` field at the call site,
 * which is what the invokevirtual actually names - both verified against the compiled bytecode.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

	@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
	private void duality$shimmerTranslucent(T entity, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> callback) {
		if (!ShimmerRenderer.isShimmering(entity))
			return;
		LivingEntityRenderer<T, M> self = (LivingEntityRenderer<T, M>) (Object) this;
		ResourceLocation texture = self.getTextureLocation(entity);
		callback.setReturnValue(RenderType.itemEntityTranslucentCull(texture));
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", //
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"), index = 4)
	private int duality$shimmerAlpha(int color) {
		return ShimmerRenderer.shimmerColorOr(color);
	}
}
