package net.spidrotech.duality.skin;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

/**
 * One selectable thing in the skin builder - "Skeleton_Skull", "Long_Hair", "Vampire_Eyes".
 *
 * TEXTURES ARE FULL-CANVAS OVERLAYS, not cropped sprites: every subpart texture is a 64x64
 * RGBA PNG in the same layout as a player skin, mostly transparent, with pixels only where that
 * subpart actually draws. This is the single most important simplification in the whole system
 * - it means compositing is a straight alpha-blend of one full image over another with zero UV
 * math, zero placement metadata, and zero chance of a part landing on the wrong body face.
 * target() therefore does NOT control where the part draws; it controls slot exclusivity and
 * gives temp effects a region to aim at (see SkinPartTarget).
 *
 * SUBPARTS exist so one part can offer several independently tintable layers without becoming
 * several parts in the GUI. A hat is one entry the player picks; its crown and its trim are two
 * subparts, and only the trim is tintable, so the GUI shows exactly one color swatch. Eyes are
 * one entry with a fixed sclera subpart and a tintable pupil subpart. Subparts composite in
 * list order, first to last.
 *
 * OWNERSHIP: free() true means everyone has this and no bookkeeping happens. Otherwise the part
 * names an unlock() id, and a player owns it once they've been granted that id (see
 * SkinUnlocks). Unlock ids are shared between parts on purpose - tagging forty cosmetics with
 * "duality:vampire_line" turns a progression step into one grant instead of forty.
 *
 * Neither field is usually set per-part by hand: SkinOwnershipRules resolves both from glob
 * patterns at load time, so a server expresses its unlock scheme once rather than once per file.
 * The catalog is loaded server-side and synced, so nothing here is a client-supplied claim.
 */
public record SkinPart(String id, SkinPartTarget target, boolean free, Optional<String> unlock, String category, ResourceLocation icon, List<SubPart> subParts) {

	/**
	 * One tintable (or not) layer of a part.
	 *
	 * defaultColor is ARGB and is what the GUI seeds the swatch with. A tint of -1 in a loadout
	 * means "use defaultColor" rather than "no tint", so a part's look can be changed in the
	 * catalog later without rewriting every saved loadout that used the default.
	 *
	 * Tinting is a per-channel multiply against the texture's own RGB, so a subpart authored in
	 * white tints to exactly the chosen color while one authored with shading keeps its shading.
	 * Author tintable subparts in greyscale.
	 */
	public record SubPart(String id, ResourceLocation texture, boolean tintable, int defaultColor) {
		public static final StreamCodec<FriendlyByteBuf, SubPart> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, SubPart sub) -> {
			buf.writeUtf(sub.id());
			buf.writeResourceLocation(sub.texture());
			buf.writeBoolean(sub.tintable());
			buf.writeInt(sub.defaultColor());
		}, (FriendlyByteBuf buf) -> new SubPart(buf.readUtf(), buf.readResourceLocation(), buf.readBoolean(), buf.readInt()));
	}

	public static final StreamCodec<FriendlyByteBuf, SkinPart> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, SkinPart part) -> {
		buf.writeUtf(part.id());
		buf.writeEnum(part.target());
		buf.writeBoolean(part.free());
		buf.writeOptional(part.unlock(), FriendlyByteBuf::writeUtf);
		buf.writeUtf(part.category());
		buf.writeResourceLocation(part.icon());
		buf.writeVarInt(part.subParts().size());
		for (SubPart sub : part.subParts()) {
			SubPart.STREAM_CODEC.encode(buf, sub);
		}
	}, (FriendlyByteBuf buf) -> {
		String id = buf.readUtf();
		SkinPartTarget target = buf.readEnum(SkinPartTarget.class);
		boolean free = buf.readBoolean();
		Optional<String> unlock = buf.readOptional(FriendlyByteBuf::readUtf);
		String category = buf.readUtf();
		ResourceLocation icon = buf.readResourceLocation();
		int count = buf.readVarInt();
		List<SubPart> subs = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			subs.add(SubPart.STREAM_CODEC.decode(buf));
		}
		return new SkinPart(id, target, free, unlock, category, icon, List.copyOf(subs));
	});

	/** Number of tint swatches the GUI should show for this part. */
	public int tintableCount() {
		int n = 0;
		for (SubPart sub : subParts) {
			if (sub.tintable())
				n++;
		}
		return n;
	}
}
