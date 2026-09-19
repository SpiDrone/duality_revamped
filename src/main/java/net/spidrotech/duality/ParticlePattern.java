package net.spidrotech.duality;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.ArrayList;

/**
 * Where to place particles around a moving projectile, for one tick. Offsets are relative to
 * the projectile's current position, in the frame of its direction of travel - so patterns stay
 * correctly oriented no matter which way the projectile is flying.
 *
 * Two built-in patterns (spiral, cylinder) plus full support for writing your own as a plain
 * lambda, e.g.:
 *   ParticlePattern zigzag = (ticksAlive, forward) -> List.of(new Vec3(0, Math.sin(ticksAlive * 0.5) * 0.3, 0));
 */
@FunctionalInterface
public interface ParticlePattern {
	/** ticksAlive drives animation (rotation, oscillation, whatever); forward is the
	 *  projectile's current direction of travel, for orienting the pattern correctly. */
	List<Vec3> offsets(int ticksAlive, Vec3 forward);

	/** A single point corkscrewing around the direction of travel - a classic magic-trail look. */
	static ParticlePattern spiral(double radius, double revolutionsPerSecond) {
		return (ticksAlive, forward) -> {
			Vec3 axis = forward.lengthSqr() > 1.0E-6 ? forward.normalize() : new Vec3(0, 0, 1);
			Vec3 perp1 = perpendicular(axis);
			Vec3 perp2 = axis.cross(perp1);
			double angle = ticksAlive * (2 * Math.PI * revolutionsPerSecond / 20.0);
			Vec3 offset = perp1.scale(Math.cos(angle) * radius).add(perp2.scale(Math.sin(angle) * radius));
			return List.of(offset);
		};
	}

	/** Several points evenly spaced around a ring perpendicular to the direction of travel - a
	 *  "hoop" of particles the projectile flies through the middle of. */
	static ParticlePattern cylinder(double radius, int pointCount) {
		return (ticksAlive, forward) -> {
			Vec3 axis = forward.lengthSqr() > 1.0E-6 ? forward.normalize() : new Vec3(0, 0, 1);
			Vec3 perp1 = perpendicular(axis);
			Vec3 perp2 = axis.cross(perp1);
			List<Vec3> points = new ArrayList<>(pointCount);
			for (int i = 0; i < pointCount; i++) {
				double angle = (2 * Math.PI * i) / pointCount;
				points.add(perp1.scale(Math.cos(angle) * radius).add(perp2.scale(Math.sin(angle) * radius)));
			}
			return points;
		};
	}

	/** A single point sitting dead center, no offset - the simplest possible pattern, useful as
	 *  a base or for a plain trailing-smoke look. */
	static ParticlePattern center() {
		return (ticksAlive, forward) -> List.of(Vec3.ZERO);
	}

	/**
	 * An unbroken line paid out behind the projectile, like a thread of spit web.
	 *
	 * <p>Every other pattern drops one cluster per tick, which at speed leaves a dotted line with
	 * gaps the length of a tick's travel. This fills the gap instead: forward is the projectile's
	 * per-tick velocity, so points spaced from here back along -forward cover exactly the distance
	 * it moved since the last emission, and each tick's run of points meets the previous one.
	 */
	static ParticlePattern strand(int pointsPerTick) {
		return (ticksAlive, forward) -> {
			List<Vec3> points = new ArrayList<>(pointsPerTick);
			for (int i = 0; i < pointsPerTick; i++)
				points.add(forward.scale(-(double) i / pointsPerTick));
			return points;
		};
	}

	/** Runs {@code pattern} only on every nth tick of flight - for sparse details like drips and
	 *  clumps that would turn into a solid smear if emitted every tick. */
	static ParticlePattern everyNTicks(int n, ParticlePattern pattern) {
		return (ticksAlive, forward) -> ticksAlive % n == 0 ? pattern.offsets(ticksAlive, forward) : List.of();
	}

	private static Vec3 perpendicular(Vec3 axis) {
		Vec3 arbitrary = Math.abs(axis.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		return axis.cross(arbitrary).normalize();
	}
}