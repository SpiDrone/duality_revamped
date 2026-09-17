package net.spidrotech.duality.abilities;

import net.spidrotech.duality.ModAttachments;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.Collections;

/**
 * The toggledAbilities list - which abilities/modes an entity currently has switched on.
 *
 * Separate from AbilityManager on purpose: AbilityManager's running state is transient and
 * server-only, while this is persisted (ModAttachments.TOGGLED_ABILITIES - survives relog and
 * death) and mirrored to the owning client (AbilityToggleNetwork), so client input code can ask
 * "is leap on?" without a round trip.
 *
 * A toggle can be a mode (vampire_mode) or an ability that a mode switched on (leap, dash,
 * deflect). What being toggled MEANS is up to whoever reads it.
 */
public final class AbilityToggles {
	private AbilityToggles() {
	}

	/** Safe on either side - the client copy is kept in sync by AbilityToggleNetwork. */
	public static boolean isToggled(LivingEntity entity, ResourceLocation id) {
		return entity.hasData(ModAttachments.TOGGLED_ABILITIES) && entity.getData(ModAttachments.TOGGLED_ABILITIES).contains(id);
	}

	public static Set<ResourceLocation> all(LivingEntity entity) {
		return Collections.unmodifiableSet(entity.getData(ModAttachments.TOGGLED_ABILITIES));
	}

	public static void set(ServerPlayer player, ResourceLocation id, boolean on) {
		update(player, toggled -> {
			if (on)
				toggled.add(id);
			else
				toggled.remove(id);
		});
	}

	/** Batch edit - sends one sync packet however many toggles change, and none if nothing did. */
	public static void update(ServerPlayer player, Consumer<Set<ResourceLocation>> edit) {
		HashSet<ResourceLocation> toggled = player.getData(ModAttachments.TOGGLED_ABILITIES);
		HashSet<ResourceLocation> before = new HashSet<>(toggled);
		edit.accept(toggled);
		if (!toggled.equals(before)) {
			player.setData(ModAttachments.TOGGLED_ABILITIES, toggled);
			sync(player);
		}
	}

	public static void sync(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new AbilityToggleNetwork.SyncTogglesPayload(List.copyOf(player.getData(ModAttachments.TOGGLED_ABILITIES))));
	}
}
