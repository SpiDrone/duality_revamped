package net.spidrotech.duality.abilities.shapeshift;

import net.spidrotech.duality.ModAttachments;
import net.spidrotech.duality.AbilityManager;
import net.spidrotech.duality.AbilityContext;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.Collections;

/**
 * Form registry, unlocked forms, and the one place that turns a player into / out of a form. Any
 * source of shapeshifting goes through here, so the effects (health cap, flight, restrictions,
 * FOV) stay identical no matter who triggered them.
 *
 * ================================================================== hooks for radials/procedures
 *   Shapeshift.canUse(entity, "bat")          client-safe - wedge display condition: unlocked AND
 *                                              the form's requirement passes right now
 *   Shapeshift.isIn(entity, "bat")            client-safe - e.g. highlight the current form
 *   Shapeshift.requestShift(player, "bat")    server - what picking a form should do. Picking the
 *                                              form you're already in turns you back
 *   Shapeshift.revert(player)                 server - back to normal
 *   Shapeshift.unlock / lock(player, id)      server - grant/remove a form
 * Form ids without a namespace default to "duality:". The forms radial wheel is already wired to
 * requestShift/revert - see ShapeshiftRadialHandler.
 *
 * The current form and unlocked forms live in persisted attachments, mirrored to the owning
 * client by ShapeshiftNetwork. The max-health modifier is permanent and saved with the player, so
 * the form has to survive a relog alongside it.
 *
 * Only one form at a time: applying a new form reverts the current one first.
 */
public final class Shapeshift {
	private static final Map<ResourceLocation, ShapeshiftForm> FORMS = new ConcurrentHashMap<>();
	private static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("duality", "shapeshift_health");

	private Shapeshift() {
	}

	// ================================================================== registry
	public static void registerForm(ShapeshiftForm form) {
		if (FORMS.putIfAbsent(form.id(), form) != null)
			throw new IllegalStateException("Duplicate shapeshift form id: " + form.id());
	}

	public static Optional<ShapeshiftForm> form(ResourceLocation id) {
		return Optional.ofNullable(FORMS.get(id));
	}

	public static Set<ResourceLocation> formIds() {
		return Collections.unmodifiableSet(FORMS.keySet());
	}

	/** "bat" -> duality:bat, "othermod:wolf" as-is. */
	public static Optional<ResourceLocation> parseFormId(String raw) {
		if (raw == null || raw.isBlank())
			return Optional.empty();
		String trimmed = raw.trim();
		return Optional.ofNullable(ResourceLocation.tryParse(trimmed.contains(":") ? trimmed : "duality:" + trimmed));
	}

	// ================================================================== queries (safe on either side)
	public static Optional<ShapeshiftForm> current(Entity entity) {
		if (!entity.hasData(ModAttachments.SHAPESHIFT_FORM))
			return Optional.empty();
		ResourceLocation id = ResourceLocation.tryParse(entity.getData(ModAttachments.SHAPESHIFT_FORM));
		return id == null ? Optional.empty() : form(id);
	}

	public static boolean isIn(Entity entity, ResourceLocation formId) {
		return current(entity).map(form -> form.id().equals(formId)).orElse(false);
	}

	public static boolean isIn(Entity entity, String formId) {
		return parseFormId(formId).map(id -> isIn(entity, id)).orElse(false);
	}

	public static Set<ResourceLocation> unlockedForms(Entity entity) {
		return Collections.unmodifiableSet(entity.getData(ModAttachments.UNLOCKED_FORMS));
	}

	public static boolean hasUnlocked(Entity entity, ResourceLocation formId) {
		return entity.hasData(ModAttachments.UNLOCKED_FORMS) && entity.getData(ModAttachments.UNLOCKED_FORMS).contains(formId);
	}

	/** Unlocked and the form's requirement passes right now - i.e. picking it would work (cooldown aside). */
	public static boolean canUse(Entity entity, ResourceLocation formId) {
		if (!(entity instanceof LivingEntity living))
			return false;
		return form(formId).map(form -> hasUnlocked(living, formId) && meetsRequirement(living, form)).orElse(false);
	}

	public static boolean canUse(Entity entity, String formId) {
		return parseFormId(formId).map(id -> canUse(entity, id)).orElse(false);
	}

