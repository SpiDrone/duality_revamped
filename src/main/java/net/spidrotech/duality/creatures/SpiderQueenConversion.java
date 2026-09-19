package net.spidrotech.duality.creatures;

import net.spidrotech.duality.entity.SpiderQueenEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns every MCreator Spider Queen into the real demonic spider the moment it enters the world.
 *
 * <p>The spider's behaviour - climbing, leaping, the animation state machine - can't live in the
 * Spider Queen element, because MCreator rewrites that entity class on every build. So it lives in
 * DemonSpiderEntity instead. But the Spider Queen is what the spawn egg, the creative tab and
 * {@code /summon duality:spider_queen} produce, and a separate entity nobody can spawn isn't much
 * use. This bridges the two without touching a generated file: the queen is cancelled on the way
 * in and a demon spider takes its place. That also upgrades any queens already saved in a world,
 * as their chunks load.
 *
 * <p>The replacement spawns on the next server tick rather than inside the event. Queens loaded from
 * disk arrive while their chunk is still loading, and adding an entity from inside that can
 * deadlock the chunk system - deferring one tick sidesteps it for every case at once.
 */
@EventBusSubscriber(modid = "duality")
public final class SpiderQueenConversion {
	private record Pending(ServerLevel level, double x, double y, double z, float yaw, float pitch, Component customName, boolean persistent) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private SpiderQueenConversion() {
	}

	@SubscribeEvent
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (!(event.getEntity() instanceof SpiderQueenEntity queen) || !(event.getLevel() instanceof ServerLevel level))
			return;
		event.setCanceled(true);
		PENDING.add(new Pending(level, queen.getX(), queen.getY(), queen.getZ(), queen.getYRot(), queen.getXRot(), queen.getCustomName(), queen.isPersistenceRequired()));
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (PENDING.isEmpty())
			return;
		List<Pending> batch = new ArrayList<>(PENDING);
		PENDING.clear();
		for (Pending pending : batch) {
			DemonSpiderEntity spider = DemonSpiderEntities.DEMON_SPIDER.get().create(pending.level());
			if (spider == null)
				continue;
			spider.moveTo(pending.x(), pending.y(), pending.z(), pending.yaw(), pending.pitch());
			spider.setYHeadRot(pending.yaw());
			spider.yBodyRot = pending.yaw();
			if (pending.customName() != null)
				spider.setCustomName(pending.customName());
			// A named or egg-placed queen that was set to stay shouldn't start despawning just
			// because it changed class.
			if (pending.persistent())
				spider.setPersistenceRequired();
			pending.level().addFreshEntity(spider);
		}
	}
}
