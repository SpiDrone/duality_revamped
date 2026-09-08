package net.spidrotech.duality;

import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleOptions;

import java.util.List;
import java.util.ArrayList;

/**
 * Collects (particle, pattern) pairs and spawns all of them each tick a projectile is in
 * flight. Build one, attach it to a ProjectileDefinition via Builder#particleEmitter, and don't
 * mutate it further after that (it's shared across every entity using that definition).
 *
 * Example - a fireball with both a corkscrewing flame trail and a smoke ring:
 *   ParticleEmitter emitter = new ParticleEmitter()
 *       .add(ParticleTypes.FLAME, ParticlePattern.spiral(0.3, 2.0))
 *       .add(ParticleTypes.SMOKE, ParticlePattern.cylinder(0.5, 6));
 */
public final class ParticleEmitter {
	private final List<Entry> entries = new ArrayList<>();

	public ParticleEmitter add(ParticleOptions particle, ParticlePattern pattern) {
		entries.add(new Entry(particle, pattern));
		return this;
	}

	void spawn(ServerLevel level, Vec3 origin, Vec3 forward, int ticksAlive) {
		for (Entry entry : entries) {
			for (Vec3 offset : entry.pattern.offsets(ticksAlive, forward)) {
				Vec3 pos = origin.add(offset);
				level.sendParticles(entry.particle, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
			}
		}
	}

	private record Entry(ParticleOptions particle, ParticlePattern pattern) {
	}
}