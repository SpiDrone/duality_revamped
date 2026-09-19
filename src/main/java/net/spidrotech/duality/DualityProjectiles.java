package net.spidrotech.duality;

import org.joml.Vector3f;

import net.spidrotech.duality.init.DualityModEntities;
import net.spidrotech.duality.entity.AbilityProjectileEntity;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// TODO: fix to match your actual generated field name
/**
 * Permanent home for registering every real projectile-based power. This is the file that grows
 * as you add the other ~299 - "one more projectile power" should mean "one more method call
 * here," per the whole point of building this system.
 *
 * Test/casting commands (FireballTestCommand, etc) should reference projectiles registered here
 * by id, not register their own copies - keeps registration and triggering as separate concerns.
 *
 * To add a new projectile: copy one of the register()/registerVariant() calls below into the
 * right magic-school section (or add a new section) and change what you need.
 */
@EventBusSubscriber(modid = "duality")
public final class DualityProjectiles {
	private DualityProjectiles() {
	}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(DualityProjectiles::registerAll);
	}

	private static void registerAll() {
		demonic();
		// wiccan();
		// angelic();
	}

	// ================================================================== demonic
	private static void demonic() {
		//==============================================FIREBALL==============================================\\
		ParticleEmitter fireballEmitter = new ParticleEmitter().add(ParticleTypes.FLAME, ParticlePattern.spiral(0.25, 3.0));
		ParticleEmitter greaterFireballEmitter = new ParticleEmitter().add(ParticleTypes.FLAME, ParticlePattern.cylinder(0.4, 8)).add(ParticleTypes.SMOKE, ParticlePattern.spiral(0.35, 1.5));
		ParticleEmitter infernoEmitter = new ParticleEmitter().add(ParticleTypes.FLAME, ParticlePattern.cylinder(1.2, 12)).add(ParticleTypes.LARGE_SMOKE, ParticlePattern.spiral(1.0, 1.0));
		// A wider ring of flame the projectile flies through the middle of, plus its own
		// (bigger, slower) spiral of smoke - busier and more dramatic, matching "greater."
		ProjectileRegistry.register("firebolt", b -> b.speed(1.2).lifetimeTicks(100) // 5s max flight before despawning
				.magicSchool("demonic").cooldownTicks(20).projectileScale(3.0f, 3.0f)// 1s
				.param("firePower", 1.0).spawner(level -> new AbilityProjectileEntity(DualityModEntities.ABILITY_PROJECTILE.get(), level))//
				.onEntityHit(ProjectileEffects.scaledFireDamage(3.0, 4, "firePower"))//
				.model("fireball")//
				.addEntityHitEffect(ProjectileEffects.impactParticles(ParticleTypes.FLAME, 8, 0.25)).addEntityHitEffect(ProjectileEffects.impactParticles(ParticleTypes.SMOKE, 5, 0.2))
				.onBlockHit(ProjectileEffects.impactParticlesOnBlock(ParticleTypes.FLAME, 6, 0.2)).areaOfEffect(0.3, ProjectileEffects.scaledFireDamage(3.0, 4, "firePower")) // near misses still take damage
				.particleEmitter(fireballEmitter));
		// Only what differs from the base gets touched - everything else (lifetime, cooldown,
		// magic school, the hit effect, the spawner/entity type) is inherited automatically.
		// particleEmitter IS touched here on purpose, to swap in a visibly different pattern.
		// The entity-hit particles stack too: this ADDS a bigger burst on top of fireball's
		// already-inherited small one (both fire on the same hitPos in the same tick), so the
		// combined result naturally reads as denser/bigger than fireball alone without needing
		// to re-declare the damage effect it also inherits.
		// Web spit's trail is a thread, not a puff: strand() lays an unbroken line of fine silk back
		// along each tick's travel, so it reads as one strand paid out behind the glob, and every
		// third tick drops a clump of cobweb shreds onto it so it looks tacky rather than clean.
		DustParticleOptions silkThread = new DustParticleOptions(new Vector3f(0.94f, 0.94f, 0.9f), 0.45f);
		ItemParticleOption webClump = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB));
		ParticleEmitter webStrandEmitter = new ParticleEmitter().add(silkThread, ParticlePattern.strand(6)).add(webClump, ParticlePattern.everyNTicks(3, ParticlePattern.center()));
		ProjectileRegistry.register("web_spit", b -> b.speed(1).lifetimeTicks(100) // 5s max flight before despawning
				.magicSchool("demonic").cooldownTicks(20).projectileScale(3.0f, 3.0f)// 1s
				.spawner(level -> new AbilityProjectileEntity(DualityModEntities.ABILITY_PROJECTILE.get(), level))//
				.onEntityHit(ProjectileEffects.slowness(60, 2)) // 3s of Slowness III (-45% speed)
				.model("web_spit")//
				.addEntityHitEffect(ProjectileEffects.impactParticles(webClump, 12, 0.3)).addEntityHitEffect(ProjectileEffects.impactParticles(silkThread, 10, 0.25))
				.onBlockHit(ProjectileEffects.impactParticlesOnBlock(webClump, 8, 0.25)).areaOfEffect(0.3, ProjectileEffects.slowness(60, 2)) // near misses still get stuck
				.particleEmitter(webStrandEmitter));
		// The "large fireball, small hitbox, hits a crowd" use case: looks huge (visualScale),
		// hits almost nothing directly (hitbox stays at the tiny base size), noClip + high
		// pierce mean it basically never stops from blocks or entities, and a generous AoE
		// radius does the actual work of damaging everyone it sweeps past. Short lifetime since
		// it's meant to blanket an area, not travel far.
		ProjectileRegistry.registerVariant("fireball_normal", "firebolt", b -> b.param("firePower", 2.0).speed(1.5).projectileScale(3.0f, 3.0f) // 0.2 base * 3.0 = 0.6 width/height
				.particleEmitter(greaterFireballEmitter).texture("duality:textures/entities/test22.png").addEntityHitEffect(ProjectileEffects.impactParticles(ParticleTypes.FLAME, 10, 0.35))
				.addEntityHitEffect(ProjectileEffects.impactParticles(ParticleTypes.LARGE_SMOKE, 8, 0.3)));
		ProjectileRegistry.register("fireball_greater", b -> b.speed(0.8).lifetimeTicks(60) // 3s - a short blanketing sweep, not a long-range shot
				.magicSchool("demonic").cooldownTicks(100) // 5s - a big, costly ability
				.param("firePower", 1.0).projectileScale(30.0f, 30.0f) // hitbox stays at the base 0.2 size
				.visualScale(6.0f) // but looks huge
				.noClip(true) // flies straight through walls/terrain
				.pierce(100) // and through as many entities as it directly grazes
				.model("fireball") // reuses the fireball shape at 6x visual scale - see earlier fix
				// Direct hits are rare (noClip + high pierce mean it usually just sweeps past),
				// so this is the one case where a real EXPLOSION particle earns its keep -
				// reserved for inferno alone so fireball/greater_fireball don't dilute the punch.
				.onEntityHit(ProjectileEffects.scaledFireDamage(4.0, 5, "firePower").andThen(ProjectileEffects.impactParticles(ParticleTypes.FLAME, 20, 0.5)).andThen(ProjectileEffects.impactParticles(ParticleTypes.LARGE_SMOKE, 14, 0.45))
						.andThen(ProjectileEffects.impactParticles(ParticleTypes.EXPLOSION, 3, 0.3))) // a rare exact hit still hits harder
				// AoE/near-miss hits are the common case for inferno (that's the whole point of
				// the ability) and fire once per entity swept, potentially many entities at
				// once - kept deliberately smaller so blanketing a crowd doesn't turn into a
				// wall of particles.
				.areaOfEffect(2.0, ProjectileEffects.scaledFireDamage(2.0, 3, "firePower")) // the real damage source
				.addAreaHitEffect(ProjectileEffects.impactParticles(ParticleTypes.FLAME, 8, 0.3))
				// noClip means block hits basically never happen for inferno today, but this
				// costs nothing and keeps the definition consistent if that ever changes.
				.onBlockHit(ProjectileEffects.impactParticlesOnBlock(ParticleTypes.FLAME, 10, 0.35)).spawner(level -> new AbilityProjectileEntity(DualityModEntities.ABILITY_PROJECTILE.get(), level)).particleEmitter(infernoEmitter));
		//==============================================ACID==============================================\\
		DustParticleOptions acidDust = new DustParticleOptions(new Vector3f(0.6f, 1.0f, 0.05f), 1.1f);
		ParticleEmitter acidSpitEmitter = new ParticleEmitter().add(acidDust, ParticlePattern.spiral(0.08, 5.0)).add(ParticleTypes.ITEM_SLIME, ParticlePattern.center());
		ProjectileRegistry.register("acid_spit", b -> b.speed(1.3).lifetimeTicks(60) // 3s max flight
				.magicSchool("demonic").cooldownTicks(40) // 2s between spits
				.gravityStrength(0.03) // gentle arc - a lobbed "spit" reads better than a laser-straight shot
				.model("acid_spit") // TODO: your Blockbench model key once it's registered in ProjectileVisualRegistry
				.spawner(level -> new AbilityProjectileEntity(DualityModEntities.ABILITY_PROJECTILE.get(), level)).particleEmitter(acidSpitEmitter).addEntityHitEffect(ProjectileEffects.damage(2.5)).addEntityHitEffect(ProjectileEffects.acidic(100, 1)) // 5s of Poison II
				.addEntityHitEffect(ProjectileEffects.impactParticles(acidDust, 14, 0.35)).addEntityHitEffect(ProjectileEffects.impactParticles(ParticleTypes.ITEM_SLIME, 6, 0.3))
				.onBlockHit(ProjectileEffects.impactParticlesOnBlock(acidDust, 12, 0.3)));
	}
	// ================================================================== wiccan
	// private static void wiccan() { ... }
	// ================================================================== angelic
	// private static void angelic() { ... }
}