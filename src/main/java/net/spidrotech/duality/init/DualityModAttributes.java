/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.spidrotech.duality.init;

import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;

@EventBusSubscriber
public class DualityModAttributes {
	public static final DeferredRegister<Attribute> REGISTRY = DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, DualityMod.MODID);
	public static final DeferredHolder<Attribute, Attribute> ORBCHARGEREDUCTION = REGISTRY.register("orbchargereduction", () -> new RangedAttribute("attribute.duality.orbchargereduction", 0, 0, 1).setSyncable(true));
	public static final DeferredHolder<Attribute, Attribute> ORBING_PROFICIENCY = REGISTRY.register("orbing_proficiency", () -> new RangedAttribute("attribute.duality.orbing_proficiency", 1, 1, 10).setSyncable(true));
	public static final DeferredHolder<Attribute, Attribute> BLOOD_MAX = REGISTRY.register("blood_max", () -> new RangedAttribute("attribute.duality.blood_max", 0, 0, 2).setSyncable(true));
	public static final DeferredHolder<Attribute, Attribute> BLOOD_QUALITY = REGISTRY.register("blood_quality", () -> new RangedAttribute("attribute.duality.blood_quality", 25, -100, 100).setSyncable(true));
	public static final DeferredHolder<Attribute, Attribute> VAMPIRE_RANK = REGISTRY.register("vampire_rank", () -> new RangedAttribute("attribute.duality.vampire_rank", 0, 0, 4).setSyncable(true));

	@SubscribeEvent
	public static void addAttributes(EntityAttributeModificationEvent event) {
		event.add(EntityType.PLAYER, ORBCHARGEREDUCTION);
		event.add(EntityType.PLAYER, ORBING_PROFICIENCY);
		event.add(EntityType.PLAYER, BLOOD_MAX);
		event.getTypes().forEach(entity -> event.add(entity, BLOOD_QUALITY));
		event.add(EntityType.PLAYER, VAMPIRE_RANK);
	}
}