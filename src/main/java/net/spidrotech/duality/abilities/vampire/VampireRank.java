package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.init.DualityModAttributes;
import net.spidrotech.duality.AbilityCondition;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;

/**
 * The four vampire tiers, driven by DualityModAttributes.VAMPIRE_RANK (a 0-4 attribute, same
 * pattern as ORBING_PROFICIENCY). Unlike Orb - one ability whose behavior scales with a
 * proficiency level - each vampire rank unlocks genuinely distinct abilities, so rank is a cast
 * condition on the ability itself (see {@link #orAbove}) rather than an internal branch.
 *
 * Attribute value 0 is deliberately NOT a rank - it's the baseline every player starts at,
 * vampire or not (see isVampire's doc for why 0 alone can't mean anything). Levels start at 1:
 *
 * THRALL (1)   - mindless, no free will. Reflexive/feral powers only.
 * FLEDGLING (2) - newly turned, real agency returns.
 * LORD (3)     - established predator.
 * QUEEN (4)    - matriarch. Can sire new Thralls, closing the loop back to level 1.
 *
 * Use level() rather than ordinal() anywhere the numeric value matters (scaling formulas, the
 * attribute itself) - ordinal() would silently be off by one against the attribute's own numbers.
 */
public enum VampireRank {
	THRALL(1), FLEDGLING(2), LORD(3), QUEEN(4);

	private final int level;

	VampireRank(int level) {
		this.level = level;
	}

	/** The attribute's own number for this rank (1-4) - see class doc for why this isn't ordinal(). */
	public int level() {
		return level;
	}

	/** 0 at Thrall .. 3 at Queen - the "steps above the floor" every per-rank scaling formula in
	 *  the vampire package multiplies its per-rank increment by. */
	public int stepsAboveThrall() {
		return level - THRALL.level;
	}

	/** Reads the caster's VAMPIRE_RANK attribute and clamps it to a valid tier - defensive
	 *  against an attribute value that's out of range (mod removed mid-playthrough, an
	 *  unturned player's default 0, etc). A non-vampire clamping to THRALL is harmless: every
	 *  caller that matters also gates on isVampire separately (see orAbove). */
	public static VampireRank fromAttribute(LivingEntity caster) {
		int raw = (int) Math.round(caster.getAttributeValue(DualityModAttributes.VAMPIRE_RANK));
		int clamped = Math.max(THRALL.level, Math.min(QUEEN.level, raw));
		return values()[clamped - THRALL.level];
	}

	public boolean isAtLeast(LivingEntity caster) {
		return isVampire(caster) && fromAttribute(caster).level() >= this.level();
	}

	/**
	 * Whether this entity is a vampire at all. VAMPIRE_RANK alone can't answer that - it's
	 * attached to every player (default 0), so 0 is ambiguous between "never touched this
	 * system" and an actual state; and even a nonzero value doesn't rule out some other system
	 * having bumped the attribute for an unrelated reason. Until there's a real race/class
	 * field, this reuses the same EquippedAbilities tag convention every RetXProcedure already
	 * checks, just for a race marker ("vampire") instead of a specific power id. If a proper
	 * race system lands later, this is the one place to repoint.
	 */
	public static boolean isVampire(LivingEntity caster) {
		if (!(caster instanceof Player player))
			return false;
		return player.getData(DualityModVariables.PLAYER_VARIABLES).EquippedAbilities.contains("vampire");
	}

	/** Cast condition factory - e.g. addCastCondition(VampireRank.FLEDGLING.orAbove()) gates an
	 *  ability to Fledgling and up (and implicitly requires isVampire). Register each vampire
	 *  Ability with one of these; for a rank-independent vampire-only check, use THRALL.orAbove(). */
	public AbilityCondition orAbove() {
		return ((AbilityCondition) ctx -> isAtLeast(ctx.caster()))
				.withMessage(Component.literal("You aren't a powerful enough vampire for that yet."));
	}
}
