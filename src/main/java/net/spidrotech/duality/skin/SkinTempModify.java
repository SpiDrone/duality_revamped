package net.spidrotech.duality.skin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * The front door for temporary skin augmentations. Server-side only on purpose: an augmentation
 * is a gameplay fact (you ARE vampirised), so it's applied here, stored in SkinManager, and
 * broadcast to everyone who can see the player. Clients never author these.
 *
 * USAGE - key(...) is the terminal call that commits, so a builder can never be left half-built
 * and silently do nothing. A "key" groups any number of separate add(...) calls, not just one -
 * see MULTI-TARGET below for why a single augmentation is often two of these:
 *
 *   SkinTempModify.add(player)
 *       .part(SkinPartTarget.EYES)
 *       .effect(SkinEffects.VAMPIRIZE)
 *       .key("vampire");
 *   SkinTempModify.add(player)
 *       .part(SkinPartTarget.PUPIL)
 *       .color(0xFFCC0000)
 *       .key("vampire");
 *
 *   SkinTempModify.removeKey(player, "vampire"); // drops every mod tagged "vampire" - both calls above
 *   SkinTempModify.removeKey(player);            // drops EVERY augmentation on this player
 *
 * Every field except key() is optional. A mod with only a color is a plain recolor with no
 * animation; a mod with only an effect animates without changing the base color; one with both
 * does the animation and lands on the new color. part() defaults to OVERLAY (whole canvas) if
 * you never call it, which is almost never what you want - call it.
 *
 * MULTI-TARGET: the vampire example above is split across two calls, both tagged "vampire",
 * because EYES and PUPIL composite at different points - see SkinPartTarget's PUPIL LAYERING
 * doc. Targeting the crack effect at EYES means it finishes drawing before PUPIL ever touches
 * the canvas, so it can't land on top of the pupil; targeting the color change at PUPIL means it
 * recolors exactly that player's actual pupil pixels (whatever shape they are) instead of a
 * fixed box. One call with both .color() and .effect() is still fine when a single target is
 * genuinely all an augmentation needs to touch.
 *
 * ORDERING: within one target, mods apply in the order they were added, right after that
 * target's own parts finish compositing - not after the whole loadout. See SkinCompositor's
 * class doc INTERLEAVED COMPOSITING. Two mods on the same target stack, later over earlier.
 */
public final class SkinTempModify {
	private SkinTempModify() {
	}

	public static Builder add(ServerPlayer player) {
		return new Builder(player);
	}

	/** Removes every modification carrying this key. No-op if none do. */
	public static void removeKey(ServerPlayer player, String key) {
		SkinManager.get().removeTempModifications(player, key);
	}

	/** No key given - removes ALL augmentations from this player, leaving the base loadout
	 *  untouched. This is the "clean slate" call for respawns, ability cancels, and debug. */
	public static void removeKey(ServerPlayer player) {
		SkinManager.get().clearTempModifications(player);
	}

	public static boolean hasKey(ServerPlayer player, String key) {
		return SkinManager.get().tempModifications(player).stream().anyMatch(mod -> mod.key().equals(key));
	}

	public static final class Builder {
		private final ServerPlayer player;
		private SkinPartTarget target = SkinPartTarget.OVERLAY;
		private int color = 0;
		private Optional<ResourceLocation> effect = Optional.empty();

		private Builder(ServerPlayer player) {
			this.player = player;
		}

		/** Which region this modification touches - see SkinPartTarget. Named part() rather
		 *  than target() to read naturally at the call site. */
		public Builder part(SkinPartTarget value) {
			this.target = value;
			return this;
		}

		/** ARGB. Overwrites the region's color while preserving its luminance, so eyes stay
		 *  eye-shaped. Pass 0 (or don't call this) for no recolor. */
		public Builder color(int argb) {
			this.color = argb;
			return this;
		}

		public Builder effect(ResourceLocation effectId) {
			this.effect = Optional.ofNullable(effectId);
			return this;
		}

		/** TERMINAL - commits the modification under this key and syncs it. */
		public void key(String key) {
			SkinManager.get().addTempModification(player, new TempSkinModification(key, target, color, effect, player.level().getGameTime()));
		}
	}
}
