package net.spidrotech.duality.abilities.demon;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.Map;

/**
 * Named fire waypoints for FlameAbility - saved on the OVERWORLD's data storage regardless of
 * which dimension each fire is actually in (same "one shared registry, lives on one level's save
 * file" pattern DualityModVariables.WorldVariables already uses), since Flame can cross
 * dimensions and there's no single obviously-correct level to attach a cross-dimension registry
 * to otherwise.
 *
 * Deliberately NOT MCreator-managed (a hand-written SavedData, same spirit as WorldVariables but
 * a separate file so nothing here is at risk of a regeneration wiping it).
 */
public class FireLinkData extends SavedData {
	public static final String DATA_NAME = "duality_fire_links";
	private final Map<String, FireWaypoint> waypoints = new ConcurrentHashMap<>();

	public static FireLinkData get(ServerLevel anyLevel) {
		ServerLevel overworld = anyLevel.getServer().overworld();
		return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FireLinkData::new, FireLinkData::load), DATA_NAME);
	}

	public Optional<FireWaypoint> get(String name) {
		return Optional.ofNullable(waypoints.get(name));
	}

	public Map<String, FireWaypoint> all() {
		return Map.copyOf(waypoints);
	}

	public void link(String name, FireWaypoint waypoint) {
		waypoints.put(name, waypoint);
		setDirty();
	}

	public boolean unlink(String name) {
		boolean removed = waypoints.remove(name) != null;
		if (removed)
			setDirty();
		return removed;
	}

	/** Drops every waypoint whose fire is gone - see FireLinkEvents for the periodic sweep that
	 *  calls this, which is what makes "destroyed if the fire is removed" true regardless of how
	 *  the fire went out (burned out, doused, exploded, mined). */
	public void pruneMissing(java.util.function.BiPredicate<ResourceKey<Level>, BlockPos> stillOnFire) {
		if (waypoints.values().removeIf(w -> !stillOnFire.test(w.dimension(), w.pos())))
			setDirty();
	}

	private static FireLinkData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
		FireLinkData data = new FireLinkData();
		for (String name : tag.getAllKeys()) {
			FireWaypoint.CODEC.parse(NbtOps.INSTANCE, tag.get(name)).result().ifPresent(w -> data.waypoints.put(name, w));
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookupProvider) {
		waypoints.forEach((name, waypoint) -> {
			Tag encoded = FireWaypoint.CODEC.encodeStart(NbtOps.INSTANCE, waypoint).result().orElse(null);
			if (encoded != null)
				tag.put(name, encoded);
		});
		return tag;
	}
}
