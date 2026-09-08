package net.spidrotech.duality;

import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.attachment.AttachmentType;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.ArrayList;

/**
 * Persistent (saved-to-disk) data for the power system - which ability ids a player has
 * permanently unlocked. Kept separate from AbilityManager, which only tracks transient runtime
 * state that doesn't need to survive a relog.
 *
 * Remember to register ATTACHMENT_TYPES to the mod event bus in your mod constructor:
 *     ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
 */
public final class ModAttachments {
	private static final String MOD_ID = "duality";
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<HashSet<ResourceLocation>>> UNLOCKED_ABILITIES = ATTACHMENT_TYPES.register("unlocked_abilities",
			() -> AttachmentType.<HashSet<ResourceLocation>>builder(() -> new HashSet<>()).serialize(ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new)).build());

	private ModAttachments() {
	}
}