package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.village.entity.DualityNpcEntity;
import net.spidrotech.duality.init.DualityModAttributes;

import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Map;

/**
 * Gives every creature its blood quality, once, the first time it's seen - replacing
 * EntitySpawnedProcedure's roll, which never ran: it waits for the attribute to read 0, but the
 * attribute defaults to 25, so every creature kept that 25 forever.
 *
 * <p>Whether a creature has rolled yet is a flag on the entity ({@value #ROLLED}), not a magic
 * value, so the roll survives saves and happens exactly once. Creatures that already exist roll the
 * next time their chunk loads.
 *
 * <p>The tiers come from the MCreator entity tags, strongest first: {@code duality:blood_strong}
 * (35-50), {@code duality:blood_medium} (15-35), {@code duality:blood_weak} (1-15). Anything in
 * none of them falls back to {@link #DEFAULT_TIERS} by entity id, then to a village NPC's
 * medium, then to weak - so a cow reads as thin blood rather than a person's. {@code
 * duality:blood_toxic} turns the result negative (tainted).
 *
 * <p>Players aren't rolled: a character's blood grows with Endurance instead (see CharacterAttributes).
 */
@EventBusSubscriber(modid = "duality")
public final class BloodQuality {
	private static final String ROLLED = "duality_blood_quality_rolled";
	private enum Tier {
		WEAK(1, 15), MEDIUM(15, 35), STRONG(35, 50);

		final int min, max;

		Tier(int min, int max) {
			this.min = min;
			this.max = max;
		}

		int roll(RandomSource random) {
			return min + random.nextInt(max - min + 1);
		}
	}

	private static final TagKey<EntityType<?>> STRONG = tag("blood_strong"), MEDIUM = tag("blood_medium"), WEAK = tag("blood_weak"), TOXIC = tag("blood_toxic");
	/** For creatures none of the tags mention. */
	private static final Map<String, Tier> DEFAULT_TIERS = Map.of("duality:spider_queen", Tier.STRONG, "duality:demon_spider", Tier.MEDIUM, "minecraft:evoker", Tier.STRONG,
			"minecraft:witch", Tier.MEDIUM, "minecraft:villager", Tier.MEDIUM, "minecraft:wandering_trader", Tier.MEDIUM, "minecraft:pillager", Tier.MEDIUM,
			"minecraft:vindicator", Tier.MEDIUM, "minecraft:piglin_brute", Tier.STRONG, "minecraft:ravager", Tier.STRONG);

	private BloodQuality() {
	}

	private static TagKey<EntityType<?>> tag(String path) {
		return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("duality", path));
	}

	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living) || living instanceof Player)
			return;
		if (living.getPersistentData().getBoolean(ROLLED))
			return;
		AttributeInstance quality = living.getAttribute(DualityModAttributes.BLOOD_QUALITY);
		if (quality == null)
			return;
		EntityType<?> type = living.getType();
		double rolled = tierOf(living).roll(living.getRandom());
		if (type.is(TOXIC))
			rolled = -rolled;
		quality.setBaseValue(rolled);
		living.getPersistentData().putBoolean(ROLLED, true);
	}

	private static Tier tierOf(LivingEntity living) {
		EntityType<?> type = living.getType();
		if (type.is(STRONG))
			return Tier.STRONG;
		if (type.is(MEDIUM))
			return Tier.MEDIUM;
		if (type.is(WEAK))
			return Tier.WEAK;
		Tier known = DEFAULT_TIERS.get(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
		if (known != null)
			return known;
		return living instanceof DualityNpcEntity ? Tier.MEDIUM : Tier.WEAK;
	}
}