	static boolean meetsRequirement(LivingEntity entity, ShapeshiftForm form) {
		return form.requirement().test(new AbilityContext(entity, AbilityManager.get().get(ShapeshiftAbility.ID).orElse(null)));
	}

	// ================================================================== server actions
	/** Goes through ShapeshiftAbility, so unlock/requirement/cooldown all apply; a failure reason is
	 *  shown on the action bar. */
	public static void requestShift(ServerPlayer player, String formId) {
		Optional<ResourceLocation> id = parseFormId(formId);
		AbilityManager.ActivationResult result = id.isEmpty() ? AbilityManager.ActivationResult.fail(Component.literal("Unknown form: " + formId))
				: AbilityManager.get().tryActivate(player, ShapeshiftAbility.ID, ctx -> ctx.set(ShapeshiftAbility.FORM_KEY, id.get()));
		if (!result.succeeded() && result.message() != null)
			player.displayClientMessage(result.message(), true);
	}

	public static void unlock(ServerPlayer player, ResourceLocation formId) {
		if (player.getData(ModAttachments.UNLOCKED_FORMS).add(formId))
			syncUnlocked(player);
	}

	public static void lock(ServerPlayer player, ResourceLocation formId) {
		if (player.getData(ModAttachments.UNLOCKED_FORMS).remove(formId)) {
			syncUnlocked(player);
			revertIfNotAllowed(player);
		}
	}

	public static void toggle(ServerPlayer player, ShapeshiftForm form) {
		if (isIn(player, form.id()))
			revert(player);
		else
			apply(player, form);
	}

	/** Applies a form unconditionally - callers wanting unlock/requirement checks use requestShift. */
	public static void apply(ServerPlayer player, ShapeshiftForm form) {
		removeEffects(player);
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null && form.maxHealthMultiplier() != 1.0) {
			// ADD_MULTIPLIED_TOTAL of (multiplier - 1) scales the final value - -0.9 leaves 10%.
			maxHealth.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER_ID, form.maxHealthMultiplier() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
		if (form.canFly()) {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
		player.setData(ModAttachments.SHAPESHIFT_FORM, form.id().toString());
		syncForm(player);
	}

	public static void revert(ServerPlayer player) {
		if (player.getData(ModAttachments.SHAPESHIFT_FORM).isEmpty())
			return;
		removeEffects(player);
		player.setData(ModAttachments.SHAPESHIFT_FORM, "");
		syncForm(player);
	}

	/** Ends the current form if it's no longer unlocked, its requirement fails, or it no longer
	 *  exists. Called on humanize, on lock, and once a second (ShapeshiftEvents). */
	public static void revertIfNotAllowed(ServerPlayer player) {
		if (player.getData(ModAttachments.SHAPESHIFT_FORM).isEmpty())
			return;
		Optional<ShapeshiftForm> form = current(player);
		if (form.isEmpty() || !hasUnlocked(player, form.get().id()) || !meetsRequirement(player, form.get()))
			revert(player);
	}

	// ================================================================== sync
	public static void syncAll(ServerPlayer player) {
		syncForm(player);
		syncUnlocked(player);
	}

	private static void syncForm(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new ShapeshiftNetwork.SyncFormPayload(player.getData(ModAttachments.SHAPESHIFT_FORM)));
	}

	private static void syncUnlocked(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new ShapeshiftNetwork.SyncUnlockedFormsPayload(List.copyOf(player.getData(ModAttachments.UNLOCKED_FORMS))));
	}

	/** Undoes whatever the CURRENT form did. An unknown id (form removed since the player saved)
	 *  is treated as if it flew, so nobody gets stranded with flight they shouldn't have. */
	private static void removeEffects(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null)
			maxHealth.removeModifier(HEALTH_MODIFIER_ID);
		boolean hadForm = !player.getData(ModAttachments.SHAPESHIFT_FORM).isEmpty();
		if (hadForm && current(player).map(ShapeshiftForm::canFly).orElse(true)) {
			// Reset to whatever the game mode allows rather than a remembered value - correct after a
			// relog, and a creative player keeps their own flight.
			player.gameMode.getGameModeForPlayer().updatePlayerAbilities(player.getAbilities());
			player.onUpdateAbilities();
		}
	}
}
