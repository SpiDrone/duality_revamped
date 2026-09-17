package net.spidrotech.duality.creatures;

import net.spidrotech.duality.client.model.animations.demonic_spiderAnimation;
import net.spidrotech.duality.entity.SpiderQueenEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The model SpiderQueenRenderer actually renders with. It bakes the exact same layer as the
 * Blockbench export (Modeldemonic_spider.LAYER_LOCATION), so geometry and texture mapping are
 * identical - it just isn't that class.
 *
 * Why a separate class: MCreator regenerates Modeldemonic_spider from models/mojmap-1.21.x on
 * every build, and that export extends EntityModel, which cannot run keyframe animations. Any
 * edit made to it gets silently reverted and the spider freezes. Owning the animated model here
 * means re-exporting the Blockbench model only ever changes geometry, never behavior. It lives
 * outside client/model because MCreator deletes files there that aren't in its model list. Part names
 * are resolved by name at animation time, so new or renamed bones in a re-export just work as
 * long as demonic_spiderAnimation uses the same names.
 *
 * The spider never guesses its own state from movement - SpiderQueenEntity syncs an AnimState
 * and starts the matching AnimationState client-side; this reads whichever one is running. Walk
 * and climb are limb-swing driven instead, so their cycle speed follows actual movement.
 */
public class DemonSpiderModel<T extends SpiderQueenEntity> extends HierarchicalModel<T> {
	/** Caps how fast the walk cycle plays when the spider is at full tilt. */
	private static final float WALK_MAX_SPEED = 2.5f;

	/** Scales limb swing into animation weight, so a slow crawl moves its legs less. */
	private static final float WALK_SCALE = 2.5f;

	private final ModelPart root;
	private final ModelPart head;

	public DemonSpiderModel(ModelPart bakedLayer) {
		this.root = bakedLayer.getChild("root");
		this.head = this.root.getChild("body").getChild("head");
	}

	@Override
	public ModelPart root() {
		return this.root;
	}

	@Override
	public void setupAnim(T spider, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);

		SpiderQueenEntity.AnimState state = spider.getAnimState();
		switch (state) {
			case GROUND -> {
				this.animateWalk(demonic_spiderAnimation.walk, limbSwing, limbSwingAmount, WALK_MAX_SPEED, WALK_SCALE);
				this.animate(spider.idleAnimState, demonic_spiderAnimation.idle, ageInTicks);
			}
			case JUMP_WINDUP -> this.animate(spider.jumpStartAnimState, demonic_spiderAnimation.jump_start, ageInTicks);
			case AIRBORNE -> this.animate(spider.jumpIdleAnimState, demonic_spiderAnimation.jump_idle, ageInTicks);
			case LANDING -> this.animate(spider.jumpLandAnimState, demonic_spiderAnimation.jump_land, ageInTicks);
			case CLIMBING -> this.animateWalk(demonic_spiderAnimation.climb, limbSwing, limbSwingAmount, WALK_MAX_SPEED, WALK_SCALE);
			case SPIT -> this.animate(spider.spitAnimState, demonic_spiderAnimation.attack, ageInTicks);
		}

		// Head tracking only makes sense upright. On a wall the renderer has rotated the whole
		// spider, so a world-space yaw/pitch would swing the head somewhere arbitrary.
		if (state == SpiderQueenEntity.AnimState.GROUND || state == SpiderQueenEntity.AnimState.SPIT) {
			this.head.yRot += netHeadYaw * Mth.DEG_TO_RAD;
			this.head.xRot += headPitch * Mth.DEG_TO_RAD;
		}
	}
}
