package net.spidrotech.duality;

import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.attachment.AttachmentType;

import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;

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
	/** Abilities/modes currently switched on (vampire_mode, leap, dash, ...) - see abilities.AbilityToggles.
	 *  Copied on death so a vampire doesn't respawn humanized with everything switched off. */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<HashSet<ResourceLocation>>> TOGGLED_ABILITIES = ATTACHMENT_TYPES.register("toggled_abilities",
			() -> AttachmentType.<HashSet<ResourceLocation>>builder(() -> new HashSet<>()).serialize(ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new)).copyOnDeath().build());
	/** Current shapeshift form id, "" for none - see abilities.shapeshift.Shapeshift. Deliberately NOT
	 *  copied on death: the respawned player has none of the form's attribute modifiers either. */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> SHAPESHIFT_FORM = ATTACHMENT_TYPES.register("shapeshift_form",
			() -> AttachmentType.builder(() -> "").serialize(Codec.STRING, form -> !form.isEmpty()).build());
	/** 0 (normal gravity) .. 100 (true zero-g) - see abilities.demon.AntiGravityAbility. */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> GRAVITY_REDUCTION_PERCENT = ATTACHMENT_TYPES.register("gravity_reduction_percent",
			() -> AttachmentType.builder(() -> 100.0f).serialize(Codec.FLOAT, percent -> percent != 100.0f).build());
	/** Shapeshift form ids a player has unlocked - see abilities.shapeshift.Shapeshift#unlock. */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<HashSet<ResourceLocation>>> UNLOCKED_FORMS = ATTACHMENT_TYPES.register("unlocked_forms",
			() -> AttachmentType.<HashSet<ResourceLocation>>builder(() -> new HashSet<>()).serialize(ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new)).copyOnDeath().build());

	private ModAttachments() {
	}
}