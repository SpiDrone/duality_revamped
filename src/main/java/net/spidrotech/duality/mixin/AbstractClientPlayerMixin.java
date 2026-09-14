package net.spidrotech.duality.mixin;

import net.spidrotech.duality.skin.client.ClientSkinCache;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.player.AbstractClientPlayer;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The single hook that makes composited skins actually appear.
 *
 * WHY HERE and not RenderPlayerEvent: getSkin() is the one place every consumer of a player's
 * appearance goes through - the body renderer, the second (hat/jacket) layer, first-person
 * hands, the skull item, the player list, mods that draw a player preview. Overriding one
 * method means all of those inherit composited skins for free and stay correct if any of them
 * changes. Intercepting the render event instead would mean reimplementing player rendering
 * and would still miss the hands and the GUI.
 *
 * CHEAPNESS MATTERS: this runs several times per player per frame. ClientSkinCache#textureFor
 * is built around that - its hit path is a map lookup and two int comparisons, and it returns
 * null (leaving the vanilla PlayerSkin untouched) for any player with nothing to composite, so
 * a server where nobody uses the builder pays almost nothing.
 *
 * Cape and elytra textures are passed through deliberately - those are separate textures with
 * their own UV layout and are not part of the 64x64 skin canvas.
 *
 * TODO: verify PlayerSkin's component list against your exact 1.21.1 mappings. It is
 * (texture, textureUrl, capeTexture, elytraTexture, model, secure) as of writing, but this
 * record gained and lost components across 1.20.x -> 1.21 and a mismatch here is a compile
 * error rather than anything subtle, so it's a cheap thing to check first.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void duality$compositeSkin(CallbackInfoReturnable<PlayerSkin> callback) {
		PlayerSkin vanilla = callback.getReturnValue();
		if (vanilla == null)
			return;
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		ResourceLocation composed = ClientSkinCache.textureFor(self, vanilla.texture());
		if (composed == null)
			return; // nothing to composite - leave the real skin exactly as it was
		callback.setReturnValue(new PlayerSkin(composed, vanilla.textureUrl(), vanilla.capeTexture(), vanilla.elytraTexture(), vanilla.model(), vanilla.secure()));
	}
}
