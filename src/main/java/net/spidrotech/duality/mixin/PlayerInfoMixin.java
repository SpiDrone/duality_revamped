package net.spidrotech.duality.mixin;

import net.spidrotech.duality.skin.client.ClientSkinCache;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a composited skin show up in the tab list.
 *
 * <p>WHY A SECOND HOOK: the face in the player list is not drawn from the entity. PlayerTabOverlay
 * walks PlayerInfo objects - which exist for players whose entity isn't even loaded - and asks each
 * one for its skin. AbstractClientPlayerMixin never sees that call, so without this the body in the
 * world wears the character's face and the tab list still shows the Mojang skin.
 *
 * <p>The two hooks overlap rather than conflict: AbstractClientPlayer#getSkin() delegates here, so
 * for a loaded player this runs first and its result is what the other mixin then sees as "vanilla".
 * That is harmless because ClientSkinCache is keyed on the player's UUID and version, so the second
 * call is a cache hit that returns the same texture rather than compositing a composite. The entity
 * hook still earns its place: it covers the DefaultPlayerSkin path taken when a player has no
 * PlayerInfo at all.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void duality$compositeTabSkin(CallbackInfoReturnable<PlayerSkin> callback) {
		PlayerSkin vanilla = callback.getReturnValue();
		if (vanilla == null)
			return;
		GameProfile profile = ((PlayerInfo) (Object) this).getProfile();
		if (profile == null || profile.getId() == null)
			return;
		ResourceLocation composed = ClientSkinCache.textureFor(profile.getId(), profile.getName(), vanilla.texture());
		PlayerSkin.Model model = ClientSkinCache.bodyModelFor(profile.getId(), vanilla.model());
		if (composed == null && model == vanilla.model())
			return; // nothing of ours to apply - leave the real skin exactly as it was
		ResourceLocation texture = composed != null ? composed : vanilla.texture();
		callback.setReturnValue(new PlayerSkin(texture, vanilla.textureUrl(), vanilla.capeTexture(), vanilla.elytraTexture(), model, vanilla.secure()));
	}
}
