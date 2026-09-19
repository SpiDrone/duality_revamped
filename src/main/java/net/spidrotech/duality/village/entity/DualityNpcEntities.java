package net.spidrotech.duality.village.entity;

import net.spidrotech.duality.DualityMod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registration for the village NPC entities.
 *
 * <p>Registers through RegisterEvent rather than a DeferredRegister on purpose. A DeferredRegister
 * has to be handed the mod event bus from the mod constructor, and the mod constructor lives in
 * DualityMod, which MCreator regenerates - so that route can only be wired by editing a generated
 * file. RegisterEvent is a mod-bus event, so an @EventBusSubscriber class registers itself and
 * nothing generated has to know this exists.
 *
 * <p>The registry name matters: {@code duality:npc} is the id VillageNpcTypes falls back to, so
 * registering it here is what switches village records over from vanilla villagers. Variants later
 * go in the shape VillageNpcTypes already looks for - {@code npc_<species>}, {@code npc_<job>},
 * {@code npc_<species>_<job>} - and nothing else has to change.
 */
@EventBusSubscriber(modid = "duality")
public final class DualityNpcEntities {
	public static final ResourceLocation NPC_ID = ResourceLocation.fromNamespaceAndPath(DualityMod.MODID, "npc");

	/**
	 * A handle resolved by id, not the type itself: building an EntityType outside registration
	 * throws "Registry is already frozen", so the type is only built inside onRegister.
	 */
	public static final DeferredHolder<EntityType<?>, EntityType<DualityNpcEntity>> NPC = DeferredHolder.create(Registries.ENTITY_TYPE, NPC_ID);

	private DualityNpcEntities() {
	}

	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
		event.register(Registries.ENTITY_TYPE, NPC_ID,
				() -> EntityType.Builder.of(DualityNpcEntity::new, MobCategory.CREATURE).sized(0.6f, 1.95f).clientTrackingRange(10).build("npc"));
	}

	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(NPC.get(), DualityNpcEntity.createAttributes().build());
	}
}
