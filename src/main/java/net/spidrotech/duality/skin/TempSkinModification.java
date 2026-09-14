package net.spidrotech.duality.skin;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One augmentation layered on top of a player's base SkinLoadout - the thing
 * SkinTempModify.add(...) builds and removeKey(...) throws away.
 *
 * KEY is the grouping handle, not a unique id. Vampirising someone adds several modifications
 * (black eye sockets, red irises, pale torso) that all carry key "vampire", and one
 * removeKey("vampire") drops the lot. Nothing stops two different systems from using the same
 * key - if they do, they're deliberately sharing a lifetime.
 *
 * startGameTime is stamped by the SERVER at creation and shipped to clients, so an effect's
 * animation progress is identical on every machine watching. A player who logs in mid-animation
 * joins it already in progress rather than restarting it, which is what you want for something
 * like a permanent transformation that happens to have a 1s reveal.
 *
 * color is ARGB; a color of 0 means "don't recolor", which is distinct from black
 * (0xFF000000). Recoloring preserves the region's existing luminance rather than flat-filling
 * it - see SkinCompositor#recolorRegion - so overwriting eye color keeps the pupil dark and the
 * highlight bright instead of turning the eye into a solid square.
 */
public record TempSkinModification(String key, SkinPartTarget target, int color, Optional<ResourceLocation> effect, long startGameTime) {

	public static final StreamCodec<FriendlyByteBuf, TempSkinModification> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, TempSkinModification mod) -> {
		buf.writeUtf(mod.key());
		buf.writeEnum(mod.target());
		buf.writeInt(mod.color());
		buf.writeOptional(mod.effect(), FriendlyByteBuf::writeResourceLocation);
		buf.writeVarLong(mod.startGameTime());
	}, (FriendlyByteBuf buf) -> new TempSkinModification(buf.readUtf(), buf.readEnum(SkinPartTarget.class), buf.readInt(), buf.readOptional(FriendlyByteBuf::readResourceLocation), buf.readVarLong()));

	public boolean hasColor() {
		return color != 0;
	}
}
