package net.spidrotech.duality.abilities.teleportation.client;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;

import java.util.UUID;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Renders every entity currently registered in ClientGlowTargets with a private (this-client-
 * only) glowing outline - the same visual vanilla uses for its "Glowing" effect (a silhouette
 * that reads through walls), but achieved by manually routing the entity's render call through
 * the outline buffer for THIS frame only, rather than by setting the entity's real (synced,
 * everyone-can-see-it) glowing flag. Nothing here touches entity data, so nothing about this is
 * visible to any other client. See ClientGlowTargets for how entries get registered.
 *
 * Reuses the AFTER_PARTICLES render stage - same one TeleportMarkerRenderer already uses
 * successfully in this codebase, rather than guessing at a less-verified stage name.
 *
 * TODO: OutlineBufferSource's exact package/API can drift between Minecraft versions - verify
 * `mc.renderBuffers().outlineBufferSource()` and its setColor(int,int,int,int) /
 * endOutlineBatch() signatures against your actual 1.21.1 mappings if this doesn't compile
 * as-is; this is written against the standard Mojang-mapped names.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ClientGlowRenderer {
	private ClientGlowRenderer() {
	}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES)
			return;
		Map<UUID, Integer> targets = ClientGlowTargets.snapshot();
		if (targets.isEmpty())
			return;
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.level instanceof ClientLevel level) || mc.player == null)
			return;
		float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
		Camera camera = event.getCamera();
		Vec3 camPos = camera.getPosition();
		PoseStack poseStack = event.getPoseStack();
		OutlineBufferSource outlineSource = mc.renderBuffers().outlineBufferSource();
		boolean drewAny = false;
		for (Map.Entry<UUID, Integer> entry : targets.entrySet()) {
			Entity entity = findByUuid(level, entry.getKey());
			if (entity == null)
				continue; // out of render distance, or has since despawned - just skip it this frame
			int argb = entry.getValue();
			outlineSource.setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >> 24) & 0xFF);
			Vec3 renderPos = entity.getPosition(partialTick);
			int packedLight = LevelRenderer.getLightColor(level, entity.blockPosition());
			mc.getEntityRenderDispatcher().render(entity, renderPos.x - camPos.x, renderPos.y - camPos.y, renderPos.z - camPos.z, entity.getYRot(), partialTick, poseStack, outlineSource, packedLight);
			drewAny = true;
		}
		if (drewAny) {
			outlineSource.endOutlineBatch();
		}
	}

	/** ClientLevel doesn't expose ServerLevel's getEntity(UUID) - fall back to a linear scan
	 *  over the entities already being considered for rendering this frame. Fine at the scale
	 *  this is used for (a handful of glow targets at most); not something to reach for with
	 *  hundreds of entries. */
	private static Entity findByUuid(ClientLevel level, UUID id) {
		for (Entity entity : level.entitiesForRendering()) {
			if (entity.getUUID().equals(id)) {
				return entity;
			}
		}
		return null;
	}
}