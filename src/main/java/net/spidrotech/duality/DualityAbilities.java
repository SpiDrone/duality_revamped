package net.spidrotech.duality;

import org.slf4j.Logger;

import net.spidrotech.duality.init.DualityModEntities;
import net.spidrotech.duality.entity.LightningVisualEntity;
import net.spidrotech.duality.abilities.teleportation.OrbAbility;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;

import com.mojang.logging.LogUtils;

/**
 * Permanent home for registering every hand-authored Ability subclass - the counterpart to
 * DualityProjectiles, but for powers that need real per-cast state machinery beyond what a
 * ProjectileDefinition + SimpleProjectileAbility can express (see Ability's own class doc).
 * OrbAbility and LightningStrikeAbility are both this kind of power - LightningStrikeAbility
 * happens to spawn projectile-shaped visual entities under the hood, but it's driven by its own
 * chain-targeting logic, not ProjectileRegistry, so it doesn't belong in DualityProjectiles.
 *
 * Test/casting commands should reference abilities registered here by id, not register their
 * own copies - same separation of concerns as DualityProjectiles/FireballTestCommand.
 *
 * OrbAbility is registered here now - previously OrbTestCommand did this in its own
 * commonSetup, but that command has been removed (it existed purely to prove the concept),
 * so this is the one and only place OrbAbility gets registered.
 */
@EventBusSubscriber(modid = "duality")
public final class DualityAbilities {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final ResourceLocation LIGHTNING_STRIKE = ResourceLocation.fromNamespaceAndPath("duality", "lightning_hands");
	public static final ResourceLocation D_LIGHTNING_STRIKE = ResourceLocation.fromNamespaceAndPath("duality", "demonic_lightning_hands");

	private DualityAbilities() {
	}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(DualityAbilities::registerAll);
	}

	private static void registerAll() {
		elemental();
		angelic();
		// demonic();
		// wiccan();
	}

	// ================================================================== elemental
	// TODO: reorganize into whichever magic-school section actually fits your design once you
	// decide where lightning belongs - this is just a placeholder grouping.
	private static void elemental() {
		AbilityManager.get().register(LightningStrikeAbility.builder(LIGHTNING_STRIKE).range(20).maxJumps(2).maxStems(4).damage(6).cooldownTicks(5) //
				.damageType("magic", "lightning").colorLighting(0xFFFFFFFF, 0xFF99CCFF, 0xFF3388FF).boltDurationTicks(15).spawner(level -> new LightningVisualEntity(DualityModEntities.LIGHTNING_VISUAL.get(), level)).build());
		AbilityManager.get().register(LightningStrikeAbility.builder(D_LIGHTNING_STRIKE).range(20).maxJumps(3).maxStems(5).damage(6).cooldownTicks(5)//
				.damageType("magic", "lightning").colorLighting(0xFFFFD1D1, 0xFF8B0000, 0xFF4A0000).boltDurationTicks(15).spawner(level -> new LightningVisualEntity(DualityModEntities.LIGHTNING_VISUAL.get(), level)).build());
	}

	// ================================================================== angelic
	private static void angelic() {
		AbilityManager.get().register(new OrbAbility());
	}
}