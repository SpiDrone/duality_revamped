package net.spidrotech.duality.village;

import net.spidrotech.duality.DualityMod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The front door to the village system.
 *
 * <p>This is the layer that holds the running server, so the rest of the mod can say
 * {@code Villages.run("Ashmere", VillageEvent.VAMPIRE_RAID)} without carrying a store, a bridge and
 * a calendar around with it. Everything it does is delegated to {@link VillageEvents} and
 * {@link VillageSimulator}, which don't know a server exists.
 *
 * <pre>
 *   Villages.run("Ashmere", VillageEvent.VAMPIRE_RAID);          // fire an event now
 *   Villages.found("Ashmere", VillageFaction.HUMAN, level, pos, 4); // village + starting roster // register a village
 *   Villages.enroll(village, entity, NpcJob.HERBALIST);           // give a mob a record and a job
 *   Villages.store().world().dualityScore();                      // read the world's standing
 * </pre>
 */
public final class Villages {
	private static MinecraftServer server;
	private static VillageStore store;
	private static ServerVillageBridge bridge;
	private static final Random RANDOM = new Random();

	private Villages() {
	}

	// ------------------------------------------------------------------------------ lifecycle
	/** Called on server start. Loads every village off disk and works off the simulation backlog. */
	public static void attach(MinecraftServer startingServer) {
		server = startingServer;
		File dualityDir = new File(startingServer.getWorldPath(LevelResource.ROOT).toFile(), "duality_data");
		store = new VillageStore(dualityDir).load();
		bridge = new ServerVillageBridge(startingServer);
		VillageEconomy.recomputeAll(store);
		List<VillageSimulator.DayReport> backlog = VillageSimulator.catchUp(store, bridge, currentDay(), RANDOM);
		int events = 0;
		for (VillageSimulator.DayReport report : backlog) {
			events += report.events().size() + report.notes().size();
		}
		DualityMod.LOGGER.info("[duality] villages loaded: {} settlements, {} named npcs, {} days caught up ({} things happened), world duality {}",
				store.villages().size(), store.npcs().size(), backlog.size(), events, String.format("%.1f", store.world().dualityScore()));
	}

	/** Called on server stop. */
	public static void detach() {
		if (store != null)
			store.saveAll();
		server = null;
		store = null;
		bridge = null;
	}

	public static boolean isReady() {
		return store != null;
	}

	public static MinecraftServer server() {
		return server;
	}

	public static VillageStore store() {
		return store;
	}

	public static ServerVillageBridge bridge() {
		return bridge;
	}

	public static Random random() {
		return RANDOM;
	}

	/** The overworld day count, which is the clock every village and every prisoner's fate runs on. */
	public static long currentDay() {
		if (server == null || server.overworld() == null)
			return 0;
		return server.overworld().getDayTime() / 24000L;
	}

	// ---------------------------------------------------------------------------------- events
	/**
	 * Runs an event against a village by name. The shape the whole system was designed around.
	 *
	 * @return what happened; never null, but {@link VillageEventResult#happened()} is false when the
	 *         event couldn't take place (no such village, wrong faction, nobody left to take)
	 */
	public static VillageEventResult run(String villageName, VillageEvent event) {
		if (!isReady())
			throw new IllegalStateException("Villages.run called with no server attached");
		return VillageEvents.run(store, bridge, villageName, event, currentDay(), RANDOM);
	}

	/** As {@link #run(String, VillageEvent)}, taking the event id as text. */
	public static VillageEventResult run(String villageName, String eventId) {
		VillageEvent event = VillageEvent.parse(eventId);
		if (event == null)
			throw new IllegalArgumentException("Unknown village event \"" + eventId + "\"");
		return run(villageName, event);
	}

	/**
	 * Simulates the next unsimulated day for every village.
	 *
	 * <p>The day comes from the simulation clock, not the world clock, so calling this five times
	 * in a row runs five distinct days rather than the same one five times over. Running ahead of
	 * the world clock that way is fine: the ticker simply has nothing to do until the world catches
	 * up.
	 */
	public static VillageSimulator.DayReport simulateDay() {
		if (!isReady())
			throw new IllegalStateException("Villages.simulateDay called with no server attached");
		long day = Math.max(store.world().lastSimulatedDay() + 1, currentDay());
		VillageSimulator.DayReport report = VillageSimulator.simulateDay(store, bridge, day, RANDOM);
		store.world().setLastSimulatedDay(day);
		store.world().addSimulatedDays(1);
		store.saveWorld();
		return report;
	}

