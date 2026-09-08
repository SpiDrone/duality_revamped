package net.spidrotech.duality.abilities.teleportation;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks who's "called" which whitelighter recently - i.e. said their name in chat - Charmed-
 * style. Deliberately does NOT store a position: it only remembers WHO called and WHEN, so
 * whatever reads this (the picker, and OrbDestinationResolver) always looks up the caller's
 * CURRENT position fresh, rather than orbing to a stale snapshot of wherever they happened to be
 * standing when they spoke.
 */
public final class WhitelighterCallRegistry {
	private static final WhitelighterCallRegistry INSTANCE = new WhitelighterCallRegistry();
	private static final long CALL_LIFETIME_TICKS = 20 * 60; // a call stays active for 60s if unanswered

	public static WhitelighterCallRegistry get() {
		return INSTANCE;
	}

	public record Call(UUID callerId, String callerName, long expiryTick) {
	}

	private final Map<UUID, List<Call>> calls = new ConcurrentHashMap<>();

	private WhitelighterCallRegistry() {
	}

	public void recordCall(UUID whitelighterId, UUID callerId, String callerName, long now) {
		List<Call> list = calls.computeIfAbsent(whitelighterId, k -> new ArrayList<>());
		list.removeIf(c -> c.callerId().equals(callerId));
		list.add(new Call(callerId, callerName, now + CALL_LIFETIME_TICKS));
	}

	public List<Call> activeCalls(UUID whitelighterId, long now) {
		List<Call> list = calls.get(whitelighterId);
		if (list == null)
			return List.of();
		list.removeIf(c -> c.expiryTick() <= now);
		return List.copyOf(list);
	}

	public void clearCalls(UUID whitelighterId) {
		calls.remove(whitelighterId);
	}
}
