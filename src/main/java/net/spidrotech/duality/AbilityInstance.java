package net.spidrotech.duality;

/**
 * Tracks the state machine for one in-progress cast: either CHARGING (counting up to
 * Ability#chargeTime) or ACTIVE (a running TOGGLE, or a duration effect counting down to its
 * expiry tick). Owned and mutated exclusively by AbilityManager.
 */
public final class AbilityInstance {
	public enum State {
		CHARGING, ACTIVE
	}

	private final AbilityContext context;
	private State state;
	private long stateStartTick;
	private long activeExpiryTick = -1;

	public AbilityInstance(AbilityContext context, State state, long startTick) {
		this.context = context;
		this.state = state;
		this.stateStartTick = startTick;
	}

	public AbilityContext context() {
		return context;
	}

	public State state() {
		return state;
	}

	public long stateStartTick() {
		return stateStartTick;
	}

	public long activeExpiryTick() {
		return activeExpiryTick;
	}

	public void setState(State state, long tick) {
		this.state = state;
		this.stateStartTick = tick;
	}

	public void setActiveExpiryTick(long tick) {
		this.activeExpiryTick = tick;
	}

	public long ticksInState(long now) {
		return now - stateStartTick;
	}
}