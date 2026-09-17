package net.spidrotech.duality;

import org.checkerframework.checker.units.qual.cd;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

/**
 * Central runtime hub for the whole power system: registering ability definitions, activating
 * them, ticking charges/active effects, handling interruption, and tracking cooldowns.
 *
 * Casters are LivingEntity, not specifically players - any mob/NPC entity type can use
 * abilities as long as it's driven by AbilityEvents (see that class) and, for abilities that
 * read custom attributes, has those attributes attached via EntityAttributeModificationEvent
 * (see ModAttributes).
 *
 * This is a plain server-side singleton rather than a capability/attachment, because its state
 * (in-progress casts, running timers) is transient and doesn't need to survive a server restart.
 */
public final class AbilityManager {
	private static final AbilityManager INSTANCE = new AbilityManager();

	public static AbilityManager get() {
		return INSTANCE;
	}

	private final Map<ResourceLocation, Ability> registry = new LinkedHashMap<>();
	private final Map<UUID, Map<ResourceLocation, AbilityInstance>> running = new ConcurrentHashMap<>();
	private final Map<UUID, Map<ResourceLocation, Long>> cooldowns = new ConcurrentHashMap<>();

	private AbilityManager() {
	}

	// ================================================================== registration
	public void register(Ability ability) {
		if (registry.putIfAbsent(ability.id(), ability) != null) {
			throw new IllegalStateException("Duplicate ability id: " + ability.id());
		}
	}

	public Optional<Ability> get(ResourceLocation id) {
		return Optional.ofNullable(registry.get(id));
	}

	public Collection<Ability> all() {
		return registry.values();
	}

	// ================================================================== activation
	/** Attempts to activate an ability for any LivingEntity caster - a player, a mob, an NPC. */
	public ActivationResult tryActivate(LivingEntity caster, ResourceLocation id) {
		return tryActivate(caster, id, ctx -> {
		});
	}

	/**
	 * Same as tryActivate(caster, id), but lets you configure the freshly-created AbilityContext
	 * before cast conditions are checked / charging starts - e.g. a test command that wants to
	 * set an explicit destination via ctx.setTargetPos(...).
	 */
	public ActivationResult tryActivate(LivingEntity caster, ResourceLocation id, Consumer<AbilityContext> setup) {
		Ability ability = registry.get(id);
		if (ability == null) {
			return ActivationResult.fail(Component.literal("Unknown ability: " + id));
		}
		long now = caster.level().getGameTime();
		UUID uuid = caster.getUUID();
		if (isOnCooldown(uuid, id, now)) {
			return ActivationResult.fail(Component.literal("That ability is still on cooldown."));
		}
		Map<ResourceLocation, AbilityInstance> casterRunning = running.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
		if (ability.type() == AbilityType.TOGGLE && casterRunning.containsKey(id)) {
			deactivate(caster, ability, casterRunning.get(id), now);
			return ActivationResult.success();
		}
		if (casterRunning.containsKey(id)) {
			return ActivationResult.fail(Component.literal("That's already active."));
		}
		AbilityContext ctx = new AbilityContext(caster, ability);
		setup.accept(ctx);
		for (AbilityCondition condition : ability.castConditions()) {
			if (!condition.test(ctx)) {
				return ActivationResult.fail(condition.failureMessage(ctx));
			}
		}
		if (ability.type() == AbilityType.CHANNELED) {
			AbilityInstance instance = new AbilityInstance(ctx, AbilityInstance.State.CHARGING, now);
			casterRunning.put(id, instance);
			ability.onChargeStart(ctx);
		} else {
			activateNow(caster, ability, ctx, now);
		}
		return ActivationResult.success();
	}

	/** Whether caster would pass every cast condition for id right now, without casting it -
	 *  ignores cooldown and whether it's already running. This is the "does this caster have access
	 *  to this ability" check (e.g. rank gates), used when a mode auto-toggles abilities on. */
	public boolean meetsCastConditions(LivingEntity caster, ResourceLocation id) {
		Ability ability = registry.get(id);
		if (ability == null)
			return false;
		AbilityContext ctx = new AbilityContext(caster, ability);
		for (AbilityCondition condition : ability.castConditions()) {
			if (!condition.test(ctx))
				return false;
		}
		return true;
	}

