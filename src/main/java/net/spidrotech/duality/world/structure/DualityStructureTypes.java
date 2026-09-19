package net.spidrotech.duality.world.structure;

import net.spidrotech.duality.DualityMod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Hand-written (not MCreator-generated) so it survives regeneration of init/.
 *
 * <p>Registers through RegisterEvent rather than a DeferredRegister: a DeferredRegister needs the
 * mod event bus, which is only reachable from the MCreator-generated mod constructor. Registering
 * from a mod-bus event keeps this self-contained.
 *
 * <p>This registration is load-bearing, not optional. {@code demon_stronghold.json} declares
 * {@code "type": "duality:cavern_jigsaw"}, and if that type is missing the JSON fails to parse,
 * which leaves an unbound value in the structure registry - and an unbound value stops the whole
 * registry freezing, so the world will not open at all.
 */
public class DualityStructureTypes {
	public static final ResourceLocation CAVERN_JIGSAW_ID = ResourceLocation.fromNamespaceAndPath(DualityMod.MODID, "cavern_jigsaw");

	public static final StructureType<CavernJigsawStructure> CAVERN_JIGSAW = () -> CavernJigsawStructure.CODEC;

	@EventBusSubscriber(modid = "duality")
	public static final class Registration {
		private Registration() {
		}

		@SubscribeEvent
		public static void onRegister(RegisterEvent event) {
			event.register(Registries.STRUCTURE_TYPE, CAVERN_JIGSAW_ID, () -> CAVERN_JIGSAW);
		}
	}
}
