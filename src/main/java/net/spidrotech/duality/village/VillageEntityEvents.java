package net.spidrotech.duality.village;

import net.spidrotech.duality.DualityMod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import net.minecraft.world.entity.Entity;

/**
 * Keeps records and entities honest about each other.
 *
 * <p>The record is the truth, but the world is allowed to disagree with it in one direction: an
 * NPC whose entity gets killed - by a player, a creeper, a fall - is dead, and the file has to say
 * so, or the simulation will keep raiding a village to abduct someone who isn't there.
 */
@EventBusSubscriber(modid = "duality")
public final class VillageEntityEvents {
	private VillageEntityEvents() {
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (!Villages.isReady())
			return;
		Entity entity = event.getEntity();
		if (entity.level().isClientSide())
			return;
		NpcRecord npc = Villages.store().npcByEntityUuid(entity.getUUID().toString());
		if (npc == null || !npc.isAlive())
			return;

		long day = Villages.currentDay();
		VillageRecord village = Villages.store().village(npc.currentVillageId());
		npc.setStatus(NpcStatus.DEAD);
		npc.clearFate();
		npc.setInWorld(false);
		npc.setEntityUuid("");
		npc.setCorpseLocation(new WorldPoint(entity.level().dimension().location().toString(), entity.blockPosition().getX(), entity.blockPosition().getY(),
				entity.blockPosition().getZ()));
		npc.addLogEntry(day, "Killed" + (village != null ? " at " + village.name() : "") + ".");
		Villages.store().save(npc);

		if (village != null) {
			village.residents().remove(npc.npcId());
			village.captives().remove(npc.npcId());
			village.addPopulation(-1);
			village.setMorale(village.morale() - 0.05);
			village.addLogEntry(new VillageRecord.LogEntry(day, "RESIDENT_KILLED", "RESOLVED", npc.name() + " was killed.", 0.0));
			Villages.store().save(village);
		}
		DualityMod.LOGGER.info("[duality/village] {} died; their record is closed", npc.name());
	}
}
