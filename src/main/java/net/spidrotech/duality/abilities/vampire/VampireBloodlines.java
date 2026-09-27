package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.faction.Factions;
import net.spidrotech.duality.faction.FactionRecord;
import net.spidrotech.duality.village.Villages;
import net.spidrotech.duality.init.DualityModAttributes;
import net.spidrotech.duality.DualityDatabaseManager;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ties vampires into the faction system: a Queen's court is just a faction, and this is what
 * founds one automatically rather than requiring a player to run /faction create by hand.
 *
 * TWO paths into {@link #attemptBloodline}, both landing on the same logic so neither can produce
 * a result the other wouldn't also accept:
 *
 * <ul>
 * <li>BOOTSTRAP - {@link #onVampireSpawned} fires when a character becomes a vampire (currently
 * only VampireCommand#setVampire; a real infection mechanic should call this too). If literally no
 * vampire bloodline exists anywhere, one is founded unconditionally, and the new vampire is
 * promoted straight to Queen to back up the title - "spawns" was written with no rank qualifier on
 * purpose, so the world is never left with vampires but no vampire society at all.</li>
 * <li>NATURAL - the day-gated tick sweep below. Once at least one bloodline already exists, only a
 * Queen not already in a faction is eligible, and even then only rarely - see
 * {@link #spawnChance}: the chance scales with how evil the world's duality score is, and decays
 * multiplicatively with every bloodline that already exists, so the vampire population
 * self-limits instead of a new court popping up next to every existing one.</li>
 * </ul>
 *
 * Checked once per in-game day per player, not per tick - a base chance meant to read as "rare"
 * would not if it were rolled every second instead.
 */
@EventBusSubscriber(modid = "duality")
public final class VampireBloodlines {
	public static final String FLAG_BLOODLINE = "vampire_bloodline";

	// ---- tunable ----
	/** Chance per in-game day, at duality = -1000 (fully evil) and zero existing bloodlines. */
	private static final double MAX_CHANCE_AT_FULL_EVIL = 0.02;
	/** Multiplies the chance for every bloodline that already exists - each one roughly halves the
	 *  odds of the next, which is what makes the population self-limiting rather than unbounded. */
	private static final double DECAY_PER_EXISTING_BLOODLINE = 0.5;

	private static final Map<UUID, Long> lastCheckedDay = new ConcurrentHashMap<>();

	private VampireBloodlines() {
	}

	/** Call the moment a character becomes a vampire, however that happens. */
	public static void onVampireSpawned(ServerPlayer player) {
		attemptBloodline(player);
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide())
			return;
		if (!VampireRank.isVampire(player))
			return;
		long today = player.level().getDayTime() / 24000L;
		Long last = lastCheckedDay.get(player.getUUID());
		if (last != null && last == today)
			return;
		lastCheckedDay.put(player.getUUID(), today);
		attemptBloodline(player);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			lastCheckedDay.remove(player.getUUID());
	}

	/** Safe to call redundantly - a vampire already in any faction (bloodline or otherwise) is
	 *  always skipped, so calling this twice in the same tick does nothing the second time. */
	private static void attemptBloodline(ServerPlayer vampire) {
		if (!Factions.isReady() || !Villages.isReady())
			return;
		String characterId = DualityDatabaseManager.getActiveCharacterId(vampire);
		if (characterId == null || characterId.isEmpty() || Factions.factionOf(characterId) != null)
			return;
		int existing = countBloodlines();
		boolean bootstrap = existing == 0;
		if (!bootstrap) {
			// Only a Queen can found an ADDITIONAL bloodline - the bootstrap case is the one
			// exception, handled below, where whoever spawned first is crowned to make one exist.
			if (!VampireRank.QUEEN.isAtLeast(vampire))
				return;
			if (Villages.random().nextDouble() >= spawnChance(existing))
				return;
		}
		if (bootstrap) {
			AttributeInstance rank = vampire.getAttribute(DualityModAttributes.VAMPIRE_RANK);
			if (rank != null)
				rank.setBaseValue(VampireRank.QUEEN.level());
		}
		Factions.CreateOutcome outcome = Factions.createForPlayer(vampire, bloodlineName(characterId));
		if (!outcome.ok())
			return;
		FactionRecord bloodline = outcome.faction();
		bloodline.flags().add(FLAG_BLOODLINE);
		bloodline.addLogEntry(Factions.currentDay(), bootstrap ? "The first vampire bloodline in a darkened world." : "A new bloodline rises.");
		// VAMPIRE kind is what makes membership rules actually apply to it - see FactionKind: only
		// a true or humanized-apparent vampire can join, and its alignment (EVIL, VAMPIRE's
		// default) is what blocks it from ever allying with a GOOD faction.
		Factions.setKind(bloodline, net.spidrotech.duality.faction.FactionKind.VAMPIRE);
	}

	public static boolean isBloodline(FactionRecord faction) {
		return faction != null && faction.flags().contains(FLAG_BLOODLINE);
	}

	public static int countBloodlines() {
		if (!Factions.isReady())
			return 0;
		int count = 0;
		for (FactionRecord faction : Factions.store().factions()) {
			if (isBloodline(faction))
				count++;
		}
		return count;
	}

	/** 0 (fully good/neutral world) .. MAX_CHANCE_AT_FULL_EVIL (fully evil), then decayed per
	 *  existing bloodline. Math.pow's own asymptote toward 0 means no explicit floor is needed. */
	private static double spawnChance(int existingBloodlines) {
		double evilFactor = Math.max(0.0, -Villages.store().world().normalized());
		return MAX_CHANCE_AT_FULL_EVIL * evilFactor * Math.pow(DECAY_PER_EXISTING_BLOODLINE, existingBloodlines);
	}

	private static String bloodlineName(String characterId) {
		String base = Factions.displayName(characterId) + "'s Bloodline";
		if (!Factions.store().nameIsTaken(base))
			return base;
		int suffix = 2;
		while (Factions.store().nameIsTaken(base + " " + suffix)) {
			suffix++;
		}
		return base + " " + suffix;
	}
}
