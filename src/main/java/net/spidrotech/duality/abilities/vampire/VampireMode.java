package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.skin.SkinTempModify;
import net.spidrotech.duality.skin.SkinPartTarget;
import net.spidrotech.duality.skin.SkinEffects;
import net.spidrotech.duality.abilities.shapeshift.Shapeshift;
import net.spidrotech.duality.abilities.AbilityToggles;
import net.spidrotech.duality.AbilityValue;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.AbilityManager;
import net.spidrotech.duality.Ability;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.List;

/**
 * Vampire mode vs humanized. Humanize isn't a separate ability - it's simply this mode being off.
 * Selecting the vampire entry in the abilities radial toggles it (AbilitiesRadialHandler).
 *
 * Switching ON checks access to each AUTO_ABILITIES entry (its cast conditions - rank gates today)
 * and toggles on whichever the vampire qualifies for; that's what makes leap/dash/deflect "just
 * work" in vampire mode with no casting. Switching OFF turns them all back off and ends any vampire
 * shapeshift form. The toggles live in AbilityToggles, so the state survives relogs and death.
 *
 * The mode also drives the vampire skin look (SKIN_KEY) - on with the mode, off when humanized.
 *
 * Concealment: while humanized, detection of vampiric nature is resisted by rank (Thrall can't hide
 * at all, Queen hides 75% of the time). A future hunter/witch/spell system should read
 * concealmentEffectiveness/detects rather than re-deriving it.
 */
@EventBusSubscriber(modid = "duality")
public final class VampireMode {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "vampire_mode");
	/** Toggled on (if the vampire has access) whenever vampire mode turns on. */
	public static final List<ResourceLocation> AUTO_ABILITIES = List.of(LeapAbility.ID, DashAbility.ID, DeflectAbility.ID);
	/** SkinTempModify key for the vampire look - same recipe as /skin's vampire toggle (SkinAdminCommand). */
	public static final String SKIN_KEY = "vampire";
	private static final int VAMPIRE_PUPIL_COLOR = 0xFFCC0000;
	private static final double TOGGLE_COOLDOWN_TICKS = 20;
	// ---- tunable: concealment per rank while humanized (0..1) ----
	private static final double CONCEALMENT_PER_RANK = 0.25;

	private VampireMode() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.INSTANT) //
				.castCondition(VampireRank.THRALL.orAbove()) //
				.cooldown(AbilityValue.constant(TOGGLE_COOLDOWN_TICKS)) //
				.onActivate(ctx -> {
					if (ctx.caster() instanceof ServerPlayer player)
						setActive(player, !isActive(player));
				}).build();
	}

	/** Safe on either side. */
	public static boolean isActive(LivingEntity entity) {
		return AbilityToggles.isToggled(entity, ID);
	}

	public static void setActive(ServerPlayer player, boolean active) {
		AbilityToggles.update(player, toggled -> {
			if (active) {
				toggled.add(ID);
				applyAccess(player, toggled);
			} else {
				toggled.remove(ID);
				AUTO_ABILITIES.forEach(toggled::remove);
			}
		});
		syncSkin(player);
		if (!active)
			Shapeshift.revertIfNotAllowed(player); // humanizing ends vampire forms (e.g. bat) right away
	}

	/** Re-checks access to each auto ability without leaving vampire mode - call after a rank change,
	 *  so newly qualified abilities switch on and ones no longer qualified switch off. */
	public static void refreshAccess(ServerPlayer player) {
		if (isActive(player))
			AbilityToggles.update(player, toggled -> applyAccess(player, toggled));
	}

	private static void applyAccess(ServerPlayer player, Set<ResourceLocation> toggled) {
		for (ResourceLocation id : AUTO_ABILITIES) {
			if (AbilityManager.get().meetsCastConditions(player, id))
				toggled.add(id);
			else
				toggled.remove(id);
		}
	}

	/** Matches the vampire skin look to the mode. Only adds it when it isn't already there, so
	 *  re-asserting vampire mode doesn't replay the reveal animation. */
	public static void syncSkin(ServerPlayer player) {
		boolean hasLook = SkinTempModify.hasKey(player, SKIN_KEY);
		if (isActive(player) && !hasLook) {
			SkinTempModify.add(player).part(SkinPartTarget.EYES).effect(SkinEffects.VAMPIRIZE).key(SKIN_KEY);
			SkinTempModify.add(player).part(SkinPartTarget.PUPIL).color(VAMPIRE_PUPIL_COLOR).key(SKIN_KEY);
		} else if (!isActive(player) && hasLook) {
			SkinTempModify.removeKey(player, SKIN_KEY);
		}
	}

	// Vampire mode outlives relogs and death; make sure the look does too, whatever SkinManager keeps.
	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			syncSkin(player);
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			syncSkin(player);
	}

	/** 0 = fully detectable. Always 0 in vampire mode or for non-vampires. */
	public static double concealmentEffectiveness(LivingEntity entity) {
		if (!VampireRank.isVampire(entity) || isActive(entity))
			return 0.0;
		return VampireRank.fromAttribute(entity).stepsAboveThrall() * CONCEALMENT_PER_RANK;
	}

	/** For a future detector: true = sees through any concealment this roll. */
	public static boolean detects(LivingEntity entity, RandomSource random) {
		return random.nextDouble() >= concealmentEffectiveness(entity);
	}
}
