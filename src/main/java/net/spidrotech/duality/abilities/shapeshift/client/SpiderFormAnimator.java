package net.spidrotech.duality.abilities.shapeshift.client;

import net.spidrotech.duality.abilities.demon.WallClimbing;
import net.spidrotech.duality.abilities.shapeshift.DualityShapeshiftForms;
import net.spidrotech.duality.abilities.shapeshift.Shapeshift;
import net.spidrotech.duality.abilities.vampire.client.JumpInputClient;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Works out which jump animation a player in demon spider form should be showing.
 *
 * <p>The spider entity gets this from its server-side state machine, but a player has no such
 * state - nothing on the server decides a player is "mid-leap". So this reconstructs it on the
 * client from what the client can see: whether the player is on the ground, which way they're
 * moving vertically, and - for the leap crouch - whether the local player is charging a leap.
 *
 * <p>The leap is what the crouch is for. Holding space to charge (JumpInputClient) holds the spider
 * at the bottom of jump_start; releasing plays the rest of it, the spring, as the leap fires. A
 * plain tap-jump has no charge to show, so it skips straight to the spring. Either way the spider
 * then floats in jump_idle and starts jump_land just before touching down so it lands on its feet.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class SpiderFormAnimator {
	/** What the spider is doing, animation-wise. */
	public enum Phase {
		GROUND, CROUCH, LAUNCH, AIRBORNE, LANDING, CLIMBING, ATTACK
	}

	/** Projectiles that make the spider form play its attack when cast - it's a spit, so spits. */
	private static final Set<ResourceLocation> SPIT_PROJECTILES = Set.of(ResourceLocation.fromNamespaceAndPath("duality", "web_spit"));

	/**
	 * Where the attack animation starts. Its first 0.2s is a wind-up before the head snaps forward
	 * at 0.35s - which suits the spider mob, whose spit fires on that snap. A player's cast is
	 * instant, so the projectile is already out by the time the animation could begin; starting past
	 * the wind-up puts the snap a couple of ticks after the shot instead of well after it.
	 */
	public static final int ATTACK_START_MS = 250;

	/** attack is 0.7s long. */
	public static final int ATTACK_END_MS = 700;
	private static final int ATTACK_TICKS = (ATTACK_END_MS - ATTACK_START_MS) / 50;

	/** One player's current phase and the tick it began on - the model times its keyframes off that tick. */
	public static final class State {
		private Phase phase = Phase.GROUND;
		private int phaseStartTick;
		private float climbCycle;
		private float climbCycleO;

		public Phase phase() {
			return this.phase;
		}

		public int phaseStartTick() {
			return this.phaseStartTick;
		}

		/**
		 * How far through the climb animation the legs are, in ticks of full-speed climbing. It only
		 * advances as the player actually moves, so the legs cycle while climbing and freeze while
		 * clinging - rather than running on a clock regardless of what the player is doing.
		 */
		public float climbCycle(float partialTick) {
			return Mth.lerp(partialTick, this.climbCycleO, this.climbCycle);
		}

		private void enter(Phase next, int tick) {
			if (this.phase != next)
				this.restart(next, tick);
		}

		/** Like enter, but starts the phase over even if it's already running - a second spit mid-attack replays it. */
		private void restart(Phase next, int tick) {
			this.phase = next;
			this.phaseStartTick = tick;
		}
	}

	/**
	 * The deepest point of the crouch in jump_start (the body bottoms out at 0.2s, then springs up
	 * by 0.32s). Charging holds here; launching plays on from here.
	 */
	public static final int CROUCH_HOLD_MS = 200;

	/** jump_start is 0.5s long; the spring is the part after the crouch. */
	public static final int JUMP_START_END_MS = 500;
	private static final int LAUNCH_TICKS = (JUMP_START_END_MS - CROUCH_HOLD_MS) / 50;

	/** jump_land is 0.6s. Held in full before handing back to walk/idle. */
	public static final int LAND_MS = 600;
	private static final int LAND_TICKS = LAND_MS / 50;

	/**
	 * How far ahead of touchdown jump_land starts. Same value and reasoning as the spider entity:
	 * starting it 8 ticks early has the legs reaching out on contact, so it lands on its feet.
	 */
	private static final int LAND_ANIM_LEAD_TICKS = 8;

	/** Vertical speed per tick that counts as taking off, rather than stepping up a block. */
	private static final double TAKEOFF_SPEED = 0.05;

	/** Downward speed per tick that counts as falling, rather than walking down a slope. */
	private static final double FALLING_SPEED = -0.1;

	/** Weak keys, so a player who leaves or unloads drops out without any cleanup code. */
	private static final Map<Player, State> STATES = new WeakHashMap<>();

	private SpiderFormAnimator() {
	}

	/** The phase to animate this player with, or null if they aren't in a form this drives. */
	public static State state(Player player) {
		return STATES.get(player);
	}

	/** Called by ProjectileCastNetwork when a player casts a projectile. */
	public static void onProjectileCast(Player player, ResourceLocation projectileId) {
		if (!SPIT_PROJECTILES.contains(projectileId) || !Shapeshift.isIn(player, DualityShapeshiftForms.DEMON_SPIDER))
			return;
		STATES.computeIfAbsent(player, p -> new State()).restart(Phase.ATTACK, player.tickCount);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.isPaused()) {
			if (mc.level == null)
				STATES.clear();
			return;
		}
		for (Player player : mc.level.players()) {
			if (Shapeshift.isIn(player, DualityShapeshiftForms.DEMON_SPIDER))
				tick(player, STATES.computeIfAbsent(player, p -> new State()));
			else
				STATES.remove(player);
		}
	}

	private static void tick(Player player, State state) {
		int now = player.tickCount;
		boolean grounded = player.onGround();
		// Position delta rather than getDeltaMovement: other players' velocity isn't synced to this
		// client, but their position is, so this reads the same for everyone.
		double vy = player.getY() - player.yo;
		boolean charging = isChargingLeap(player);
		int inPhase = now - state.phaseStartTick();

		// Wall climbing overrides every jump phase: whatever the spider was doing, once it's on a
		// wall it's climbing. Its legs are driven by distance moved, vertical included, because the
		// player's own walk animation only counts horizontal movement - which is almost none while
		// going straight up a wall.
		state.climbCycleO = state.climbCycle;
		boolean climbing = WallClimbing.isClimbing(player);

		// An attack plays out in full over whatever else is going on - a spit from a wall or mid-air
		// still gets its lunge. The renderer keeps the model tipped onto the wall separately.
		if (state.phase() == Phase.ATTACK) {
			if (inPhase < ATTACK_TICKS)
				return;
			state.enter(climbing ? Phase.CLIMBING : grounded ? Phase.GROUND : Phase.AIRBORNE, now);
			return;
		}

		if (climbing) {
			double dx = player.getX() - player.xo;
			double dz = player.getZ() - player.zo;
			state.climbCycle += (float) (Math.sqrt(dx * dx + vy * vy + dz * dz) / WallClimbing.CLIMB_SPEED);
			state.enter(Phase.CLIMBING, now);
			return;
		}
		if (state.phase() == Phase.CLIMBING) {
			state.enter(grounded ? Phase.GROUND : Phase.AIRBORNE, now);
			return;
		}

		switch (state.phase()) {
			case GROUND -> {
				if (charging && grounded)
					state.enter(Phase.CROUCH, now);
				else if (!grounded && vy > TAKEOFF_SPEED)
					state.enter(Phase.LAUNCH, now);
				else if (!grounded && vy < FALLING_SPEED)
					state.enter(Phase.AIRBORNE, now);
			}
			case CROUCH -> {
				if (!charging)
					// Released. The leap fires server-side and its velocity arrives a tick or two
					// later, so spring now rather than waiting to see the player leave the ground -
					// waiting would pop the spider upright first and then launch it.
					state.enter(Phase.LAUNCH, now);
				else if (!grounded)
					state.enter(Phase.AIRBORNE, now);
			}
			case LAUNCH -> {
				// If nothing actually launched them (a charge released too early to fire), they're
				// still on the ground when the spring finishes and simply return to standing.
				if (inPhase >= LAUNCH_TICKS)
					state.enter(grounded ? Phase.GROUND : Phase.AIRBORNE, now);
			}
			case AIRBORNE -> {
				if (grounded || (vy < 0 && isAboutToLand(player, vy)))
					state.enter(Phase.LANDING, now);
			}
			case LANDING -> {
				if (!grounded && vy > TAKEOFF_SPEED)
					state.enter(Phase.LAUNCH, now); // jumped again straight off the landing - a bhop
				else if (grounded && inPhase >= LAND_TICKS)
					state.enter(Phase.GROUND, now);
			}
			case CLIMBING, ATTACK -> {
				// Both handled above, before this switch.
			}
		}
	}

	/**
	 * Whether this player is holding a leap charge. Only knowable for the local player: the charge
	 * is tracked by their own JumpInputClient and by the server's AbilityManager, and neither is
	 * sent to other clients. Other players' leaps still animate - they just skip the crouch and go
	 * straight to the spring, the same as a plain jump.
	 */
	private static boolean isChargingLeap(Player player) {
		return player == Minecraft.getInstance().player && JumpInputClient.heldChargeTicks() > 0;
	}

	/** Casts straight down as far as the player will fall in LAND_ANIM_LEAD_TICKS at their current speed. */
	private static boolean isAboutToLand(Player player, double vy) {
		double lookAhead = Math.max(0.5, -vy * LAND_ANIM_LEAD_TICKS);
		Vec3 from = player.position();
		return player.level().clip(new ClipContext(from, from.add(0, -lookAhead, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
				.getType() != HitResult.Type.MISS;
	}
}
