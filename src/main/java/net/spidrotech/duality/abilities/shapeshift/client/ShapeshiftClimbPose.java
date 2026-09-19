package net.spidrotech.duality.abilities.shapeshift.client;

import net.spidrotech.duality.abilities.demon.WallClimbing;
import net.spidrotech.duality.abilities.shapeshift.Shapeshift;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How far onto a wall a shapeshifted player's model should be tipped, for any form - not tied to
 * the spider. A form opts in to being drawn on the wall in ShapeshiftFormRenderers; this just keeps
 * the numbers every such form needs.
 *
 * <p>The amount eases in and out over a few ticks, the same as the spider mob, so the model rolls
 * onto the wall instead of snapping 90 degrees in one frame. The wall's yaw is held after the player
 * leaves the wall, so the model rolls back upright from the side it was actually on.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ShapeshiftClimbPose {
	/** Per-tick easing toward on/off the wall - same rate as the spider mob's lean. */
	private static final float EASE_PER_TICK = 0.15f;

	private static final class Pose {
		float amount;
		float amountO;
		float wallYaw;
	}

	private static final Map<Player, Pose> POSES = new WeakHashMap<>();

	private ShapeshiftClimbPose() {
	}

	/** 0 = upright, 1 = flat against the wall, interpolated for this frame. */
	public static float amount(Player player, float partialTick) {
		Pose pose = POSES.get(player);
		return pose == null ? 0f : Mth.lerp(partialTick, pose.amountO, pose.amount);
	}

	/** Yaw, in degrees, pointing into the wall - the way the model should face while on it. */
	public static float wallYaw(Player player) {
		Pose pose = POSES.get(player);
		return pose == null ? 0f : pose.wallYaw;
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			POSES.clear();
			return;
		}
		if (mc.isPaused())
			return;
		for (Player player : mc.level.players()) {
			if (Shapeshift.current(player).isEmpty()) {
				POSES.remove(player);
				continue;
			}
			Pose pose = POSES.computeIfAbsent(player, p -> new Pose());
			boolean climbing = WallClimbing.isClimbing(player);
			if (climbing) {
				Direction wall = WallClimbing.wallDirection(player);
				if (wall != null)
					pose.wallYaw = wall.toYRot();
			}
			pose.amountO = pose.amount;
			pose.amount = Mth.approach(pose.amount, climbing ? 1f : 0f, EASE_PER_TICK);
		}
	}
}