	/** Adds a fellow traveller to an in-progress cast (e.g. right-clicking a party member while
	 *  Orb is charging). */
	public void addTarget(LivingEntity caster, ResourceLocation id, Entity target) {
		AbilityInstance instance = runningInstance(caster.getUUID(), id);
		if (instance != null) {
			instance.context().addTarget(target);
		}
	}

	/** Cancels a charging ability, or turns off an active TOGGLE/duration effect early. Also the
	 *  quickest way to clear a stuck instance during testing. */
	public void cancel(LivingEntity caster, ResourceLocation id) {
		AbilityInstance instance = runningInstance(caster.getUUID(), id);
		if (instance == null)
			return;
		Ability ability = registry.get(id);
		long now = caster.level().getGameTime();
		if (instance.state() == AbilityInstance.State.CHARGING) {
			ability.onChargeInterrupted(instance.context());
			running.get(caster.getUUID()).remove(id);
		} else {
			deactivate(caster, ability, instance, now);
		}
	}

	// ================================================================== ticking
	/** Call once per entity, per server tick (see AbilityEvents#onEntityTick). */
	public void tick(LivingEntity caster) {
		Map<ResourceLocation, AbilityInstance> casterRunning = running.get(caster.getUUID());
		if (casterRunning == null || casterRunning.isEmpty())
			return;
		long now = caster.level().getGameTime();
		for (AbilityInstance instance : new ArrayList<>(casterRunning.values())) {
			Ability ability = instance.context().ability();
			boolean interrupted = false;
			for (AbilityCondition condition : ability.maintainConditions()) {
				if (!condition.test(instance.context())) {
					interruptOrDeactivate(caster, ability, instance, now);
					casterRunning.remove(ability.id());
					interrupted = true;
					break;
				}
			}
			if (interrupted)
				continue;
			if (instance.state() == AbilityInstance.State.CHARGING) {
				double requiredTicks = ability.chargeTime().resolve(instance.context());
				long elapsed = instance.ticksInState(now);
				float progress = requiredTicks <= 0 ? 1f : (float) Math.min(1.0, elapsed / requiredTicks);
				ability.onChargeTick(instance.context(), progress);
				if (elapsed >= requiredTicks) {
					activateNow(caster, ability, instance.context(), now);
				}
			} else {
				ability.onTick(instance.context());
				if (instance.activeExpiryTick() >= 0 && now >= instance.activeExpiryTick()) {
					deactivate(caster, ability, instance, now);
				}
			}
		}
	}

	/** Call from your damage event handler (see AbilityEvents#onDamage) so that abilities with
	 *  interruptOnDamage(true) get cancelled the moment the caster takes damage. */
	public void onDamaged(LivingEntity caster) {
		Map<ResourceLocation, AbilityInstance> casterRunning = running.get(caster.getUUID());
		if (casterRunning == null)
			return;
		for (AbilityInstance instance : new ArrayList<>(casterRunning.values())) {
			Ability ability = instance.context().ability();
			if (instance.state() == AbilityInstance.State.CHARGING && ability.interruptsOnDamage()) {
				ability.onChargeInterrupted(instance.context());
				casterRunning.remove(ability.id());
			}
		}
	}

	// ================================================================== internals
	private void activateNow(LivingEntity caster, Ability ability, AbilityContext ctx, long now) {
		Map<ResourceLocation, AbilityInstance> casterRunning = running.computeIfAbsent(caster.getUUID(), k -> new ConcurrentHashMap<>());
		ability.onActivate(ctx);
		double durationTicks = ability.duration().resolve(ctx);
		if (ability.type() == AbilityType.TOGGLE) {
			casterRunning.put(ability.id(), new AbilityInstance(ctx, AbilityInstance.State.ACTIVE, now));
		} else if (durationTicks > 0) {
			AbilityInstance instance = new AbilityInstance(ctx, AbilityInstance.State.ACTIVE, now);
			instance.setActiveExpiryTick(now + (long) durationTicks);
			casterRunning.put(ability.id(), instance);
		} else {
			casterRunning.remove(ability.id());
			startCooldown(caster.getUUID(), ability, ctx, now);
		}
	}

