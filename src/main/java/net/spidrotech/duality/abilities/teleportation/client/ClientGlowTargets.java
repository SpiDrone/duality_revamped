package net.spidrotech.duality.abilities.teleportation.client;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

/**
 * Client-only registry of "which entities should render with a private glow outline, and in
 * what color" - private meaning visible ONLY to this client, unlike vanilla's
 * Entity#setGlowingTag (a synced entity-data flag every nearby player can see). Not currently
 * used by Orb - passenger selection feedback is handled entirely by ClientOrbitVisualManager's
 * orbiting ghost entity instead. Kept around for any future feature that wants a caster-only
 * glow (e.g. a "shimmering" power); this class doesn't know or care which feature is asking, it
 * just tracks (UUID -> ARGB color) pairs and lets ClientGlowRenderer draw them each frame.
 *
 * Usage:
 *   ClientGlowTargets.setGlow(entity.getUUID(), 0xFFFF0000); // red
 *   ...
 *   ClientGlowTargets.clearGlow(entity.getUUID());
 *
 * CAVEAT: clearAll() is blunt - it wipes every registered glow regardless of which feature set
 * it. Fine while there's zero or one consumer; if two features use this concurrently, clearAll()
 * calls would need to become feature-scoped instead of nuking everything.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientGlowTargets {
	private static final Map<UUID, Integer> GLOWING = new LinkedHashMap<>();

	private ClientGlowTargets() {
	}

	public static void setGlow(UUID entityId, int argbColor) {
		GLOWING.put(entityId, argbColor);
	}

	public static void clearGlow(UUID entityId) {
		GLOWING.remove(entityId);
	}

	public static boolean isGlowing(UUID entityId) {
		return GLOWING.containsKey(entityId);
	}

	public static void clearAll() {
		GLOWING.clear();
	}

	public static Map<UUID, Integer> snapshot() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(GLOWING));
	}
}