	/**
	 * Puts entities under records that ought to have one. A resident whose village is loaded but
	 * who has no entity - because they were just turned at their captor's hold, or because the
	 * server restarted with them marked absent - gets spawned here.
	 *
	 * <p>Self-gating: {@link ServerVillageBridge#spawnAt} refuses when the chunk isn't loaded, so
	 * this can be called on a timer without checking anything itself.
	 *
	 * @return how many entities were placed
	 */
	public static int embodySweep() {
		if (!isReady())
			return 0;
		int spawned = 0;
		for (VillageRecord village : store.villages()) {
			if (village.isAbandoned())
				continue;
			for (NpcRecord npc : store.residentsOf(village)) {
				// inWorld is only cleared when we know there's no entity - on a deliberate despawn
				// or a confirmed death - so this never double-spawns someone who just wandered into
				// an unloaded chunk.
				if (npc.inWorld() || !npc.shouldBeEmbodied())
					continue;
				if (bridge.spawnAt(npc, village)) {
					store.save(npc);
					spawned++;
				}
			}
		}
		return spawned;
	}

	/**
	 * Reconciles every village's pantry chests with its food ledger.
	 *
	 * <p>This is where a player dropping a stack of bread into the village chest actually becomes
	 * food the village eats, and where a month of eating that happened on cold chunks becomes an
	 * emptier chest. Villages whose pantries aren't loaded are skipped and lose nothing by it - the
	 * ledger is still the truth, and it catches up next time someone is nearby.
	 *
	 * @return how many villages were reconciled
	 */
	public static int pantrySweep() {
		if (!isReady())
			return 0;
		int synced = 0;
		for (VillageRecord village : store.villages()) {
			VillagePantry.SyncResult result = VillagePantry.sync(store, bridge, village);
			if (!result.synced())
				continue;
			synced++;
			if (result.playerContributed()) {
				bridge.announce(village, String.format("Someone has put %.0f days of food in %s's pantry. It holds %.0f of %.0f now.", result.playerDelta(),
						village.name(), result.stored(), result.capacity()));
				village.setMorale(village.morale() + Math.min(0.08, result.playerDelta() * 0.004));
				// Food arriving from outside is the one thing that can break a blighted village's
				// death spiral, so it's worth something on the duality score.
				store.applyDuality(Math.min(6.0, result.playerDelta() * 0.15) * (village.faction().isEvil() ? -0.5 : 1.0));
				store.save(village);
			}
		}
		return synced;
	}

	/** Puts up a building at a position and hands it back. The pantry is the one worth placing by
	 *  hand: put it where you want the village's chest to be. */
	public static VillageBuilding addBuilding(VillageRecord village, BuildingType type, ServerLevel level, BlockPos pos) {
		if (!isReady() || village == null)
			return null;
		VillageBuilding building = new VillageBuilding(type, currentDay(), pointOf(level, pos));
		village.buildings().add(building);
		VillageEconomy.recompute(store, village);
		store.save(village);
		return building;
	}

	// ------------------------------------------------------------------------------ registration
	/** Registers a new village at a position. Returns null if the name is already taken. */
	public static VillageRecord create(String name, VillageFaction faction, ServerLevel level, BlockPos pos) {
		if (!isReady() || store.nameIsTaken(name))
			return null;
		VillageRecord village = VillageRecord.create(name, faction, pointOf(level, pos), currentDay());
		store.add(village);
		return village;
	}

