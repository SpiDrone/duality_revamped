package net.spidrotech.duality.abilities.vampire.client;

import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;

/**
 * The on-screen "yank" when a dash goes off (triggered by the server's DashFeedbackPayload):
 *
 *  - FOV punch: widens dashing forward, pulls in dashing backward
 *  - camera: a decaying roll wobble plus a small pitch kick, so the view is jerked and then settles
 *  - overlay: dark crimson closing in from the screen edges, and speed streaks flying outward
 *
 * All three share one envelope - a fast punch in, then a slower ease out over DURATION_MS. Camera
 * and FOV are scaled by the vanilla "FOV Effects" slider and the overlay by "Screen Effects", so
 * players who've turned those down get a matching, gentler version.
 *
 * Wall-clock timed (not ticks) so it stays smooth at any framerate and isn't frozen by the pause
 * menu mid-effect.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class DashScreenEffect {
	// ---- tunable ----
	private static final long DURATION_MS = 450;
	private static final float ATTACK_FRACTION = 0.12f;
	private static final double FORWARD_FOV_KICK = 0.16;
	private static final double BACKWARD_FOV_KICK = -0.10;
	private static final float ROLL_DEGREES = 3.5f;
	private static final float PITCH_KICK_DEGREES = 1.2f;
	private static final int VIGNETTE_LAYERS = 10;
	private static final int VIGNETTE_MAX_ALPHA = 90;
	private static final int VIGNETTE_RGB = 0x2A0006;
	private static final int STREAK_COUNT = 18;
	private static final int STREAK_MAX_ALPHA = 140;
	private static final int STREAK_RGB = 0xFFD6D6;

	private static long startMs = -1;
	private static boolean backwards;
	private static float rollSign = 1f;

	private DashScreenEffect() {
	}

	public static void play(boolean dashedBackwards) {
		startMs = Util.getMillis();
		backwards = dashedBackwards;
		rollSign = (startMs & 1) == 0 ? 1f : -1f; // lean a different way from dash to dash
	}

	/** 0..1 through the effect, or -1 when nothing is playing. */
	private static float progress() {
		if (startMs < 0)
			return -1f;
		float t = (Util.getMillis() - startMs) / (float) DURATION_MS;
		if (t >= 1f) {
			startMs = -1;
			return -1f;
		}
		return t;
	}

	/** Fast punch in, slow ease out. */
	private static float envelope(float t) {
		if (t < ATTACK_FRACTION)
			return t / ATTACK_FRACTION;
		float out = 1f - (t - ATTACK_FRACTION) / (1f - ATTACK_FRACTION);
		return out * out;
	}

	@SubscribeEvent
	public static void onComputeFov(ViewportEvent.ComputeFov event) {
		float t = progress();
		if (t < 0)
			return;
		double scale = Minecraft.getInstance().options.fovEffectScale().get();
		double kick = (backwards ? BACKWARD_FOV_KICK : FORWARD_FOV_KICK) * envelope(t) * scale;
		event.setFOV(event.getFOV() * (1.0 + kick));
	}

	@SubscribeEvent
	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		float t = progress();
		if (t < 0)
			return;
		float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
		event.setRoll(event.getRoll() + rollSign * ROLL_DEGREES * scale * Mth.sin(t * Mth.PI * 3f) * (1f - t));
		event.setPitch(event.getPitch() + (backwards ? PITCH_KICK_DEGREES : -PITCH_KICK_DEGREES) * scale * envelope(t));
	}

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Pre event) {
		float t = progress();
		if (t < 0)
			return;
		float strength = envelope(t) * Minecraft.getInstance().options.screenEffectScale().get().floatValue();
		if (strength <= 0.01f)
			return;
		GuiGraphics graphics = event.getGuiGraphics();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		drawVignette(graphics, width, height, strength);
		drawStreaks(graphics, width, height, t, strength);
	}

	/** Stacked translucent bands, densest at the rim. */
	private static void drawVignette(GuiGraphics graphics, int width, int height, float strength) {
		int band = Math.max(2, Math.min(width, height) / 40);
		for (int i = 0; i < VIGNETTE_LAYERS; i++) {
			int alpha = (int) (strength * VIGNETTE_MAX_ALPHA * (1f - i / (float) VIGNETTE_LAYERS));
			if (alpha <= 0)
				continue;
			int color = (alpha << 24) | VIGNETTE_RGB;
			int inner = i * band;
			int outer = inner + band;
			graphics.fill(0, inner, width, outer, color);
			graphics.fill(0, height - outer, width, height - inner, color);
			graphics.fill(inner, outer, outer, height - outer, color);
			graphics.fill(width - outer, outer, width - inner, height - outer, color);
		}
	}

	/** Speed lines flying outward from the centre. GuiGraphics only draws axis-aligned rects, so each
	 *  streak is laid along whichever screen axis its direction is closest to. Seeded from the dash's
	 *  start time, so the layout holds still across frames and only the streaks' distance moves. */
	private static void drawStreaks(GuiGraphics graphics, int width, int height, float t, float strength) {
		int alpha = (int) (strength * STREAK_MAX_ALPHA);
		if (alpha <= 0)
			return;
		int color = (alpha << 24) | STREAK_RGB;
		RandomSource random = RandomSource.create(startMs);
		float centerX = width / 2f;
		float centerY = height / 2f;
		float reach = Math.max(width, height) * 0.5f;
		for (int i = 0; i < STREAK_COUNT; i++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			float startRadius = 0.45f + random.nextFloat() * 0.35f;
			float length = (0.08f + random.nextFloat() * 0.12f) * reach;
			float radius = (startRadius + t * 0.5f) * reach;
			float dx = Mth.cos(angle);
			float dy = Mth.sin(angle);
			int x = (int) (centerX + dx * radius);
			int y = (int) (centerY + dy * radius);
			if (Math.abs(dx) >= Math.abs(dy)) {
				int x2 = (int) (x + Math.signum(dx) * length);
				graphics.fill(Math.min(x, x2), y, Math.max(x, x2), y + 1, color);
			} else {
				int y2 = (int) (y + Math.signum(dy) * length);
				graphics.fill(x, Math.min(y, y2), x + 1, Math.max(y, y2), color);
			}
		}
	}
}
