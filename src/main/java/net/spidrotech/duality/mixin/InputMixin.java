package net.spidrotech.duality.mixin;

import net.spidrotech.duality.abilities.vampire.VampireMode;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.player.Input;
import net.minecraft.client.Minecraft;

/**
 * Half of "vampires can sprint backwards" - see LocalPlayerMixin for the other half (the sprint
 * START check; this one covers the ongoing-sprint CANCEL check and the underwater start check,
 * both of which read hasForwardImpulse() directly rather than going through a method we can
 * override on LocalPlayer alone).
 *
 * Only patches the LOCAL player's own Input - Input has no back-reference to its owner, so this
 * checks Minecraft.getInstance().player.input == self rather than assuming every Input belongs to
 * a vampire in mobility mode.
 */
@Mixin(Input.class)
public abstract class InputMixin {

	@Inject(method = "hasForwardImpulse", at = @At("HEAD"), cancellable = true)
	private void duality$allowBackwardImpulse(CallbackInfoReturnable<Boolean> callback) {
		Input self = (Input) (Object) this;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.input == self && VampireMode.isActive(mc.player) && Math.abs(self.forwardImpulse) > 1.0E-5F) {
			callback.setReturnValue(true);
		}
	}
}
