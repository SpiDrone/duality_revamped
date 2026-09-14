package net.spidrotech.duality.world.structure;

import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** Hand-written (not MCreator-generated) so it survives regeneration of init/. */
public class DualityStructureTypes {
	public static final DeferredRegister<StructureType<?>> REGISTRY = DeferredRegister.create(Registries.STRUCTURE_TYPE, DualityMod.MODID);

	public static final DeferredHolder<StructureType<?>, StructureType<CavernJigsawStructure>> CAVERN_JIGSAW = REGISTRY.register("cavern_jigsaw",
			() -> () -> CavernJigsawStructure.CODEC);
}
