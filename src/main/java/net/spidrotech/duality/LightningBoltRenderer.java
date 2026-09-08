package net.spidrotech.duality;

import org.joml.Matrix4f;

import org.checkerframework.checker.units.qual.g;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

/**
 * Procedural lightning bolt rendering - no model, no texture, just directly-drawn geometry.
 *
 * Uses a CUSTOM render type instead of vanilla's RenderType.lightning(), for one specific
 * reason: vanilla's version writes to the depth buffer (WriteMaskState.COLOR_DEPTH_WRITE), which
 * is fine for a single bolt but actively causes z-fighting here, since this renderer draws a lot
 * of overlapping/coincident geometry in the same pass - the glow-halo and inner-core quads sit
 * exactly coplanar (same normal direction, just different width), and consecutive bolt segments
 * share endpoints. When depth gets WRITTEN (not just tested), those overlapping pieces compete
 * for the same depth values and flicker. DUALITY_LIGHTNING below is identical to vanilla's
 * lightning render type except it writes color only, leaving depth TESTING (so bolts still
 * correctly hide behind walls) but never WRITING (so bolt geometry can never fight itself).
 *
 * Also switched backface culling off (NO_CULL) instead of manually drawing every quad twice
 * (once forward-wound, once reverse-wound) to fake double-sided rendering - that old approach
 * drew two literally coincident triangles per quad, which was itself a source of the same
 * problem. NO_CULL makes a single quad visible from both sides directly.
 */
@OnlyIn(Dist.CLIENT)
public class LightningBoltRenderer extends EntityRenderer<LightningVisualBase> {
	// Cached per-entity so the jagged shape is generated once and stays stable across frames
	// instead of jittering every frame.
	private static final Map<Integer, List<LightningBoltGeometry.Segment>> GEOMETRY_CACHE = new ConcurrentHashMap<>();
	// Same shader + additive blend as vanilla's RenderType.lightning(), but COLOR_WRITE only (no
	// depth write) and NO_CULL instead of vanilla's default culling - see class doc for why.
	private static final RenderType DUALITY_LIGHTNING = RenderType.create("duality_lightning_bolt", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, true, RenderType.CompositeState.builder()
			.setShaderState(RenderStateShard.POSITION_COLOR_SHADER).setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY).setWriteMaskState(RenderStateShard.COLOR_WRITE).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));

	public LightningBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(LightningVisualBase entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		List<LightningBoltGeometry.Segment> segments = GEOMETRY_CACHE.computeIfAbsent(entity.getId(), id -> LightningBoltGeometry.generate(entity.position(), entity.endPoint(), entity.seed()));
		float alpha = entity.lifeFraction(partialTick);
		if (alpha <= 0f)
			return;
		LightningColorScheme colors = entity.colors();
		Vec3 origin = entity.position();
		poseStack.pushPose();
		Matrix4f matrix = poseStack.last().pose();
		VertexConsumer buffer = bufferSource.getBuffer(DUALITY_LIGHTNING);
		for (LightningBoltGeometry.Segment segment : segments) {
			Vec3 from = segment.from().subtract(origin);
			Vec3 to = segment.to().subtract(origin);
			float width = segment.isBranch() ? 0.03f : 0.07f;
			// Two passes per segment: a wide, faint glow halo underneath, then a thin bright
			// core on top - the combination is what should read as "glowing" via additive blend.
			// These two passes are exactly coplanar (same normal direction, just wider) - that's
			// fine now that DUALITY_LIGHTNING doesn't write depth, but would have been a z-fight
			// risk under vanilla's depth-writing lightning render type.
			drawSegment(matrix, buffer, from, to, width * 3.5f, colors.glowColor(), alpha * 0.35f);
			drawSegment(matrix, buffer, from, to, width, colors.innerColor(), alpha);
		}
		poseStack.popPose();
		super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
	}

	/**
	 * Draws one segment as a 3D '+' shape (two intersecting quads) - ONE quad per plane now,
	 * not two. NO_CULL on DUALITY_LIGHTNING makes each quad visible from both sides without
	 * needing a second, literally coincident copy wound the other direction.
	 */
	private void drawSegment(Matrix4f matrix, VertexConsumer buffer, Vec3 from, Vec3 to, float width, int color, float alpha) {
		Vec3 dir = to.subtract(from);
		if (dir.lengthSqr() < 1.0E-6)
			return;
		// Create two orthogonal normals to build a 3D '+' shape
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 normal1 = dir.cross(up);
		if (normal1.lengthSqr() < 1.0E-6) {
			normal1 = dir.cross(new Vec3(1, 0, 0));
		}
		normal1 = normal1.normalize().scale(width / 2.0);
		Vec3 normal2 = dir.cross(normal1).normalize().scale(width / 2.0);
		float r = ((color >> 16) & 0xFF) / 255f;
		float g = ((color >> 8) & 0xFF) / 255f;
		float b = (color & 0xFF) / 255f;
		drawQuad(matrix, buffer, from, to, normal1, r, g, b, alpha);
		drawQuad(matrix, buffer, from, to, normal2, r, g, b, alpha);
	}

	private void drawQuad(Matrix4f matrix, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 normal, float r, float g, float b, float alpha) {
		Vec3 p1 = from.add(normal);
		Vec3 p2 = from.subtract(normal);
		Vec3 p3 = to.subtract(normal);
		Vec3 p4 = to.add(normal);
		buffer.addVertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, alpha);
		buffer.addVertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, alpha);
		buffer.addVertex(matrix, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, alpha);
		buffer.addVertex(matrix, (float) p4.x, (float) p4.y, (float) p4.z).setColor(r, g, b, alpha);
	}

	@Override
	public ResourceLocation getTextureLocation(LightningVisualBase entity) {
		return MissingTextureAtlasSprite.getLocation();
	}
}