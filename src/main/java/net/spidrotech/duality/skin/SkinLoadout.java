package net.spidrotech.duality.skin;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

/**
 * A player's BASE appearance - the thing the builder GUI edits, the thing that persists, and
 * the ONLY thing shapeshifting copies.
 *
 * That last point is the reason this type exists as its own record rather than being folded in
 * with temp modifications: "shapeshifting copies the base skin, not their temporary
 * augmentations" isn't implemented as a filter that has to remember to exclude things - it's
 * implemented by there being nothing else in here to copy. A disguise is literally
 * SkinManager#setDisguise(player, otherPlayersLoadout), and since this record is immutable the
 * copy can't drift from the original either. Temp mods live in SkinTempModify, keyed to the
 * player who OWNS the body, so a disguised vampire still has their own black eyes.
 *
 * baseTexture is an optional override for the bottom layer. Empty means "use this player's real
 * Mojang skin" (see ClientSkinCache's readback path); present means a builder-chosen base body
 * from the catalog, which is the faster and more reliable path.
 */
public record SkinLoadout(Optional<String> baseTexturePartId, List<Equipped> parts) {

	/** One equipped part plus its chosen tints, one per tintable subpart IN ORDER. A tint of -1
	 *  means "use that subpart's defaultColor" - see SkinPart.SubPart. Non-tintable subparts are
	 *  skipped when walking this list, so its length equals part.tintableCount(). */
	public record Equipped(String partId, List<Integer> tints) {
		public static final StreamCodec<FriendlyByteBuf, Equipped> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, Equipped eq) -> {
			buf.writeUtf(eq.partId());
			buf.writeVarInt(eq.tints().size());
			for (int tint : eq.tints()) {
				buf.writeInt(tint);
			}
		}, (FriendlyByteBuf buf) -> {
			String partId = buf.readUtf();
			int count = buf.readVarInt();
			List<Integer> tints = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				tints.add(buf.readInt());
			}
			return new Equipped(partId, List.copyOf(tints));
		});
	}

	public static final SkinLoadout EMPTY = new SkinLoadout(Optional.empty(), List.of());

	public static final StreamCodec<FriendlyByteBuf, SkinLoadout> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, SkinLoadout loadout) -> {
		buf.writeOptional(loadout.baseTexturePartId(), FriendlyByteBuf::writeUtf);
		buf.writeVarInt(loadout.parts().size());
		for (Equipped eq : loadout.parts()) {
			Equipped.STREAM_CODEC.encode(buf, eq);
		}
	}, (FriendlyByteBuf buf) -> {
		Optional<String> base = buf.readOptional(FriendlyByteBuf::readUtf);
		int count = buf.readVarInt();
		List<Equipped> parts = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			parts.add(Equipped.STREAM_CODEC.decode(buf));
		}
		return new SkinLoadout(base, List.copyOf(parts));
	});

	/** Equip, respecting slot exclusivity - anything already occupying an exclusive target is
	 *  dropped. Returns a new loadout; this record never mutates. Unknown part ids are returned
	 *  unchanged rather than throwing, since a catalog reload can retire a part underneath a
	 *  saved loadout. */
	public SkinLoadout with(Equipped equipped) {
		SkinPart incoming = SkinPartCatalog.get(equipped.partId());
		if (incoming == null)
			return this;
		List<Equipped> next = new ArrayList<>();
		for (Equipped existing : parts) {
			SkinPart part = SkinPartCatalog.get(existing.partId());
			boolean displaced = part != null && part.target() == incoming.target() && incoming.target().isExclusive();
			if (!displaced && !existing.partId().equals(equipped.partId())) {
				next.add(existing);
			}
		}
		next.add(equipped);
		return new SkinLoadout(baseTexturePartId, List.copyOf(next));
	}

	/** Swaps the bottom layer. Pass null to fall back to the player's real Mojang skin - see
	 *  client.ClientSkinCache's BASE IMAGE note for why both paths exist. */
	public SkinLoadout withBase(@javax.annotation.Nullable String basePartId) {
		return new SkinLoadout(Optional.ofNullable(basePartId), parts);
	}

	public SkinLoadout without(String partId) {
		List<Equipped> next = new ArrayList<>(parts);
		next.removeIf(eq -> eq.partId().equals(partId));
		return new SkinLoadout(baseTexturePartId, List.copyOf(next));
	}

	/** Composite order - see SkinPartTarget's RENDER ORDER note. Parts whose id is no longer in
	 *  the catalog are dropped here rather than at equip time, so a temporarily-missing catalog
	 *  entry doesn't permanently destroy someone's saved look. */
	public List<Equipped> sortedForRender() {
		List<Equipped> sorted = new ArrayList<>();
		for (Equipped eq : parts) {
			if (SkinPartCatalog.has(eq.partId()))
				sorted.add(eq);
		}
		sorted.sort((a, b) -> Integer.compare(SkinPartCatalog.get(a.partId()).target().order(), SkinPartCatalog.get(b.partId()).target().order()));
		return sorted;
	}
}
