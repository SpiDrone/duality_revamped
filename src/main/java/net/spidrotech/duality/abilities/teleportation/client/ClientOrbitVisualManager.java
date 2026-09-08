package net.spidrotech.duality.abilities.teleportation.client;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;

import java.util.function.Function;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Client-only "ghost" visual entities that mark selected teleport passengers - visible ONLY to
 * this client. The ghost entity is never added to the level (Level#addFreshEntity would sync it
 * to the server and every nearby player); instead it's constructed once, ticked, and rendered
 * manually every frame via a direct EntityRenderDispatcher#render call, completely bypassing the
 * normal entity list. Nobody else - not the server, not other players - ever knows this entity
 * exists.
 *
 * POSITIONING: the ghost is anchored DIRECTLY on the target's own position every frame (see
 * BELT_HEIGHT), not revolving around it at some radius - see git history / chat log for the
 * earlier orbit-radius version this replaced, which fought the model's own spin animation.
 *
 * ANIMATION TIMING (important, previously the source of a real bug): LivingEntityRenderer feeds
 * a model's setupAnim an "ageInTicks" of entity.tickCount + partialTick, which only climbs
 * smoothly if tickCount increments exactly once per real game tick. Since this ghost is never
 * added to the level and only gets .tick() called manually (see onClientTick below), that
 * increment isn't reliably in sync with render calls the way a normal level-resident entity's
 * would be - when tickCount effectively stalls, ageInTicks collapses to just the fractional
 * partialTick, which resets 0->1 every tick (20/sec), producing a rapid back-and-forth jitter
 * in any rotation derived from it instead of a smooth spin. FIX: overwrite the ghost's
 * tickCount directly from mc.level.getGameTime() every render frame, below - the same reliable,
 * monotonic time source TeleportMarkerRenderer's own gameTimeSeconds already uses successfully
 * elsewhere in this codebase. This makes ageInTicks == getGameTime() + partialTick on every
 * single frame, completely decoupled from whatever onClientTick's manual .tick() calls are or
 * aren't doing correctly.
 *
 * SETUP (required, once, at client startup):
 *   ClientOrbitVisualManager.setSpawner(level -> new YourOrbitEntity(YourModEntities.ORBIT_VISUAL.get(), level));
 *
 * USAGE: select(UUID)/deselect(UUID) attach/remove the visual for a given target entity - see
 * client.TeleportPickerInputHandler for where passenger selection calls these.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ClientOrbitVisualManager {
	private static final double BELT_HEIGHT = 0.9;

	private record GhostEntry(Entity ghost, UUID targetId) {
	}

	private static final Map<UUID, GhostEntry> ACTIVE = new LinkedHashMap<>();
	private static Function<ClientLevel, Entity> spawner;

	private ClientOrbitVisualManager() {
	}

	public static void setSpawner(Function<ClientLevel, Entity> newSpawner) {
		spawner = newSpawner;
	}

	public static boolean isSelected(UUID targetId) {
		return ACTIVE.containsKey(targetId);
	}

	public static void select(UUID targetId) {
		if (ACTIVE.containsKey(targetId) || spawner == null)
			return;
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.level instanceof ClientLevel level))
			return;
		Entity ghost = spawner.apply(level);
		ACTIVE.put(targetId, new GhostEntry(ghost, targetId));
	}

	public static void deselect(UUID targetId) {
		ACTIVE.remove(targetId);
	}

	public static void clearAll() {
		ACTIVE.clear();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (ACTIVE.isEmpty())
			return;
		for (GhostEntry entry : ACTIVE.values()) {
			// No longer load-bearing for animation timing (see class doc ANIMATION TIMING) -
			// kept only for any other incidental LivingEntity bookkeeping (effect durations,
			// etc.) that might expect tick() to run. Safe to remove entirely if you find it's
			// not needed for anything.
			entry.ghost().tick();
		}
	}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES)
			return;
		if (ACTIVE.isEmpty())
			return;
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.level instanceof ClientLevel level) || mc.player == null)
			return;
		float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
		Camera camera = event.getCamera();
		Vec3 camPos = camera.getPosition();
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
		// See class doc ANIMATION TIMING - this is the fix. Overwriting tickCount here means
		// every ghost's ageInTicks is exactly getGameTime()+partialTick this frame, regardless
		// of onClientTick's own increment cadence.
		int gameTickCount = (int) level.getGameTime();
		boolean renderedAny = false;
		for (GhostEntry entry : ACTIVE.values()) {
			Entity target = findByUuid(level, entry.targetId());
			if (target == null)
				continue;
			Vec3 targetPos = target.getPosition(partialTick);
			Vec3 ghostPos = targetPos.add(0, BELT_HEIGHT, 0);
			Entity ghost = entry.ghost();
			ghost.tickCount = gameTickCount;
			ghost.setPos(ghostPos.x, ghostPos.y, ghostPos.z);
			ghost.setYRot(0f);
			int packedLight = LevelRenderer.getLightColor(level, ghost.blockPosition());
			mc.getEntityRenderDispatcher().render(ghost, ghostPos.x - camPos.x, ghostPos.y - camPos.y, ghostPos.z - camPos.z, 0f, partialTick, poseStack, bufferSource, packedLight);
			renderedAny = true;
		}
		if (renderedAny) {
			bufferSource.endBatch();
		}
	}

	private static Entity findByUuid(ClientLevel level, UUID id) {
		for (Entity entity : level.entitiesForRendering()) {
			if (entity.getUUID().equals(id)) {
				return entity;
			}
		}
		return null;
	}
}