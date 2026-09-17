package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.abilities.shapeshift.ShapeshiftForm;
import net.spidrotech.duality.abilities.shapeshift.Shapeshift;
import net.spidrotech.duality.AbilityContext;
import net.spidrotech.duality.AbilityCondition;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.EnumMap;
import java.util.Collections;

/**
 * Vampire shapeshift forms - one bat form per rank from Fledgling up.
 *
 * Reaching a rank unlocks that rank's form automatically (checked once a second, since there's no
 * rank-change event). Lower-rank forms stay unlocked, so a Queen can still pick the Fledgling bat
 * from the forms radial. Each form requires its own minimum rank plus vampire mode, so humanizing
 * or dropping below a form's rank ends it (see ShapeshiftForm#requirement).
 *
 * Adding the Zealot/Queen bats: define the form below with batForm(...) and add it to
 * FORM_UNLOCKED_AT_RANK - registration and unlocking pick it up from there.
 */
@EventBusSubscriber(modid = "duality")
public final class VampireForms {
	private static final int UNLOCK_CHECK_INTERVAL_TICKS = 20;

	/** Fledgling - the vanilla bat: 10% max hearts, flight, no items/blocks, wider FOV. */
	public static final ShapeshiftForm BAT = batForm("bat", VampireRank.FLEDGLING, 0.10, 1.35f);
	// TODO: public static final ShapeshiftForm LORD_BAT = batForm("lord_bat", VampireRank.LORD, ...);
	// TODO: public static final ShapeshiftForm QUEEN_BAT = batForm("queen_bat", VampireRank.QUEEN, ...);

	/** The form each rank unlocks. Thrall has none. */
	public static final Map<VampireRank, ShapeshiftForm> FORM_UNLOCKED_AT_RANK;
	static {
		Map<VampireRank, ShapeshiftForm> forms = new EnumMap<>(VampireRank.class);
		forms.put(VampireRank.FLEDGLING, BAT);
		// forms.put(VampireRank.LORD, LORD_BAT);
		// forms.put(VampireRank.QUEEN, QUEEN_BAT);
		FORM_UNLOCKED_AT_RANK = Collections.unmodifiableMap(forms);
	}

	private VampireForms() {
	}

	public static void registerAll() {
		FORM_UNLOCKED_AT_RANK.values().forEach(Shapeshift::registerForm);
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || player.level().getGameTime() % UNLOCK_CHECK_INTERVAL_TICKS != 0)
			return;
		if (!VampireRank.isVampire(player))
			return;
		int rank = VampireRank.fromAttribute(player).level();
		FORM_UNLOCKED_AT_RANK.forEach((minRank, form) -> {
			if (rank >= minRank.level())
				Shapeshift.unlock(player, form.id()); // no-op (and no sync) if already unlocked
		});
	}

	/** Every vampire bat form flies and blocks items/blocks; they differ in rank, hearts and FOV. */
	private static ShapeshiftForm batForm(String name, VampireRank minRank, double maxHealthMultiplier, float fovMultiplier) {
		return new ShapeshiftForm(ResourceLocation.fromNamespaceAndPath("duality", name), maxHealthMultiplier, true, true, true, fovMultiplier, requirement(minRank));
	}

	/** minRank or above, and in vampire mode. */
	private static AbilityCondition requirement(VampireRank minRank) {
		return new AbilityCondition() {
			@Override
			public boolean test(AbilityContext ctx) {
				return minRank.isAtLeast(ctx.caster()) && VampireMode.isActive(ctx.caster());
			}

			@Override
			public Component failureMessage(AbilityContext ctx) {
				return !minRank.isAtLeast(ctx.caster()) ? Component.literal("You aren't a powerful enough vampire for that form yet.") : Component.literal("You can only take that form in vampire mode.");
			}
		};
	}
}
