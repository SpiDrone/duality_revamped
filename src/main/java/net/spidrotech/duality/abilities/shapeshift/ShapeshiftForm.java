package net.spidrotech.duality.abilities.shapeshift;

import net.spidrotech.duality.AbilityCondition;

import net.minecraft.resources.ResourceLocation;

/**
 * One kind of shape - a bat, a wolf, a mist cloud, a polymorph spell's pig. Pure data: whatever
 * turns someone INTO the form (the shapeshift radial, a spell, a curse) goes through Shapeshift, so
 * the form doesn't know or care where it came from.
 *
 * @param maxHealthMultiplier 1.0 = unchanged, 0.1 = 10% of normal max hearts
 * @param canFly              grants vanilla creative-style flight while in this form
 * @param blocksItemUse       cancels right-clicking items and blocks
 * @param blocksBlockPlacing  cancels placing blocks
 * @param fovMultiplier       client FOV scale while in this form, 1.0 = unchanged
 * @param requirement         must pass to enter the form AND to stay in it - checked again every
 *                            second, so a form whose requirement stops holding (e.g. a vampire form
 *                            after humanizing) ends on its own. Use {@code ctx -> true} for none.
 */
public record ShapeshiftForm(ResourceLocation id, double maxHealthMultiplier, boolean canFly, boolean blocksItemUse, boolean blocksBlockPlacing, float fovMultiplier, AbilityCondition requirement) {
}
