package net.spidrotech.duality.mana;

import net.spidrotech.duality.abilities.vampire.VampireRank;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Map;
import java.util.Set;

/**
 * What each power costs to use, paid from Mana (which, for a vampire, is their blood).
 *
 * <p>Charged by AbilityManager#tryActivate once a cast passes its conditions: an instant power
 * when it fires, a channelled one (Orb) when it starts charging, a toggle only when it's switched
 * on. Only players pay - a demon spider spitting web isn't drawing on anyone's pool - and not in
 * creative.
 *
 * <p>Tune freely. A power not listed costs {@link #DEFAULT_COST}.
 */
public final class AbilityCosts {
	public static final double DEFAULT_COST = 10;

	/** Not magic - bodies doing what bodies do, however inhumanly fast. */
	private static final Set<String> PHYSICAL = Set.of("dash", "leap", "deflect");

	private static final Map<String, Double> COSTS = Map.ofEntries(Map.entry("orb_normal", 15.0), Map.entry("blink", 8.0), Map.entry("shimmer", 10.0),
			Map.entry("flame", 15.0), Map.entry("flight", 20.0), Map.entry("levitate", 10.0), Map.entry("anti_gravity", 15.0), Map.entry("vanish", 15.0),
			Map.entry("screech", 10.0), Map.entry("wall_climber", 10.0), Map.entry("shapeshift", 20.0), Map.entry("vampire_mode", 5.0) /* putting on the human disguise; letting the vampire out is free */, Map.entry("firebolt", 6.0),
			Map.entry("fireball_normal", 10.0), Map.entry("fireball_greater", 20.0), Map.entry("fireball_inferno", 35.0), Map.entry("lightning_hands_normal", 12.0),
			Map.entry("lightning_hands_demonic", 12.0), Map.entry("acid_spit", 8.0), Map.entry("web_spit", 6.0));

	private AbilityCosts() {
	}

	/** Paid only if they land: casting just checks the caster could pay, and the power itself calls
	 *  {@link #chargeOnHit} when it strikes something - so a Lightning Hands fired at nothing is free. */
	private static final Set<String> CHARGED_ON_HIT = Set.of("lightning_hands_normal", "lightning_hands_demonic");

	/** Takes a CHARGED_ON_HIT power's cost, now that it has hit. Players only, not in creative. */
	public static void chargeOnHit(LivingEntity caster, ResourceLocation abilityId) {
		if (caster instanceof Player player && !player.getAbilities().instabuild)
			Mana.drain(player, costOf(abilityId));
	}

	public static double costOf(ResourceLocation abilityId) {
		if (!"duality".equals(abilityId.getNamespace()))
			return DEFAULT_COST;
		String path = abilityId.getPath();
		if (PHYSICAL.contains(path))
			return 0;
		return COSTS.getOrDefault(path, DEFAULT_COST);
	}

	/**
	 * Takes the cost of casting this power from the caster, or refuses. Returns false - having
	 * taken nothing, and told the player why - if they can't afford it.
	 */
	public static boolean pay(LivingEntity caster, ResourceLocation abilityId) {
		if (!(caster instanceof Player player) || player.getAbilities().instabuild)
			return true;
		// Vampire mode toggles itself through this one cast, and the direction decides the price: the
		// vampire is what they really are, so letting it out is free; the human disguise is the
		// effort, and a hungry vampire can't manage it at all (see VampireHunger#loseDisguiseWhenHungry).
		if (abilityId.equals(net.spidrotech.duality.abilities.vampire.VampireMode.ID)) {
			if (!net.spidrotech.duality.abilities.vampire.VampireMode.isActive(player))
				return true;
			if (player instanceof ServerPlayer serverPlayer && net.spidrotech.duality.abilities.vampire.VampireHunger.tooHungryToHide(serverPlayer)) {
				serverPlayer.displayClientMessage(Component.literal("You're too hungry to pass for human. Feed first.").withStyle(ChatFormatting.DARK_RED), true);
				return false;
			}
		}
		double cost = costOf(abilityId);
		if (cost <= 0)
			return true;
		boolean onHit = "duality".equals(abilityId.getNamespace()) && CHARGED_ON_HIT.contains(abilityId.getPath());
		if (onHit ? Mana.current(player) >= cost : Mana.spend(player, cost))
			return true;
		if (player instanceof ServerPlayer serverPlayer) {
			String what = VampireRank.isVampire(player) ? "blood" : "mana";
			serverPlayer.displayClientMessage(Component.literal("Not enough " + what + " (" + Math.round(cost) + " needed).").withStyle(ChatFormatting.BLUE), true);
		}
		return false;
	}
}
