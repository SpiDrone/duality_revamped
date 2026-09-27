package net.spidrotech.duality.abilities.vampire.client;

import net.spidrotech.duality.abilities.vampire.BiteNetwork;

import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;

import com.mojang.math.Axis;

/**
 * The bite's lunge, client side.
 *
 * <ul>
 * <li>When the local vampire bites (a bare-handed attack in vampire mode - see BiteNetwork#isBite),
 * the arm swing is suppressed. Not swinging also means no swing packet, so nobody else sees an arm
 * swing either.
 * <li>Instead the player's whole model tips forward from the feet for {@link #DURATION_TICKS}, easing
 * out to {@link #MAX_ANGLE} and back - a lunge at the target's neck. Other players' clients are told
 * by the server (BiteNetwork); the biter's own starts at once.
 * <li>In first person, where you can't see your own body, the camera dips instead.
 * </ul>
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class BiteLean {
	private static final float DURATION_TICKS = 8f;
	private static final float MAX_ANGLE = 28f;
	/** How far the first-person camera dips, as a share of the lean. */
	private static final float CAMERA_SHARE = 0.4f;

	/** Entity id to the game time (ticks) its lean started. */
	private static final Map<Integer, Long> STARTED = new ConcurrentHashMap<>();
	/** Models whose pose this frame was tipped in Pre, so Post knows to undo it. */
	private static final Set<Integer> PUSHED = new HashSet<>();

	private BiteLean() {
	}

	public static void start(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null)
			STARTED.put(entityId, mc.level.getGameTime());
	}

	/** Degrees of lean right now, or 0 once it's over (and forgotten). */
	private static float angle(int entityId, float partialTick) {
		Long start = STARTED.get(entityId);
		Minecraft mc = Minecraft.getInstance();
		if (start == null || mc.level == null)
			return 0;
		float t = (mc.level.getGameTime() - start + partialTick) / DURATION_TICKS;
		if (t >= 1f || t < 0f) {
			STARTED.remove(entityId);
			return 0;
		}
		return MAX_ANGLE * Mth.sin((float) Math.PI * t);
	}

	@SubscribeEvent
	public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft mc = Minecraft.getInstance();
		if (!event.isAttack() || mc.player == null || !(mc.hitResult instanceof EntityHitResult hit) || !BiteNetwork.isBite(mc.player, hit.getEntity()))
			return;
		event.setSwingHand(false);
		start(mc.player.getId());
	}

	@SubscribeEvent
	public static void onRenderPre(RenderPlayerEvent.Pre event) {
		Player player = event.getEntity();
		float angle = angle(player.getId(), event.getPartialTick());
		if (angle <= 0)
			return;
		// Tip about the axis across the body at the feet: turn to face along the body, pitch forward,
		// turn back - so the renderer's own rotations afterwards are untouched.
		float bodyYaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
		event.getPoseStack().pushPose();
		event.getPoseStack().mulPose(Axis.YP.rotationDegrees(-bodyYaw));
		event.getPoseStack().mulPose(Axis.XP.rotationDegrees(angle));
		event.getPoseStack().mulPose(Axis.YP.rotationDegrees(bodyYaw));
		PUSHED.add(player.getId());
	}

	@SubscribeEvent
	public static void onRenderPost(RenderPlayerEvent.Post event) {
		if (PUSHED.remove(event.getEntity().getId()))
			event.getPoseStack().popPose();
	}

	@SubscribeEvent
	public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || event.getCamera().getEntity() != mc.player || !mc.options.getCameraType().isFirstPerson())
			return;
		float angle = angle(mc.player.getId(), (float) event.getPartialTick());
		if (angle > 0)
			event.setPitch(event.getPitch() + angle * CAMERA_SHARE);
	}
}
