package net.spidrotech.duality.faction;

import net.spidrotech.duality.village.Villages;
import net.spidrotech.duality.DualityDatabaseManager;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Removed if they're found out" - the ONE way a fake membership (see
 * FactionRecord#fakeMembers) ever ends on its own, as opposed to
 * {@link Factions#recheckMembership} downgrading someone into one or a rules change removing one
 * outright.
 *
 * <p>Checked once per in-game day per online player, same reasoning as the old vampire-only
 * version this replaces: a chance rolled every tick wouldn't read as "rare" or "eventually", no
 * matter how small. The chance itself comes from {@link MemberKinds#concealmentEffectiveness} -
 * a vampire passing as human gets the benefit of Vampire Mode's concealment; anyone else faking a
 * faction (a demon, say) has no equivalent mechanic yet, so they sit at the flat baseline rate
 * until one exists.
 */
@EventBusSubscriber(modid = "duality")
public final class FactionImpostorEvents {
	// ---- tunable: chance per in-game day a fake membership is noticed, before concealment reduces it ----
	private static final double BASE_EXPOSURE_CHANCE = 0.05;

	private static final Map<UUID, Long> lastCheckedDay = new ConcurrentHashMap<>();

	private FactionImpostorEvents() {
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide())
			return;
		if (!Factions.isReady())
			return;
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId == null || characterId.isEmpty())
			return;
		long today = player.level().getDayTime() / 24000L;
		Long last = lastCheckedDay.get(player.getUUID());
		if (last != null && last == today)
			return;
		lastCheckedDay.put(player.getUUID(), today);
		checkExposure(player, characterId);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		lastCheckedDay.remove(event.getEntity().getUUID());
	}

	private static void checkExposure(ServerPlayer player, String characterId) {
		List<FactionRecord> fakedFactions = Factions.fakeFactionsOf(characterId);
		if (fakedFactions.isEmpty())
			return;
		double concealment = MemberKinds.concealmentEffectiveness(player);
		double exposureChance = BASE_EXPOSURE_CHANCE * (1.0 - concealment);
		for (FactionRecord faction : fakedFactions) {
			if (Villages.random().nextDouble() >= exposureChance)
				continue;
			if (Factions.removeFakeMember(faction, characterId)) {
				player.displayClientMessage(Component.literal(faction.name() + " has found out what you really are, and cast you out."), false);
			}
		}
	}
}
