package net.spidrotech.duality.mixin;

import net.spidrotech.duality.abilities.vampire.VampireMode;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.player.LocalPlayer;

/**
 * Half of "vampires can sprint backwards" - see InputMixin for the other half.
 *
 * Vanilla's non-underwater sprint-start check is a direct field comparison, input.forwardImpulse
 * >= 0.8 - strictly forward, and not expressed via hasForwardImpulse() (which InputMixin patches),
 * so it needs its own override: same threshold, but on the absolute value, so holding sprint while
 * moving backward starts a sprint exactly as holding it while moving forward does. The underwater
 * branch already goes through hasForwardImpulse() and picks up InputMixin's fix for free.
 *
 * There's no NeoForge event fine-grained enough to hook this (it's inline in LocalPlayer#aiStep),
 * hence the mixin rather than an event listener like the rest of this ability.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

	@Inject(method = "hasEnoughImpulseToStartSprinting", at = @At("HEAD"), cancellable = true)
	private void duality$allowBackwardSprintStart(CallbackInfoReturnable<Boolean> callback) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!self.isUnderWater() && VampireMode.isActive(self) && Math.abs(self.input.forwardImpulse) >= 0.8f) {
			callback.setReturnValue(true);
		}
	}
}
