package net.spidrotech.duality.procedures;

import net.spidrotech.duality.init.DualityModParticleTypes;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.SimpleParticleType;

public class ParticlesOrbingProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z) {
		double particleRadius = 0;
		double particleAmount = 0;
		particleAmount = 8;
		particleRadius = 4;
		for (int index0 = 0; index0 < (int) particleAmount; index0++) {
			world.addParticle((SimpleParticleType) (DualityModParticleTypes.ORBS.get()), (x + 0 + Mth.nextDouble(RandomSource.create(), -0.1, 0.1) * particleRadius), (y + 0 + Mth.nextDouble(RandomSource.create(), 0, 1) * 2),
					(z + 0 + Mth.nextDouble(RandomSource.create(), -0.1, 0.1) * particleRadius), 0, 0, 0);
		}
	}
}