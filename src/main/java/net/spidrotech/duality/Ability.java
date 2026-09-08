package net.spidrotech.duality;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.ArrayList;

/**
 * Defines a single power. One instance of this class is the shared *definition* of an ability -
 * its timings, conditions, and behavior. It is NOT per-player state; every player who has this
 * ability unlocked shares the same instance. Per-cast runtime data lives in {@link AbilityContext},
 * and is created fresh every time {@link AbilityManager} activates the ability.
 *
 * There are two ways to define an ability:
 *  1. {@link #builder(ResourceLocation, AbilityType)} - fluent, lambda-based. Use this for the vast
 *     majority of powers. It's the fast path for a roster of hundreds of abilities, since you don't
 *     need a new .java file per power.
 *  2. Subclass this directly - use this when a power needs real per-cast state machinery beyond what
 *     fits comfortably in lambdas (see OrbAbility, which tracks selected teleport passengers and
 *     recomputes its own charge time live as they're added).
 */
public abstract class Ability {
	private final ResourceLocation id;
	private final AbilityType type;
	private final List<AbilityCondition> castConditions = new ArrayList<>();
	private final List<AbilityCondition> maintainConditions = new ArrayList<>();
	private AbilityValue chargeTime = AbilityValue.constant(0);
	private AbilityValue duration = AbilityValue.constant(0);
	private AbilityValue cooldown = AbilityValue.constant(0);
	private boolean interruptOnDamage = false;

	protected Ability(ResourceLocation id, AbilityType type) {
		this.id = id;
		this.type = type;
	}

	public static AbilityBuilder builder(ResourceLocation id, AbilityType type) {
		return new AbilityBuilder(id, type);
	}

	// ---------------------------------------------------------------- accessors
	public final ResourceLocation id() {
		return id;
	}

	public final AbilityType type() {
		return type;
	}

	public final AbilityValue chargeTime() {
		return chargeTime;
	}

	public final AbilityValue duration() {
		return duration;
	}

	public final AbilityValue cooldown() {
		return cooldown;
	}

	public final boolean interruptsOnDamage() {
		return interruptOnDamage;
	}

	public final List<AbilityCondition> castConditions() {
		return castConditions;
	}

	public final List<AbilityCondition> maintainConditions() {
		return maintainConditions;
	}

	// ---------------------------------------------------------------- config (called from subclass ctors / AbilityBuilder)
	protected void setChargeTime(AbilityValue value) {
		this.chargeTime = value;
	}

	protected void setDuration(AbilityValue value) {
		this.duration = value;
	}

	protected void setCooldown(AbilityValue value) {
		this.cooldown = value;
	}

	protected void setInterruptOnDamage(boolean value) {
		this.interruptOnDamage = value;
	}

	protected void addCastCondition(AbilityCondition condition) {
		castConditions.add(condition);
	}

	protected void addMaintainCondition(AbilityCondition condition) {
		maintainConditions.add(condition);
	}

	// ---------------------------------------------------------------- lifecycle hooks (override what you need)
	/** Fired once, the tick a CHANNELED ability begins charging. */
	public void onChargeStart(AbilityContext ctx) {
	}

	/** Fired every tick while charging. progress is 0.0-1.0 and is recomputed live, so it reflects
	 *  any mid-charge changes (e.g. more targets added, stretching the required time back out). */
	public void onChargeTick(AbilityContext ctx, float progress) {
	}

	/** Fired if charging is interrupted: damage taken (if interruptOnDamage is set), a maintain
	 *  condition failing, or a manual cancel. */
	public void onChargeInterrupted(AbilityContext ctx) {
	}

	/** Fired when the ability actually goes off - after charge-up completes, or immediately for
	 *  INSTANT/TOGGLE types. This is where you do the actual effect (teleport, damage, buff, etc). */
	public void onActivate(AbilityContext ctx) {
	}

	/** Fired every tick while ACTIVE - i.e. for as long as a TOGGLE is on, or for the length of a
	 *  duration-based effect. Not called for one-shot INSTANT abilities with no duration. */
	public void onTick(AbilityContext ctx) {
	}

	/** Fired when a TOGGLE is switched off, or a duration effect naturally expires. */
	public void onDeactivate(AbilityContext ctx) {
	}

	@Override
	public String toString() {
		return "Ability[" + id + "]";
	}
}