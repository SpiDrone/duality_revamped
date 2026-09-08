package net.spidrotech.duality.abilities.teleportation.client;

import org.checkerframework.checker.units.qual.g;

import net.spidrotech.duality.abilities.teleportation.TeleportMarker;
import net.spidrotech.duality.abilities.teleportation.TeleportDirectionUtil;
import net.spidrotech.duality.DimensionStackConfig;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;

import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Draws the current TeleportPickerClientState markers as camera-facing 2D icons - a "billboard,"
 * the same technique vanilla uses for entity nameplates. Purely a client-side overlay: nothing
 * here is a real entity, and nothing here is visible to anyone but this player.
 *
 * LAYOUT (radial, player-anchored - replaces an earlier real-bearing "compass" design): each
 * marker sits at a FIXED angle and a FIXED radius from the PLAYER, not from the target's real
 * location - same-tier-or-higher destinations (see DimensionStackConfig#tier; same dimension
 * always counts as equal) form a ring floating above eye level, strictly-lower-tier destinations
 * form a ring below the player's feet. This is a deliberate departure from a true compass: the
 * old design computed a real bearing toward each target's actual position, which is inherently
 * unstable up close (a small step near the target's horizontal position swings the angle a lot,
 * since you're near the pivot of the bearing itself) - no amount of smoothing fixes that at the
 * root, because the instability comes from the geometry, not the framerate. Anchoring purely to
 * the PLAYER's own position instead removes the instability entirely: moving around just
 * translates the whole ring smoothly, since a marker's position never depends on where its real
 * target actually is, only on which ring it belongs to and which fixed slot it was assigned.
 *
 * SLOT ASSIGNMENT: markers are sorted by selectionId before assigning ring slots (equally spaced
 * around 360 degrees), so slot assignment doesn't reshuffle between frames just because the
 * server happened to return the list in a different order. Slots only actually move when a
 * ring's MEMBER COUNT changes (a call expires, a waypoint is added/removed) - angular smoothing
 * below eases that occasional reflow in over a few frames instead of snapping, since it's the
 * only remaining source of a marker's angle changing frame-to-frame.
 *
 * TOOLTIP: whichever marker is currently under the player's crosshair (BROWSING) or the
 * confirmed marker (LOCKED) gets its label drawn above its icon using Font.DisplayMode.SEE_THROUGH
 * - the same draw mode vanilla uses for glowing entity nameplates, so it reads through walls
 * exactly like the icon itself does.
 *
 * DEPTH TESTING - ALWAYS_PASS_DEPTH_TEST below, NOT vanilla's RenderStateShard.NO_DEPTH_TEST
 * (found the hard way, via screenshots showing terrain and glass still occluding the marker).
 * NO_DEPTH_TEST's underlying depth func value is 519, which numerically IS GL_ALWAYS - but
 * vanilla's own DepthTestStateShard#setupRenderState() special-cases exactly that value as a
 * "don't touch depth state" SKIP, not "set depth func to always pass": `if (this.depthFunc !=
 * 519) { enableDepthTest(); depthFunc(this.depthFunc); }`. Since 519 fails that check,
 * NO_DEPTH_TEST issues no GL calls at all - it just leaves whatever depth state the terrain pass
 * already left active (enabled, LEQUAL). That's fine in contexts like GUI rendering where depth
 * testing is already globally off beforehand, but in world-space rendering it does nothing,
 * which is exactly why terrain and glass were still winning. ALWAYS_PASS_DEPTH_TEST is a
 * hand-built RenderStateShard.DepthTestStateShard subclass that calls
 * RenderSystem.depthFunc(GL_ALWAYS) directly, bypassing that skip entirely, so our fragments
 * genuinely always pass regardless of what's already in the depth buffer.
 *
 * The render hook itself runs at AFTER_WEATHER (the latest stage NeoForge exposes) -
 * solid/cutout/translucent terrain, entities, particles, and weather have ALL already been drawn
 * to the framebuffer by that point, so drawing here with a real always-pass depth test
 * guarantees these markers appear in front of everything else in the scene, blocks, glass, and
 * entities included.
 * TODO: verify AFTER_WEATHER exists under that exact name in your NeoForge version - stage names
 * have shifted before (see WhitelighterCallListener's similar TODO on getRawText()).
 *
 * BROWSING: all markers render; whichever one the player is currently looking at gets that
 * outline in plain white (feedback for "you're looking at this one") plus its name tooltip.
 * LOCKED: only the confirmed destination renders, outlined in TeleportPickerClientState's
 * glowStyle() instead (visual confirmation of the actual destination), with its tooltip always
 * shown since it's the only marker left on screen.
 *
 * Shared by every teleport ability's picker, not Orb-specific.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class TeleportMarkerRenderer {
	// ---- ring geometry - purely relative to the PLAYER, never to a target's real position ----
	private static final double RADIAL_RADIUS = 5; // horizontal distance from the player to every icon
	private static final double SKY_HEIGHT = 5.0; // blocks above eye level for the "sky" ring
	private static final double GROUND_DEPTH = 5.0; // blocks below the feet for the "ground" ring
	// Icons are 21x21 source pixels - this is a world-space size, not a pixel size, but keeping
	// it modest (rather than large) is what keeps that resolution reading crisp instead of
	// visibly blurry/blocky at typical viewing distance. Vanilla textures default to nearest-
	// neighbor filtering (no .mcmeta blur flag needed) so the pixel art itself stays sharp.
	private static final float ICON_SIZE = 0.6f;
	// How much larger the outline silhouette is drawn than the real icon - at 21px source, 1.18x
	// works out to roughly a 2px outline. Tune directly if you want a thicker/thinner line.
	private static final float OUTLINE_SCALE = 1.18f;
	private static final int TARGETED_WHITE = 0xFFFFFFFF;
	// Height above the icon's own center that the name tooltip renders at.
	private static final double TOOLTIP_HEIGHT = 0.55;
	// One RenderType per icon texture is required for BOTH caches below (the texture itself is
	// baked into the render state) - cached so repeated frames don't rebuild it for the same icon.
	private static final Map<ResourceLocation, RenderType> ICON_TYPE_CACHE = new ConcurrentHashMap<>();
	private static final Map<ResourceLocation, RenderType> OUTLINE_TYPE_CACHE = new ConcurrentHashMap<>();
	// Raw GL depth-func constants - avoids pulling in an LWJGL GL11 import just for two ints, and
	// matches the literal values vanilla's own DepthTestStateShard uses internally.
	private static final int GL_ALWAYS = 519;
	private static final int GL_LEQUAL = 515;
	/** Hand-built depth shard that genuinely always passes the depth test - see class doc DEPTH
	 *  TESTING for why vanilla's own RenderStateShard.NO_DEPTH_TEST can't be used for this (it
	 *  silently no-ops for this exact depth-func value instead of disabling the test).
	 *  CompositeState.builder().setDepthTestState(...) requires the specific
	 *  RenderStateShard.DepthTestStateShard type, not just any RenderStateShard, so this
	 *  subclasses it directly and overrides setupRenderState()/clearRenderState() to issue the
	 *  real GL calls ourselves instead of going through the buggy constructor logic - the
	 *  super(...) call's own depthFunc is irrelevant since these overrides never invoke it. */
	private static final RenderStateShard.DepthTestStateShard ALWAYS_PASS_DEPTH_TEST = new RenderStateShard.DepthTestStateShard("duality_always_pass_depth", GL_ALWAYS) {
		@Override
		public void setupRenderState() {
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(GL_ALWAYS);
		}

		@Override
		public void clearRenderState() {
			RenderSystem.enableDepthTest();
			RenderSystem.depthFunc(GL_LEQUAL);
		}
	};
	// ---- angular smoothing state, keyed by TeleportMarker#selectionId - see class doc SLOT
	// ASSIGNMENT. Fraction of the remaining angular gap closed per rendered frame; lower is
	// smoother/laggier, higher is snappier/twitchier.
	private static final float SMOOTHING_FACTOR = 0.2f;
	private static final Map<String, Float> smoothedYaw = new HashMap<>();
	private static final Map<String, Float> smoothedPitch = new HashMap<>();

	/** One marker's fixed ring slot, resolved to a real world position for this frame - the
	 *  position only depends on the player's OWN current position/eye height and the marker's
	 *  assigned slot, never on the marker's real target location. See class doc LAYOUT. */
	private record RadialPlacement(TeleportMarker marker, Vec3 worldPos) {
	}

	/** Custom stand-in for RenderType.entityCutoutNoCull(texture) - same shader
	 *  (RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER), same vertex format (NEW_ENTITY), same texture/lightmap/overlay
	 *  behavior, but with depth testing disabled so terrain can't hide the marker.
	 *  entityCutoutNoCull itself has no way to opt out of its baked-in depth test, hence
	 *  rebuilding it here instead of reusing it. */
	private static RenderType iconTypeFor(ResourceLocation texture) {
		return ICON_TYPE_CACHE.computeIfAbsent(texture,
				tex -> RenderType.create("duality_teleport_marker_icon_" + tex.toString().replace(':', '_').replace('/', '_'), DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
						RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER).setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
								.setLightmapState(RenderStateShard.LIGHTMAP).setOverlayState(RenderStateShard.OVERLAY).setCullState(RenderStateShard.NO_CULL).setDepthTestState(ALWAYS_PASS_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE)
								.createCompositeState(true)));
	}

	private static RenderType outlineTypeFor(ResourceLocation texture) {
		return OUTLINE_TYPE_CACHE.computeIfAbsent(texture, tex -> RenderType.create("duality_teleport_marker_outline_" + tex.toString().replace(':', '_').replace('/', '_'), DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false,
				true, RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_OUTLINE_SHADER).setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
						// Normal alpha blending (not additive) so it reads as a clean solid
						// color rather than a glowing haze - swap for
						// RenderStateShard.LIGHTNING_TRANSPARENCY if you want more of an
						// additive glow look instead.
						.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setWriteMaskState(RenderStateShard.COLOR_WRITE).setCullState(RenderStateShard.NO_CULL).setDepthTestState(ALWAYS_PASS_DEPTH_TEST).createCompositeState(false)));
	}

	private TeleportMarkerRenderer() {
	}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER)
			return;
		if (!TeleportPickerClientState.isActive()) {
			// Picker closed - drop smoothing state so a marker doesn't "remember" a stale
			// angle the next time the picker opens with a different set of destinations.
			smoothedYaw.clear();
			smoothedPitch.clear();
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null)
			return;
		float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
		Camera camera = event.getCamera();
		Vec3 camPos = camera.getPosition();
		Vec3 eyePos = mc.player.getEyePosition(partialTick);
		Vec3 feetPos = mc.player.getPosition(partialTick);
		Level viewerLevel = mc.player.level();
		float gameTimeSeconds = (mc.level.getGameTime() + partialTick) / 20f;
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
		Font font = mc.font;
		int packedLight = 0xF000F0; // full-bright - a magical destination marker shouldn't dim in the dark
		if (TeleportPickerClientState.isLocked()) {
			TeleportMarker locked = TeleportPickerClientState.selectedMarker();
			List<RadialPlacement> placements = computeRadialPlacements(List.of(locked), viewerLevel.dimension(), eyePos, feetPos);
			pruneSmoothingState(Set.of(locked.selectionId()));
			if (placements.isEmpty()) {
				bufferSource.endBatch();
				return;
			}
			RadialPlacement placement = placements.get(0);
			int glowColor = TeleportPickerClientState.glowStyle().colorAt(gameTimeSeconds);
			renderMarker(poseStack, bufferSource, camera, camPos, placement, true, glowColor);
			renderTooltip(poseStack, bufferSource, camera, camPos, font, placement, packedLight);
			bufferSource.endBatch();
			return;
		}
		List<TeleportMarker> markers = TeleportPickerClientState.markers();
		if (markers.isEmpty()) {
			pruneSmoothingState(Set.of());
			return;
		}
		// Sorted for stable slot assignment - see class doc SLOT ASSIGNMENT.
		List<TeleportMarker> sorted = markers.stream().sorted(Comparator.comparing(TeleportMarker::selectionId)).toList();
		List<RadialPlacement> placements = computeRadialPlacements(sorted, viewerLevel.dimension(), eyePos, feetPos);
		Set<String> currentKeys = new HashSet<>();
		for (TeleportMarker marker : sorted) {
			currentKeys.add(marker.selectionId());
		}
		pruneSmoothingState(currentKeys);
		List<TeleportDirectionUtil.Bearing<RadialPlacement>> bearings = new ArrayList<>(placements.size());
		for (RadialPlacement placement : placements) {
			bearings.add(smoothedBearing(bearingFor(eyePos, placement)));
		}
		RadialPlacement targeted = TeleportDirectionUtil.closestToLook(bearings, mc.player.getYRot(), mc.player.getXRot(), TeleportDirectionUtil.LOOK_SELECT_THRESHOLD_DEGREES);
		TeleportPickerClientState.setCurrentlyTargeted(targeted != null ? targeted.marker() : null);
		for (RadialPlacement placement : placements) {
			boolean isTargeted = placement.equals(targeted);
			renderMarker(poseStack, bufferSource, camera, camPos, placement, isTargeted, TARGETED_WHITE);
			if (isTargeted) {
				renderTooltip(poseStack, bufferSource, camera, camPos, font, placement, packedLight);
			}
		}
		bufferSource.endBatch();
	}

	/** True if a target's dimension is the same tier or higher than the viewer's (same dimension
	 *  always counts as equal, matching TeleportDirectionUtil's own convention) - such markers
	 *  form the "sky" ring above the player; everything else forms the "ground" ring below. */
	private static boolean isSkyBand(ResourceKey<Level> viewerDimension, ResourceKey<Level> targetDimension) {
		if (viewerDimension == targetDimension)
			return true;
		return DimensionStackConfig.tier(targetDimension) >= DimensionStackConfig.tier(viewerDimension);
	}

	/** Splits the sorted marker list into its sky/ground rings and resolves each to a real world
	 *  position for THIS frame, based purely on the player's current eye/feet position - see
	 *  class doc LAYOUT. */
	private static List<RadialPlacement> computeRadialPlacements(List<TeleportMarker> sortedMarkers, ResourceKey<Level> viewerDimension, Vec3 eyePos, Vec3 feetPos) {
		List<TeleportMarker> sky = new ArrayList<>();
		List<TeleportMarker> ground = new ArrayList<>();
		for (TeleportMarker marker : sortedMarkers) {
			if (isSkyBand(viewerDimension, marker.dimension())) {
				sky.add(marker);
			} else {
				ground.add(marker);
			}
		}
		List<RadialPlacement> result = new ArrayList<>(sortedMarkers.size());
		placeRing(sky, eyePos.add(0, SKY_HEIGHT, 0), result);
		placeRing(ground, feetPos.add(0, -GROUND_DEPTH, 0), result);
		return result;
	}

	/** Evenly spaces every marker in one ring around its center point at RADIAL_RADIUS. Angle 0
	 *  is world-north on purpose (not relative to the player's facing) - this is a stable ring
	 *  anchored to the world, not a menu that spins to face you; turning your own view is what
	 *  brings different slots into sight, exactly like a compass rose painted on the ground. */
	private static void placeRing(List<TeleportMarker> ring, Vec3 center, List<RadialPlacement> out) {
		int count = ring.size();
		if (count == 0)
			return;
		for (int i = 0; i < count; i++) {
			double angleRad = Math.toRadians((360.0 / count) * i);
			double dx = Math.sin(angleRad) * RADIAL_RADIUS;
			double dz = Math.cos(angleRad) * RADIAL_RADIUS;
			out.add(new RadialPlacement(ring.get(i), center.add(dx, 0, dz)));
		}
	}

	/** Real yaw/pitch bearing from the player's eye to a placement's resolved world position -
	 *  used only for "what's the player currently looking at," via TeleportDirectionUtil's own
	 *  closestToLook. Horizontal distance here is always exactly RADIAL_RADIUS by construction
	 *  (never near zero), so unlike the old real-target bearing this has no near-target
	 *  numerical instability to begin with - smoothedBearing below only has to smooth the
	 *  occasional ring-reflow case (see class doc SLOT ASSIGNMENT), not per-frame jitter. */
	private static TeleportDirectionUtil.Bearing<RadialPlacement> bearingFor(Vec3 eyePos, RadialPlacement placement) {
		double dx = placement.worldPos().x - eyePos.x;
		double dz = placement.worldPos().z - eyePos.z;
		double dy = placement.worldPos().y - eyePos.y;
		double horizontalDist = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dx, dz) * (180.0 / Math.PI));
		float pitch = (float) (Mth.atan2(dy, horizontalDist) * (180.0 / Math.PI));
		return new TeleportDirectionUtil.Bearing<>(placement, yaw, pitch);
	}

	/** Exponentially smooths a bearing toward its previous rendered value, keyed by the
	 *  underlying marker's selectionId - see class doc SLOT ASSIGNMENT. Yaw wraps correctly
	 *  through +/-180 via Mth.wrapDegrees. First time a given selectionId is seen, snaps
	 *  straight to the raw value (nothing to smooth from yet). */
	private static TeleportDirectionUtil.Bearing<RadialPlacement> smoothedBearing(TeleportDirectionUtil.Bearing<RadialPlacement> raw) {
		String key = raw.value().marker().selectionId();
		Float prevYaw = smoothedYaw.get(key);
		Float prevPitch = smoothedPitch.get(key);
		float newYaw;
		float newPitch;
		if (prevYaw == null || prevPitch == null) {
			newYaw = raw.yaw();
			newPitch = raw.pitch();
		} else {
			float yawDelta = Mth.wrapDegrees(raw.yaw() - prevYaw);
			newYaw = prevYaw + yawDelta * SMOOTHING_FACTOR;
			newPitch = prevPitch + (raw.pitch() - prevPitch) * SMOOTHING_FACTOR;
		}
		smoothedYaw.put(key, newYaw);
		smoothedPitch.put(key, newPitch);
		return new TeleportDirectionUtil.Bearing<>(raw.value(), newYaw, newPitch);
	}

	/** Drops smoothing state for any selectionId that's no longer present, so a waypoint that
	 *  gets deleted (or a call that expires) doesn't leak its entry forever. */
	private static void pruneSmoothingState(Set<String> currentKeys) {
		smoothedYaw.keySet().retainAll(currentKeys);
		smoothedPitch.keySet().retainAll(currentKeys);
	}

	private static void renderMarker(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera, Vec3 camPos, RadialPlacement placement, boolean glowing, int glowColor) {
		Vec3 worldPos = placement.worldPos();
		ResourceLocation icon = placement.marker().icon();
		poseStack.pushPose();
		poseStack.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
		poseStack.pushPose();
		poseStack.mulPose(camera.rotation()); // billboard - always face the camera
		if (glowing) {
			poseStack.pushPose();
			poseStack.scale(ICON_SIZE * OUTLINE_SCALE, -ICON_SIZE * OUTLINE_SCALE, ICON_SIZE * OUTLINE_SCALE);
			drawOutlineQuad(poseStack, bufferSource, icon, glowColor);
			poseStack.popPose();
		}
		poseStack.pushPose();
		poseStack.scale(ICON_SIZE, -ICON_SIZE, ICON_SIZE);
		drawIconQuad(poseStack, bufferSource, icon, glowing ? 1.0f : 0.75f);
		poseStack.popPose();
		poseStack.popPose();
		poseStack.popPose();
	}

	/** Draws the marker's label above its icon, using the same SEE_THROUGH text mode vanilla
	 *  uses for glowing entity nameplates - reads through walls just like the icon does. The
	 *  translate-then-rotate order matters: translating BEFORE mulPose(camera.rotation()) moves
	 *  the tooltip straight up in world space (matching how vanilla positions nameplates above
	 *  entities), and only the drawing itself billboards to face the camera. */
	private static void renderTooltip(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera, Vec3 camPos, Font font, RadialPlacement placement, int packedLight) {
		Vec3 worldPos = placement.worldPos();
		String label = placement.marker().label();
		if (label == null || label.isBlank())
			return;
		Minecraft mc = Minecraft.getInstance();
		poseStack.pushPose();
		poseStack.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
		poseStack.translate(0, TOOLTIP_HEIGHT, 0);
		poseStack.mulPose(camera.rotation());
		poseStack.scale(-0.025f, -0.025f, 0.025f);
		var matrix = poseStack.last().pose();
		float halfWidth = -font.width(label) / 2f;
		int backgroundColor = (int) (mc.options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
		font.drawInBatch(label, halfWidth, 0, 0xFFFFFFFF, false, matrix, bufferSource, Font.DisplayMode.SEE_THROUGH, backgroundColor, packedLight);
		poseStack.popPose();
	}

	/** The real icon, full texture color, normal lighting - through iconTypeFor's custom
	 *  no-depth-test NEW_ENTITY render type (see class doc), which still needs the same overlay
	 *  UV alongside color/UV0/light/normal that entityCutoutNoCull's format required. */
	private static void drawIconQuad(PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation icon, float alpha) {
		VertexConsumer buffer = bufferSource.getBuffer(iconTypeFor(icon));
		var matrix = poseStack.last().pose();
		int light = 0xF000F0; // full-bright - a magical destination marker shouldn't dim in the dark
		float half = 0.5f;
		buffer.addVertex(matrix, -half, -half, 0).setColor(1f, 1f, 1f, alpha).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
		buffer.addVertex(matrix, -half, half, 0).setColor(1f, 1f, 1f, alpha).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
		buffer.addVertex(matrix, half, half, 0).setColor(1f, 1f, 1f, alpha).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
		buffer.addVertex(matrix, half, -half, 0).setColor(1f, 1f, 1f, alpha).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
	}

	/** The enlarged silhouette pass - same texture, but through RENDERTYPE_OUTLINE_SHADER, which
	 *  ignores the texture's own RGB and paints its alpha-shaped silhouette in vertexColor
	 *  instead. position_tex_color format only - no overlay/light/normal to set. */
	private static void drawOutlineQuad(PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation icon, int argb) {
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		float a = ((argb >> 24) & 0xFF) / 255f;
		VertexConsumer buffer = bufferSource.getBuffer(outlineTypeFor(icon));
		var matrix = poseStack.last().pose();
		float half = 0.5f;
		buffer.addVertex(matrix, -half, -half, 0).setColor(r, g, b, a).setUv(0, 0);
		buffer.addVertex(matrix, -half, half, 0).setColor(r, g, b, a).setUv(0, 1);
		buffer.addVertex(matrix, half, half, 0).setColor(r, g, b, a).setUv(1, 1);
		buffer.addVertex(matrix, half, -half, 0).setColor(r, g, b, a).setUv(1, 0);
	}
}