	/**
	 * Gives an existing entity a record and makes it a resident. From that moment it has a job, eats,
	 * and can be abducted, turned, starved, killed or rescued like anyone else.
	 *
	 * <p>The entity's own type is pinned onto the record, so enrolling a vanilla villager means you
	 * keep a vanilla villager. Residents the simulation creates itself have no pinned type and
	 * resolve through {@link VillageNpcTypes} instead, which asks for the mod's own NPCs first.
	 *
	 * @param job the job to give them, or null to let the village's needs decide
	 */
	public static NpcRecord enroll(VillageRecord village, Entity entity, NpcJob job) {
		if (!isReady() || village == null || entity == null)
			return null;
		String name = entity.hasCustomName() ? entity.getCustomName().getString() : VillageNames.person(RANDOM);
		NpcRecord npc = NpcRecord.create(name, village.villageId());
		npc.setSpecies(VillageEconomy.speciesFor(village));
		npc.setJob(job != null ? job : VillageEconomy.neededJob(store, village, RANDOM));
		npc.setEntityType(EntityType.getKey(entity.getType()).toString());
		npc.setEntityUuid(entity.getUUID().toString());
		npc.setInWorld(true);
		npc.addLogEntry(currentDay(), "Enrolled as a " + npc.jobLabel() + " of " + village.name() + ".");
		store.add(npc);
		village.residents().add(npc.npcId());
		if (village.population() < village.residents().size())
			village.setPopulation(village.residents().size());
		store.save(village);
		VillageEconomy.recompute(store, village);
		store.save(village);
		return npc;
	}

	/**
	 * Registers a village and gives it a starting roster: a few named residents with the jobs a new
	 * settlement needs. This is what {@code /village create} uses, and what a settlement founded by
	 * the simulation is modelled on - a village with no named residents has no economy to speak of,
	 * because unnamed population only ever feeds itself.
	 */
	public static VillageRecord found(String name, VillageFaction faction, ServerLevel level, BlockPos pos, int namedResidents) {
		VillageRecord village = create(name, faction, level, pos);
		if (village == null)
			return null;
		for (int i = 0; i < namedResidents; i++) {
			NpcRecord npc = NpcRecord.create(VillageNames.person(RANDOM), village.villageId());
			npc.setSpecies(VillageEconomy.speciesFor(village));
			npc.setJob(VillageEconomy.neededJob(store, village, RANDOM));
			npc.addLogEntry(currentDay(), "A founding resident of " + village.name() + ".");
			store.add(npc);
			village.residents().add(npc.npcId());
			VillageEconomy.recompute(store, village);
		}
		if (village.population() < village.residents().size())
			village.setPopulation(village.residents().size());
		VillageEconomy.recompute(store, village);
		store.save(village);
		return village;
	}

	// ----------------------------------------------------------------------------------- lookup
	public static WorldPoint pointOf(ServerLevel level, BlockPos pos) {
		return new WorldPoint(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
	}

	/** The village whose bounds contain this position, or null. Nearest wins if they overlap. */
	public static VillageRecord villageAt(ServerLevel level, BlockPos pos) {
		if (!isReady())
			return null;
		WorldPoint point = pointOf(level, pos);
		VillageRecord best = null;
		double bestDistSqr = Double.MAX_VALUE;
		for (VillageRecord village : store.villages()) {
			double distSqr = village.center().horizontalDistanceSqr(point);
			if (distSqr < 0 || distSqr > (double) village.radius() * village.radius())
				continue;
			if (distSqr < bestDistSqr) {
				bestDistSqr = distSqr;
				best = village;
			}
		}
		return best;
	}

	/** Every village within range of a position, nearest first - for "what's around here". */
	public static List<VillageRecord> villagesNear(ServerLevel level, BlockPos pos, double range) {
		List<VillageRecord> found = new ArrayList<>();
		if (!isReady())
			return found;
		WorldPoint point = pointOf(level, pos);
		for (VillageRecord village : store.villages()) {
			double distSqr = village.center().horizontalDistanceSqr(point);
			if (distSqr >= 0 && distSqr <= range * range)
				found.add(village);
		}
		found.sort((a, b) -> Double.compare(a.center().horizontalDistanceSqr(point), b.center().horizontalDistanceSqr(point)));
		return found;
	}

	/** Finds a named NPC by id, or by name if the id doesn't match anything. */
	public static NpcRecord findNpc(String idOrName) {
		if (!isReady() || idOrName == null)
			return null;
		NpcRecord byId = store.npc(idOrName);
		if (byId != null)
			return byId;
		for (NpcRecord npc : store.npcs()) {
			if (npc.name().equalsIgnoreCase(idOrName))
				return npc;
		}
		return null;
	}
}
