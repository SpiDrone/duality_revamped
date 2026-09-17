package net.spidrotech.duality.abilities.vampire.client;

import net.spidrotech.duality.abilities.vampire.LeapAbility;
import net.spidrotech.duality.abilities.vampire.JumpInputNetwork;
import net.spidrotech.duality.abilities.vampire.DashAbility;
import net.spidrotech.duality.abilities.AbilityToggles;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.Minecraft;

/**
 * The space-bar scheme while leap and/or dash are toggled on (i.e. in vampire mode):
 *
 *   tap                      - jump
 *   hold after a jump        - vanilla bhop, exactly as holding space always behaves
 *   hold from standing       - charge a leap (as long as you like), release to launch
 *   double tap               - dash where you're looking; hold shift to dash the opposite way
 *
 * How they're told apart:
 *  - A press that starts while standing (grounded longer than LANDING_GRACE_TICKS) holds back
 *    vanilla's jump. Released within TAP_TICKS it jumps on release; held longer it becomes a leap
 *    charge. That short hold-back is the unavoidable cost of "tap = jump" and "hold = charge"
 *    sharing one key - a tap jumps as its key comes up instead of as it goes down.
 *  - A press that starts in the air, or on the tick of landing, is left entirely to vanilla - that's
 *    the bhop case, and why a quick re-press on landing still hops instead of charging.
 *  - A second tap started within DOUBLE_TAP_WINDOW_TICKS of a tap's release dashes when released.
 *    Held instead of tapped, it's just a hold after a jump (bhop), not a dash.
 *
 * Hooked into MovementInputUpdateEvent, NOT a client tick event: vanilla reads the jump key inside
 * LocalPlayer#aiStep (input.tick) and this event fires right after, so changes to input.jumping here
 * are what the jump logic actually sees. A ClientTickEvent runs before input.tick and gets
 * overwritten.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class JumpInputClient {
	private static final int TAP_TICKS = 4;
	private static final int DOUBLE_TAP_WINDOW_TICKS = 6;
	private static final int LANDING_GRACE_TICKS = 3;

	private static boolean wasDown;
	private static int pressTicks;
	private static int groundedTicks;
	private static int ticksSinceRelease = DOUBLE_TAP_WINDOW_TICKS + 1;
	private static boolean lastReleaseWasTap;
	/** This press is holding back vanilla's jump: a tap jumps on release, a hold charges. */
	private static boolean deferred;
	private static boolean charging;
	/** This press began soon enough after a tap to be the second half of a double tap. */
	private static boolean secondTap;

	private JumpInputClient() {
	}

	@SubscribeEvent
	public static void onMovementInput(MovementInputUpdateEvent event) {
		Minecraft mc = Minecraft.getInstance();
		if (!(event.getEntity() instanceof LocalPlayer player) || player != mc.player)
			return;
		Input input = event.getInput();
		boolean down = mc.options.keyJump.isDown();
		groundedTicks = player.onGround() ? groundedTicks + 1 : 0;

		boolean leapOn = AbilityToggles.isToggled(player, LeapAbility.ID);
		boolean dashOn = AbilityToggles.isToggled(player, DashAbility.ID);
		if ((!leapOn && !dashOn) || jumpMeansSomethingElse(player)) {
			abort();
			wasDown = down;
			return;
		}

		if (down && !wasDown) {
			pressTicks = 0;
			secondTap = dashOn && lastReleaseWasTap && ticksSinceRelease <= DOUBLE_TAP_WINDOW_TICKS;
			deferred = leapOn && !secondTap && groundedTicks > LANDING_GRACE_TICKS;
		}

		if (down) {
			pressTicks++;
			if (deferred) {
				if (!charging && !player.onGround()) {
					deferred = false; // walked off a ledge before it became a charge - hand back to vanilla
				} else {
					input.jumping = false;
					if (!charging && pressTicks > TAP_TICKS) {
						charging = true;
						PacketDistributor.sendToServer(new JumpInputNetwork.StartLeapChargePayload());
					}
				}
			}
		} else if (wasDown) {
			boolean tap = pressTicks <= TAP_TICKS;
			if (charging) {
				charging = false;
				PacketDistributor.sendToServer(new JumpInputNetwork.ReleaseLeapChargePayload());
			} else if (deferred && tap) {
				input.jumping = true; // the jump this tap held back
			}
			if (secondTap && tap)
				PacketDistributor.sendToServer(new JumpInputNetwork.DashPayload(input.shiftKeyDown));
			// A dash uses up the double tap, so tap-tap-tap doesn't dash on both the 2nd and 3rd tap.
			lastReleaseWasTap = tap && !secondTap;
			ticksSinceRelease = 0;
			deferred = false;
			secondTap = false;
		} else if (ticksSinceRelease <= DOUBLE_TAP_WINDOW_TICKS) {
			ticksSinceRelease++;
		}
		wasDown = down;
	}

	/** Swimming, climbing, flying (creative or bat form), gliding, riding - space already has a job. */
	private static boolean jumpMeansSomethingElse(LocalPlayer player) {
		return player.getAbilities().flying || player.isPassenger() || player.isInWater() || player.isInLava() || player.onClimbable() || player.isFallFlying();
	}

	/** Ticks held past the tap threshold - i.e. how long the current leap charge has been
	 *  building, uncapped. 0 when not charging. Tracked here rather than read from the server
	 *  (AbilityManager is server-only, see its class doc) so a HUD bar responds with zero
	 *  latency; pair with LeapAbility#maxChargeTicks and clamp to it for display - see
	 *  procedures.ReturnJumpPowerProcedure. */
	public static double heldChargeTicks() {
		return charging ? Math.max(0, pressTicks - TAP_TICKS) : 0.0;
	}

	private static void abort() {
		if (charging)
			PacketDistributor.sendToServer(new JumpInputNetwork.ReleaseLeapChargePayload());
		charging = false;
		deferred = false;
		secondTap = false;
		lastReleaseWasTap = false;
		pressTicks = 0;
	}
}
