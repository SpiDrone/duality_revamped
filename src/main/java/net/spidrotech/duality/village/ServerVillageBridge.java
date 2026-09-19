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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
	/** What the village stores its surplus as when the simulation has grown more than the chests
	 *  currently hold. Bread keeps, stacks, and is obviously food to a player looking in. */
	private static final net.minecraft.world.item.Item STORED_FOOD = Items.BREAD;

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
		// One of the mod's own NPCs can carry its record id, so its goals can read the record
		// without a reverse lookup. A vanilla stand-in just doesn't get this.
		if (entity instanceof net.spidrotech.duality.village.entity.DualityNpcEntity duality)
			duality.bindTo(npc);
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

	// --------------------------------------------------------------------------------- pantry
	@Override
	public double readPantry(VillageRecord village) {
		List<Container> containers = pantryContainers(village, true);
		if (containers.isEmpty())
			return -1;
		double total = 0;
		for (Container container : containers) {
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				total += foodValue(container.getItem(slot));
			}
		}
		return total;
	}

	@Override
	public double writePantry(VillageRecord village, double delta) {
		List<Container> containers = pantryContainers(village, false);
		if (containers.isEmpty() || delta == 0)
			return 0;
		return delta > 0 ? addFood(containers, delta) : -removeFood(containers, -delta);
	}

	/** Puts bread in until the chests are full or the surplus is stored. */
	private double addFood(List<Container> containers, double wanted) {
		double perItem = VillagePantry.foodUnits(nutritionOf(STORED_FOOD), 1);
		if (perItem <= 0)
			return 0;
		int itemsWanted = (int) Math.floor(wanted / perItem);
		int placed = 0;
		for (Container container : containers) {
			for (int slot = 0; slot < container.getContainerSize() && placed < itemsWanted; slot++) {
				ItemStack existing = container.getItem(slot);
				if (existing.isEmpty()) {
					int count = Math.min(itemsWanted - placed, STORED_FOOD.getDefaultMaxStackSize());
					container.setItem(slot, new ItemStack(STORED_FOOD, count));
					placed += count;
				} else if (existing.is(STORED_FOOD) && existing.getCount() < existing.getMaxStackSize()) {
					int count = Math.min(itemsWanted - placed, existing.getMaxStackSize() - existing.getCount());
					existing.grow(count);
					placed += count;
				}
			}
			container.setChanged();
		}
		return placed * perItem;
	}

	/** Takes food back out, which is what "the village ate while you were away" looks like. */
	private double removeFood(List<Container> containers, double wanted) {
		double taken = 0;
		for (Container container : containers) {
			for (int slot = 0; slot < container.getContainerSize() && taken < wanted; slot++) {
				ItemStack stack = container.getItem(slot);
				double perItem = foodValue(stack) / Math.max(1, stack.getCount());
				if (perItem <= 0)
					continue;
				int needed = (int) Math.ceil((wanted - taken) / perItem);
				int count = Math.min(needed, stack.getCount());
				stack.shrink(count);
				if (stack.isEmpty())
					container.setItem(slot, ItemStack.EMPTY);
				taken += count * perItem;
			}
			container.setChanged();
		}
		return taken;
	}

	/**
	 * Every container inside a pantry building's footprint, and nowhere else. Food in a chest in
	 * somebody's house is their own business.
	 *
	 * @param placeIfMissing when the footprint is loaded but has no container at all, put a chest
	 *                       there so the village has somewhere to keep its food and a player has
	 *                       somewhere to leave some
	 */
	public List<Container> pantryContainers(VillageRecord village, boolean placeIfMissing) {
		List<Container> found = new ArrayList<>();
		for (VillageBuilding pantry : village.pantries()) {
			ServerLevel level = levelOf(pantry.position());
			if (level == null)
				continue;
			BlockPos centre = blockPos(pantry.position());
			if (!level.hasChunkAt(centre))
				continue;
			int before = found.size();
			collectContainers(level, centre, found);
			if (found.size() == before && placeIfMissing) {
				Container placed = placePantryContainer(level, centre, village);
				if (placed != null)
					found.add(placed);
			}
		}
		return found;
	}

	/** Walks the block entities of the chunks the footprint touches, rather than probing every
	 *  block position in it - a pantry's worth of cube is a few thousand lookups otherwise. */
	private void collectContainers(ServerLevel level, BlockPos centre, List<Container> found) {
		int radius = VillageBuilding.FOOTPRINT_RADIUS;
		for (int chunkX = centre.getX() - radius >> 4; chunkX <= centre.getX() + radius >> 4; chunkX++) {
			for (int chunkZ = centre.getZ() - radius >> 4; chunkZ <= centre.getZ() + radius >> 4; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk == null)
					continue;
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					BlockPos pos = entry.getKey();
					if (Math.abs(pos.getX() - centre.getX()) > radius || Math.abs(pos.getZ() - centre.getZ()) > radius
							|| Math.abs(pos.getY() - centre.getY()) > radius)
						continue;
					if (entry.getValue() instanceof Container container)
						found.add(container);
				}
			}
		}
	}

	/** Puts the village's chest in the ground, once, if there's somewhere sensible for it. */
	private Container placePantryContainer(ServerLevel level, BlockPos centre, VillageRecord village) {
		BlockPos target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre);
		// Replaceable is not enough - water is replaceable, and a chest at the bottom of a lake is
		// not a pantry anyone is going to use.
		if (!level.getBlockState(target).canBeReplaced() || !level.getFluidState(target).isEmpty() || !level.getFluidState(target.below()).isEmpty())
			return null;
		level.setBlockAndUpdate(target, Blocks.CHEST.defaultBlockState());
		DualityMod.LOGGER.info("[duality/village] placed {}'s pantry chest at {}", village.name(), target);
		return level.getBlockEntity(target) instanceof Container container ? container : null;
	}

	/** Food units a stack is worth. Anything with an effect on it - rotten flesh, pufferfish - is
	 *  not counted: a village is not fed on things that make people ill. */
	private static double foodValue(ItemStack stack) {
		if (stack.isEmpty())
			return 0;
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null || !food.effects().isEmpty())
			return 0;
		return VillagePantry.foodUnits(food.nutrition(), stack.getCount());
	}

	private static int nutritionOf(net.minecraft.world.item.Item item) {
		FoodProperties food = new ItemStack(item).get(DataComponents.FOOD);
		return food == null ? 0 : food.nutrition();
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
