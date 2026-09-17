package net.spidrotech.duality.village;

import net.spidrotech.duality.DualityMod;

import net.minecraft.world.entity.EntityType;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Decides which entity a village record spawns as.
 *
 * <p>These are duality settlements with duality NPCs in them. A vanilla villager may end up living
 * in one - {@code /village npc add} will happily enroll whatever is standing there, and a mod-added
 * mob works just as well - but they are the exception, not the model. So this is the single place
 * that answers "what does this record look like in the world", and it asks for the mod's own NPCs
 * first, every time.
 *
 * <p>The duality NPC entities don't exist yet. Until they're registered this resolves to the
 * vanilla stand-in and says so once in the log. The moment {@code duality:npc} (or a species or job
 * variant of it) is registered, every existing record picks it up on its next spawn with no
 * migration, because records store a species and a job rather than an entity id.
 *
 * <p>Candidate ids are tried most specific first, so registering any subset works:
 *
 * <pre>
 *   duality:npc_vampire_guard     species + job
 *   duality:npc_guard             job
 *   duality:npc_vampire           species
 *   duality:npc                   the general duality villager
 *   minecraft:villager            stand-in, until one of the above exists
 * </pre>
 */
public final class VillageNpcTypes {
	/** Namespace the mod's own NPC entities are registered under. */
	public static final String NAMESPACE = "duality";
	/** Where records land when the mod has no NPC entity registered yet. */
	public static final String FALLBACK = "minecraft:villager";

	private static boolean warnedAboutFallback = false;

	private VillageNpcTypes() {
	}

	/**
	 * The entity id this NPC should spawn as.
	 *
	 * <p>A record that was made by enrolling a specific existing entity keeps that entity's type -
	 * enrolling a vanilla villager means you get a vanilla villager back. Everyone else is resolved
	 * from what they are.
	 */
	public static String resolve(NpcRecord npc, VillageRecord village) {
		if (npc.entityType() != null && !npc.entityType().isEmpty())
			return npc.entityType();
		for (String candidate : candidates(npc, village)) {
			if (isRegistered(candidate))
				return candidate;
		}
		if (!warnedAboutFallback) {
			warnedAboutFallback = true;
			DualityMod.LOGGER.info("[duality/village] no {} NPC entities registered yet; village residents will spawn as {} until there are", NAMESPACE, FALLBACK);
		}
		return FALLBACK;
	}

	/**
	 * Ids to try, most specific first. Split out from {@link #resolve} so it can be read and tested
	 * without a registry - the naming scheme is the contract between this and whatever entities get
	 * registered later.
	 */
	public static Set<String> candidates(NpcRecord npc, VillageRecord village) {
		Set<String> ids = new LinkedHashSet<>();
		String species = slug(npc.species());
		String job = slug(npc.job().name());
		String faction = village == null ? "" : slug(village.faction().name());
		if (!species.isEmpty() && !job.isEmpty())
			ids.add(NAMESPACE + ":npc_" + species + "_" + job);
		if (!job.isEmpty())
			ids.add(NAMESPACE + ":npc_" + job);
		if (!species.isEmpty())
			ids.add(NAMESPACE + ":npc_" + species);
		if (!faction.isEmpty())
			ids.add(NAMESPACE + ":npc_" + faction);
		ids.add(NAMESPACE + ":npc");
		return ids;
	}

	private static boolean isRegistered(String id) {
		Optional<EntityType<?>> type = EntityType.byString(id);
		return type.isPresent();
	}

	/** "Vampire" -> "vampire", "WITCH_COVEN" -> "witch_coven". Anything that isn't a-z, 0-9 or _
	 *  is dropped, so a hand-edited species can't produce an invalid resource path. */
	private static String slug(String raw) {
		if (raw == null)
			return "";
		StringBuilder out = new StringBuilder(raw.length());
		for (char c : raw.toLowerCase().toCharArray()) {
			if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')
				out.append(c);
			else if (c == ' ' || c == '-')
				out.append('_');
		}
		return out.toString();
	}
}
