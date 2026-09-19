package net.spidrotech.duality.abilities.shapeshift.client;

import net.spidrotech.duality.client.model.animations.demonic_spiderAnimation;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.joml.Vector3f;

/**
 * The demonic spider as worn by a player.
 *
 * <p>Separate from creatures/DemonSpiderModel because that one animates off the spider entity's
 * synced AnimState, and a player has no such state - there is no server-side spider deciding it is
 * mid-leap. The equivalent is reconstructed client-side by SpiderFormAnimator; this just plays
 * whichever phase it reports. The geometry is the same Blockbench mesh either way.
 */
public class SpiderFormModel<T extends Entity> extends HierarchicalModel<T> {
	private static final Vector3f ANIMATION_VECTOR_CACHE = new Vector3f();
	private static final float WALK_MAX_SPEED = 2.5f;
	private static final float WALK_SCALE = 2.5f;

	private final ModelPart root;

	public SpiderFormModel(ModelPart bakedLayer) {
		this.root = bakedLayer.getChild("root");
	}

	@Override
	public ModelPart root() {
		return this.root;
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);

		SpiderFormAnimator.State state = entity instanceof Player player ? SpiderFormAnimator.state(player) : null;
		SpiderFormAnimator.Phase phase = state != null ? state.phase() : SpiderFormAnimator.Phase.GROUND;
		// KeyframeAnimations wants milliseconds; a tick is 50 of them. Timed off the tick the phase
		// began, so each animation starts from its own first frame.
		long msInPhase = state != null ? (long) ((ageInTicks - state.phaseStartTick()) * 50f) : 0L;

		switch (phase) {
			case GROUND -> {
				// No state machine starts and stops the idle here, so it simply loops off age.
				this.play(demonic_spiderAnimation.idle, (long) (ageInTicks * 50f));
				this.animateWalk(demonic_spiderAnimation.walk, limbSwing, limbSwingAmount, WALK_MAX_SPEED, WALK_SCALE);
			}
			// Sink into the crouch, then hold at its lowest point for as long as the charge lasts.
			case CROUCH -> this.play(demonic_spiderAnimation.jump_start, Math.min(msInPhase, SpiderFormAnimator.CROUCH_HOLD_MS));
			// Pick up exactly where the crouch left off and play out the spring.
			case LAUNCH -> this.play(demonic_spiderAnimation.jump_start, Math.min(SpiderFormAnimator.CROUCH_HOLD_MS + msInPhase, SpiderFormAnimator.JUMP_START_END_MS));
			case AIRBORNE -> this.play(demonic_spiderAnimation.jump_idle, msInPhase);
			case LANDING -> this.play(demonic_spiderAnimation.jump_land, Math.min(msInPhase, SpiderFormAnimator.LAND_MS));
			// One tick of full-speed climbing = one tick (50ms) of the loop, so at full speed it plays
			// in real time and it slows to a stop as the player does. The renderer tips the whole model
			// onto the wall separately (ShapeshiftFormRenderers).
			case CLIMBING -> this.play(demonic_spiderAnimation.climb, (long) (state.climbCycle(ageInTicks - entity.tickCount) * 50f));
			case ATTACK -> this.play(demonic_spiderAnimation.attack, Math.min(SpiderFormAnimator.ATTACK_START_MS + msInPhase, SpiderFormAnimator.ATTACK_END_MS));
		}
	}

	private void play(AnimationDefinition definition, long ms) {
		KeyframeAnimations.animate(this, definition, Math.max(0L, ms), 1f, ANIMATION_VECTOR_CACHE);
	}
}
