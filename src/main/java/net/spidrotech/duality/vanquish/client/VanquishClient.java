package net.spidrotech.duality.vanquish.client;

import net.spidrotech.duality.vanquish.Vanquish;

import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;

/**
 * The part of a vanquish (see Vanquish) that's only drawn: the demon trembling as the fire cracks
 * spread, then being dragged down through the ground. The ground hides them as they go, since the
 * model is simply drawn lower and the terrain covers it.
 *
 * <p>The vanquished player's own view switches to third person for the length of it, so they
 * watch it happen to them, and goes back to whatever it was afterwards.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class VanquishClient {
	/** How far down they're pulled - enough to take the whole body under. */
	private static final float SINK_DEPTH = 2.4f;
	private static final float MAX_TREMBLE = 0.035f;

	private static final Map<Integer, Long> STARTED = new ConcurrentHashMap<>();
	private static final Set<Integer> PUSHED = new HashSet<>();
	private static CameraType cameraBefore;

	private VanquishClient() {
	}

	public static void start(int entityId, long startTick) {
		STARTED.put(entityId, startTick);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getId() == entityId && cameraBefore == null) {
			cameraBefore = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
	}

	/** Ticks since this entity's vanquish began (with the partial tick), or -1 if it isn't. */
	private static float elapsed(int entityId, float partialTick) {
		Long start = STARTED.get(entityId);
		Minecraft mc = Minecraft.getInstance();
		if (start == null || mc.level == null)
			return -1;
		return mc.level.getGameTime() - start + partialTick;
	}

	@SubscribeEvent
	public static void onRenderPre(RenderPlayerEvent.Pre event) {
		int id = event.getEntity().getId();
		float elapsed = elapsed(id, event.getPartialTick());
		if (elapsed < 0)
			return;
		if (elapsed >= Vanquish.DURATION_TICKS) {
			// Gone under. Vanilla would now draw the corpse back up at ground level and topple it -
			// so keep it hidden while it's dead, and for the moment until the death arrives. Once
			// they're alive again (respawned - same entity id), this vanquish is over.
			if (event.getEntity().isDeadOrDying() || elapsed < Vanquish.DURATION_TICKS + 10) {
				event.setCanceled(true);
				return;
			}
			STARTED.remove(id);
			return;
		}
		event.getPoseStack().pushPose();
		PUSHED.add(id);
		if (elapsed < Vanquish.SINK_START) {
			float tremble = MAX_TREMBLE * (elapsed / Vanquish.SINK_START);
			event.getPoseStack().translate((Math.random() - 0.5) * 2 * tremble, 0, (Math.random() - 0.5) * 2 * tremble);
		} else {
			float t = Math.min(1f, (elapsed - Vanquish.SINK_START) / (float) (Vanquish.DURATION_TICKS - Vanquish.SINK_START));
			// Slow at first, then yanked down.
			event.getPoseStack().translate(0, -SINK_DEPTH * t * t, 0);
		}
	}

	@SubscribeEvent
	public static void onRenderPost(RenderPlayerEvent.Post event) {
		if (PUSHED.remove(event.getEntity().getId()))
			event.getPoseStack().popPose();
	}

	/** Forgets finished vanquishes (a few ticks late, so the last frame isn't cut short), and gives
	 *  the vanquished player their camera back. */
	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			STARTED.clear();
			restoreCamera(mc);
			return;
		}
		long now = mc.level.getGameTime();
		// A long safety net only - the render hook ends a vanquish as soon as the player is back.
		STARTED.entrySet().removeIf(entry -> now - entry.getValue() > Vanquish.DURATION_TICKS + 1200);
		// Their camera comes back once they've gone under (the death screen is up by then).
		if (cameraBefore != null && (mc.player == null || !STARTED.containsKey(mc.player.getId()) || now - STARTED.get(mc.player.getId()) >= Vanquish.DURATION_TICKS))
			restoreCamera(mc);
	}

	private static void restoreCamera(Minecraft mc) {
		if (cameraBefore != null) {
			mc.options.setCameraType(cameraBefore);
			cameraBefore = null;
		}
	}
}
