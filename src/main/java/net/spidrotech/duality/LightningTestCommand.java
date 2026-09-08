/**package net.spidrotech.duality;

import org.slf4j.Logger;

import net.spidrotech.duality.init.DualityModEntities;
import net.spidrotech.duality.entity.LightningVisualEntity;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import com.mojang.logging.LogUtils;
@EventBusSubscriber(modid = "duality")
public final class LightningTestCommand {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "lightning_strike");
	private LightningTestCommand() {
	}
	// This nested class explicitly registers to the MOD bus for the setup event
	@EventBusSubscriber(modid = "duality", bus = EventBusSubscriber.Bus.MOD)
	public static class ModBusEvents {
		@SubscribeEvent
		public static void commonSetup(FMLCommonSetupEvent event) {
			event.enqueueWork(() -> {
				AbilityManager.get().register(LightningStrikeAbility.builder(ID).range(20).maxJumps(4).maxStems(10).damage(6).cooldownTicks(0) // 1s - short on purpose, for fast test iteration
						.damageType("magic", "lightning").colorLighting(0xFFFFFFFF, 0xFF99CCFF, 0xFF3388FF).boltDurationTicks(15)
						// TODO: replace YourLightningEntity with whatever you name the entity you
						// create in MCreator (extending LightningVisualBase)
						.spawner(level -> new LightningVisualEntity(DualityModEntities.LIGHTNING_VISUAL.get(), level)).build());
				LOGGER.info("[duality] Registered lightning_strike test ability");
			});
		}
	}
	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("lightning").executes(context -> cast(context.getSource())));
	}
	private static int cast(CommandSourceStack source) {
		if (!(source.getEntity() instanceof LivingEntity caster)) {
			LOGGER.warn("/lightning was run by a non-living entity or console, ignoring");
			return 0;
		}
		
		AbilityManager.ActivationResult result = AbilityManager.get().tryActivate(caster, ID);
		
		if (!result.succeeded()) {
			LOGGER.info("/lightning failed for {}: {}", caster.getName().getString(), result.message());
			return 0;
		}
		return 1;
	}
}*/