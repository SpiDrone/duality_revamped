package net.spidrotech.duality;

import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;

/**
 * Pure procedural geometry generation for a jagged, branching lightning bolt between two points
 * - no Minecraft rendering API involved, just math, so this part is safe to trust independent of
 * any rendering-pipeline uncertainty elsewhere. Uses the standard "midpoint displacement"
 * fractal technique: recursively split each segment in half, nudge the midpoint sideways by a
 * random amount that shrinks each recursion, and occasionally spin off a shorter side-branch.
 *
 * Deterministic given the same seed, so the client only needs to generate this once (cached)
 * rather than regenerating a jittery new shape every frame.
 */
public final class LightningBoltGeometry {
	public record Segment(Vec3 from, Vec3 to, boolean isBranch) {
	}

	private LightningBoltGeometry() {
	}

	public static List<Segment> generate(Vec3 start, Vec3 end, long seed) {
		List<Segment> segments = new ArrayList<>();
		Random random = new Random(seed);
		double totalLength = start.distanceTo(end);
		double initialJitter = Math.max(0.15, totalLength * 0.08);
		subdivide(start, end, 5, initialJitter, random, segments, false);
		return segments;
	}

	private static void subdivide(Vec3 from, Vec3 to, int recursionsLeft, double jitter, Random random, List<Segment> out, boolean isBranch) {
		if (recursionsLeft <= 0 || from.distanceTo(to) < 0.3) {
			out.add(new Segment(from, to, isBranch));
			return;
		}
		Vec3 mid = from.add(to).scale(0.5);
		Vec3 axis = to.subtract(from).normalize();
		Vec3 perp = perpendicular(axis, random);
		Vec3 displaced = mid.add(perp.scale((random.nextDouble() - 0.5) * 2.0 * jitter));
		subdivide(from, displaced, recursionsLeft - 1, jitter * 0.55, random, out, isBranch);
		subdivide(displaced, to, recursionsLeft - 1, jitter * 0.55, random, out, isBranch);
		// Occasionally spin off a shorter branch from this midpoint - only from the main bolt,
		// not from other branches, to keep the shape readable rather than an overwhelming tangle.
		if (!isBranch && random.nextFloat() < 0.35f && recursionsLeft >= 2) {
			Vec3 branchDir = axis.scale(0.6 + random.nextDouble() * 0.4).add(perp.scale((random.nextDouble() - 0.5) * 1.5)).normalize();
			double branchLength = from.distanceTo(to) * (0.3 + random.nextDouble() * 0.3);
			Vec3 branchEnd = displaced.add(branchDir.scale(branchLength));
			subdivide(displaced, branchEnd, recursionsLeft - 2, jitter * 0.5, random, out, true);
		}
	}

	private static Vec3 perpendicular(Vec3 axis, Random random) {
		Vec3 arbitrary = Math.abs(axis.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 base = axis.cross(arbitrary).normalize();
		// Rotate the perpendicular direction randomly around the axis, so displacement isn't
		// always in the same plane (which would make every bolt look flat/2D).
		double angle = random.nextDouble() * Math.PI * 2;
		Vec3 base2 = axis.cross(base).normalize();
		return base.scale(Math.cos(angle)).add(base2.scale(Math.sin(angle)));
	}
}