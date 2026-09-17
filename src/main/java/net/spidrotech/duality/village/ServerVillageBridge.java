package net.spidrotech.duality.village;

import net.spidrotech.duality.DualityMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.UUID;

/**
 * The live implementation of {@link VillageWorldBridge}: the only class in this package that knows
 * entities exist.
 *
 * <p>Everything here is best-effort by design. If a village's chunks aren't loaded there is no
 * entity to despawn and nowhere to put one, and that's fine - the record has already been updated,
 * and the record is the truth. The entity catches up whenever someone next walks over there.
 */
public class ServerVillageBridge implements VillageWorldBridge {
	/** How far from a village a player has to be to hear about what just happened there. */
	private static final double ANNOUNCE_RADIUS = 96.0;

	private final MinecraftServer server;

	public ServerVillageBridge(MinecraftServer server) {
		this.server = server;
	}

	@Override
	public void despawn(NpcRecord npc) {
		Entity entity = findEntity(npc);
		if (entity != null)
			entity.discard();
		npc.setEntityUuid("");
		npc.setInWorld(false);
	}

	@Override
	public boolean spawnAt(NpcRecord npc, VillageRecord village) {
		if (village == null || !npc.shouldBeEmbodied())
			return false;
		ServerLevel level = levelOf(village.center());
		if (level == null)
			return false;
		BlockPos pos = blockPos(village.center());
		// Nothing to spawn into. The record still says they're here; the entity appears when the
		// chunk does.
		if (!level.hasChunkAt(pos))
			return false;
		// Duality settlements are meant to be full of duality NPCs; VillageNpcTypes picks the most
		// specific one the mod has registered for this species and job, and only falls back to a
		// vanilla villager while none exist.
		String entityId = VillageNpcTypes.resolve(npc, village);
		Optional<EntityType<?>> type = EntityType.byString(entityId);
		if (type.isEmpty()) {
			DualityMod.LOGGER.warn("[duality] village NPC {} resolved to unknown entity type {}", npc.npcId(), entityId);
			return false;
		}
		Entity entity = type.get().spawn(level, pos.above(), MobSpawnType.EVENT);
		if (entity == null)
			return false;
		entity.setCustomName(Component.literal(npc.name()));
		entity.setCustomNameVisible(true);
		// Named villagers are not despawn fodder - losing one to a mob cap would quietly break a
		// quest that's already been written into a file.
		if (entity instanceof Mob mob)
			mob.setPersistenceRequired();
		npc.setEntityUuid(entity.getUUID().toString());
		npc.setInWorld(true);
		return true;
	}

	@Override
	public void placeCorpse(NpcRecord npc, VillageRecord village) {
		// The location is what matters and it's already on the record; a corpse entity to render it
		// with is a separate piece of work. Until there is one, a player arriving too late finds
		// the record (via /village npc) rather than a body.
		if (npc.corpseLocation() == null && village != null)
			npc.setCorpseLocation(village.center());
		DualityMod.LOGGER.info("[duality] {} died at {}", npc.name(), npc.corpseLocation());
	}

	@Override
	public WorldPoint resolveSite(WorldPoint proposal) {
		ServerLevel level = levelOf(proposal);
		if (level == null)
			return null;
		BlockPos pos = blockPos(proposal);
		// Only snap to the real surface where the terrain already exists. Asking for a heightmap in
		// an ungenerated chunk would generate it, and this runs for parts of the world nobody has
		// ever been to - so out there the proposal stands and the settlement sits at the parent's
		// elevation until someone goes and looks.
		if (!level.hasChunkAt(pos))
			return proposal;
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos);
		if (level.getFluidState(surface.below()).isSource())
			return null;
		return new WorldPoint(proposal.dimension(), surface.getX(), surface.getY(), surface.getZ());
	}

	@Override
	public void announce(VillageRecord village, String line) {
		DualityMod.LOGGER.info("[duality/village] {}: {}", village.name(), line);
		ServerLevel level = levelOf(village.center());
		if (level == null)
			return;
		BlockPos center = blockPos(village.center());
		double radiusSqr = (ANNOUNCE_RADIUS + village.radius()) * (ANNOUNCE_RADIUS + village.radius());
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(center) <= radiusSqr)
				player.displayClientMessage(Component.literal("§7" + line), false);
		}
	}

	// -------------------------------------------------------------------------------- lookups
	/** The entity backing this record, or null if it isn't loaded (or isn't there any more). */
	public Entity findEntity(NpcRecord npc) {
		if (npc.entityUuid() == null || npc.entityUuid().isEmpty())
			return null;
		UUID uuid;
		try {
			uuid = UUID.fromString(npc.entityUuid());
		} catch (IllegalArgumentException e) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(uuid);
			if (entity != null)
				return entity;
		}
		return null;
	}

	public ServerLevel levelOf(WorldPoint point) {
		ResourceLocation id = ResourceLocation.tryParse(point.dimension());
		if (id == null)
			return null;
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
		return server.getLevel(key);
	}

	public static BlockPos blockPos(WorldPoint point) {
		return new BlockPos(point.x(), point.y(), point.z());
	}
}
