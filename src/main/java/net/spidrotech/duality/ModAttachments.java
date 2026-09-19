package net.spidrotech.duality;

import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;

import java.util.HashSet;
import java.util.ArrayList;

/**
 * Persistent (saved-to-disk) data for the power system - which ability ids a player has
 * permanently unlocked. Kept separate from AbilityManager, which only tracks transient runtime
 * state that doesn't need to survive a relog.
 *
 * These register from RegisterEvent, a mod-bus event, rather than through a DeferredRegister. A
 * DeferredRegister has to be handed the mod event bus from the mod constructor, and that lives in
 * MCreator-generated DualityMod - so it could only ever be wired by editing a regenerated file, and
 * any such edit is silently lost on the next build. Registration has to survive that, because
 * getData on an attachment type that never reached the registry throws.
 */
public final class ModAttachments {
	private static final String MOD_ID = "duality";

	public static final AttachmentType<HashSet<ResourceLocation>> UNLOCKED_ABILITIES = AttachmentType.<HashSet<ResourceLocation>>builder(() -> new HashSet<>())
			.serialize(ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new)).build();
	/** Abilities/modes currently switched on (vampire_mode, leap, dash, ...) - see abilities.AbilityToggles.
	 *  Copied on death so a vampire doesn't respawn humanized with everything switched off. */
	public static final AttachmentType<HashSet<ResourceLocation>> TOGGLED_ABILITIES = AttachmentType.<HashSet<ResourceLocation>>builder(() -> new HashSet<>())
			.serialize(ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new)).copyOnDeath().build();
	/** Current shapeshift form id, "" for none - see abilities.shapeshift.Shapeshift. Deliberately NOT
	 *  copied on death: the respawned player has none of the form's attribute modifiers either. */
	public static final AttachmentType<String> SHAPESHIFT_FORM = AttachmentType.builder(() -> "").serialize(Codec.STRING, form -> !form.isEmpty()).build();
	/** 0 (normal gravity) .. 100 (true zero-g) - see abilities.demon.AntiGravityAbility. */
	public static final AttachmentType<Float> GRAVITY_REDUCTION_PERCENT = AttachmentType.builder(() -> 100.0f)
			.serialize(Codec.FLOAT, percent -> percent != 100.0f).build();
	/** Shapeshift form ids a player has unlocked - see abilities.shapeshift.Shapeshift#unlock. */
	public static final AttachmentType<HashSet<ResourceLocation>> UNLOCKED_FORMS = AttachmentType.<HashSet<ResourceLocation>>builder(() -> new HashSet<>())
			.serialize(ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new)).copyOnDeath().build();

	private ModAttachments() {
	}

	@EventBusSubscriber(modid = MOD_ID)
	public static final class Registration {
		private Registration() {
		}

		@SubscribeEvent
		public static void onRegister(RegisterEvent event) {
			event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, helper -> {
				helper.register(id("unlocked_abilities"), UNLOCKED_ABILITIES);
				helper.register(id("toggled_abilities"), TOGGLED_ABILITIES);
				helper.register(id("shapeshift_form"), SHAPESHIFT_FORM);
				helper.register(id("gravity_reduction_percent"), GRAVITY_REDUCTION_PERCENT);
				helper.register(id("unlocked_forms"), UNLOCKED_FORMS);
			});
		}

		private static ResourceLocation id(String path) {
			return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
		}
	}
}