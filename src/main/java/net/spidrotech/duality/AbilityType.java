package net.spidrotech.duality;

/**
 * How an ability activates. These are orthogonal to duration/cooldown - an INSTANT ability can
 * still have a duration (e.g. an instant self-buff that lasts 10s), and a CHANNELED ability can
 * lead into a duration effect too (e.g. a long channel that then grants a buff for a while).
 */
public enum AbilityType {
	/** Fires immediately - no charge-up. */
	INSTANT,
	/** Requires charge-up time (Ability#chargeTime) to elapse before it activates. The caster is
	 *  vulnerable to interruption during this window (see Ability#interruptsOnDamage and
	 *  Ability#maintainConditions). */
	CHANNELED,
	/** Switches on and stays on until explicitly deactivated (pressing the key again, running out
	 *  of a resource via a failed maintain condition, etc). Ignores chargeTime/duration. */
	TOGGLE
}