	private void deactivate(LivingEntity caster, Ability ability, AbilityInstance instance, long now) {
		ability.onDeactivate(instance.context());
		Map<ResourceLocation, AbilityInstance> casterRunning = running.get(caster.getUUID());
		if (casterRunning != null)
			casterRunning.remove(ability.id());
		startCooldown(caster.getUUID(), ability, instance.context(), now);
	}

	private void interruptOrDeactivate(LivingEntity caster, Ability ability, AbilityInstance instance, long now) {
		if (instance.state() == AbilityInstance.State.CHARGING) {
			ability.onChargeInterrupted(instance.context());
		} else {
			deactivate(caster, ability, instance, now);
		}
	}

	private void startCooldown(UUID uuid, Ability ability, AbilityContext ctx, long now) {
		double cd = ability.cooldown().resolve(ctx);
		if (cd <= 0)
			return;
		cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(ability.id(), now + (long) cd);
	}

	private boolean isOnCooldown(UUID uuid, ResourceLocation id, long now) {
		Map<ResourceLocation, Long> casterCooldowns = cooldowns.get(uuid);
		if (casterCooldowns == null)
			return false;
		Long expiry = casterCooldowns.get(id);
		return expiry != null && now < expiry;
	}

	private AbilityInstance runningInstance(UUID uuid, ResourceLocation id) {
		Map<ResourceLocation, AbilityInstance> casterRunning = running.get(uuid);
		return casterRunning == null ? null : casterRunning.get(id);
	}

	// ================================================================== queries (for HUD/GUI use)
	public boolean isRunning(LivingEntity caster, ResourceLocation id) {
		return runningInstance(caster.getUUID(), id) != null;
	}

	public float chargeProgress(LivingEntity caster, ResourceLocation id) {
		AbilityInstance instance = runningInstance(caster.getUUID(), id);
		if (instance == null || instance.state() != AbilityInstance.State.CHARGING)
			return 0f;
		double required = instance.context().ability().chargeTime().resolve(instance.context());
		long elapsed = instance.ticksInState(caster.level().getGameTime());
		return required <= 0 ? 1f : (float) Math.min(1.0, elapsed / required);
	}

	/** Read-only-ish access to the AbilityContext of an in-progress cast (CHARGING or ACTIVE) -
	 *  lets a network handler add/remove targets (or otherwise inspect state) on a running
	 *  instance without AbilityManager needing bespoke per-feature methods for every case. See
	 *  TeleportNetwork#handleTogglePassenger for the motivating use: adding a passenger to an
	 *  Orb cast that's already charging. Empty if nothing's running for that (caster, id) pair. */
	public Optional<AbilityContext> runningContext(LivingEntity caster, ResourceLocation id) {
		AbilityInstance instance = runningInstance(caster.getUUID(), id);
		return instance == null ? Optional.empty() : Optional.of(instance.context());
	}

	/** Call when an entity is permanently gone (player logout, mob death/despawn) to avoid
	 *  leaking state. Mobs die/despawn far more often than players log out, so if you're using
	 *  this for NPCs, make sure AbilityEvents' death/removal hooks are actually wired up. */
	public void clearEntity(LivingEntity caster) {
		running.remove(caster.getUUID());
		cooldowns.remove(caster.getUUID());
	}

	public record ActivationResult(boolean succeeded, Component message) {
		public static ActivationResult success() {
			return new ActivationResult(true, null);
		}

		public static ActivationResult fail(Component message) {
			return new ActivationResult(false, message);
		}
	}
}
