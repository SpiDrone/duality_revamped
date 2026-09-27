package net.spidrotech.duality.item.athame;

import net.spidrotech.duality.village.entity.DualityNpcEntity;
import net.spidrotech.duality.village.NpcRecord;
import net.spidrotech.duality.mana.Mana;
import net.spidrotech.duality.init.DualityModItems;
import net.spidrotech.duality.init.DualityModAttributes;
import net.spidrotech.duality.charactercreation.SubraceDefinition;
import net.spidrotech.duality.charactercreation.SkillType;
import net.spidrotech.duality.charactercreation.RaceDefinition;
import net.spidrotech.duality.charactercreation.RaceCatalog;
import net.spidrotech.duality.charactercreation.CharacterProgress;
import net.spidrotech.duality.charactercreation.CharacterCreation;
import net.spidrotech.duality.charactercreation.CharacterAttributes;
import net.spidrotech.duality.abilities.vampire.VampireRank;
import net.spidrotech.duality.DualityDatabaseManager;

import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * Everything the athame does, hooked by event so none of it lives in the MCreator-generated
 * AthameItem (which would lose it on regeneration).
 *
 * <ul>
 * <li><b>Quick, light blade</b> - its attack speed is overridden to {@link #ATTACK_SPEED}.
 * <li><b>Every cut</b> draws blood into the glass hilt (one dose per hit, up to
 * {@link BloodSample#MAX_DOSES}, from one source at a time), and saps some of the target's mana
 * into that blood - more from a stronger source (see {@link #sapMana}).
 * <li><b>A killing blow</b> on a demon or witch takes one of their powers into the blade - and
 * away from them, if they were a player's character. A vampire who kills a person with it becomes
 * {@link CharacterProgress#SOULLESS}.
 * <li><b>Use</b> takes in the power in the blade, at the proficiency it was taken at. It does
 * nothing (and keeps the power) if you already have it at least that well. A human who takes in a
 * demon's power may become a Lower-Level demon; high Endurance makes that less likely.
 * <li><b>Sneak-use with a glass bottle in the other hand</b> swaps the hilt out: the blood goes
 * into the bottle as a blood vial, and the hilt comes back empty.
 * </ul>
 */
@EventBusSubscriber(modid = "duality")
public final class AthameEvents {
	/** Added to the player's base 4 attacks... per second: -1 leaves 3 swings a second (a sword gets 1.6). */
	public static final double ATTACK_SPEED = -1.0;
	/** Share of the target's mana pool each cut takes into the blood. */
	public static final double MANA_SAP_FRACTION = 0.06;
	/** The pool an NPC of each species gives as if it had (lower-case species), and for any other. */
	private static final Map<String, Double> NPC_MANA = Map.of("witch", 120.0, "vampire", 60.0, "thrall", 20.0);
	private static final double NPC_MANA_DEFAULT = 10;
	/** Creatures with magic in them. Any creature not listed gives none. */
	private static final Map<String, Double> CREATURE_MANA = Map.of("minecraft:witch", 80.0, "minecraft:evoker", 100.0, "minecraft:illusioner", 100.0,
			"duality:demon_spider", 40.0, "duality:spider_queen", 160.0);
	/** Chance a human is turned by a demon's power at Endurance 1, and how much each point above cuts it. */
	public static final double CONVERSION_BASE = 0.35, CONVERSION_PER_ENDURANCE = 0.06, CONVERSION_FLOOR = 0.05;

	private static final ResourceLocation BASE_ATTACK_SPEED_ID = ResourceLocation.withDefaultNamespace("base_attack_speed");
	private static final TagKey<EntityType<?>> BLOODLESS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("duality", "bloodless"));
	private static final TagKey<EntityType<?>> ACIDIC_BLOOD = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("duality", "acidic_blood"));

	/** Powers the blade can take from creatures and NPCs, which have no character sheet to take from. */
	private static final List<String> WITCH_POWERS = List.of("fireball_normal", "fireball_greater", "lightning_hands_normal", "shimmer");
	private static final List<String> VAMPIRE_POWERS = List.of("leap", "dash", "deflect");
	private static final List<String> THRALL_POWERS = List.of("leap");
	private static final List<String> SPIDER_POWERS = List.of("acid_spit", "web_spit");
	private static final Map<String, List<String>> CREATURE_WITCHES = Map.of("minecraft:witch", WITCH_POWERS);
	private static final Map<String, List<String>> CREATURE_DEMONS = Map.of("duality:demon_spider", SPIDER_POWERS, "duality:spider_queen", SPIDER_POWERS);

	private AthameEvents() {
	}

	private static boolean isAthame(ItemStack stack) {
		return stack.is(DualityModItems.ATHAME.get());
	}

	/** The athame this damage was dealt with, or null if it wasn't a melee hit from one. */
	private static ItemStack athameOf(DamageSource source) {
		if (!(source.getEntity() instanceof Player attacker) || source.getDirectEntity() != attacker)
			return null;
		ItemStack held = attacker.getMainHandItem();
		return isAthame(held) ? held : null;
	}

	/**
	 * Every athame starts out with an empty hilt ({@code "blood": "0"}) in its custom data.
	 * ReturnAthameBloodLevelProcedure - the MCreator procedure behind the model's fill state - reads
	 * the first character of that string, and crashed the game on a fresh athame (the creative tab's,
	 * a crafted one) that didn't have it yet. Done as a default component rather than in the
	 * procedure so it survives MCreator regenerating the procedure.
	 */
	@SubscribeEvent
	public static void onDefaultComponents(net.neoforged.neoforge.event.ModifyDefaultComponentsEvent event) {
		net.minecraft.nbt.CompoundTag empty = new net.minecraft.nbt.CompoundTag();
		empty.putString("blood", "0");
		event.modify(DualityModItems.ATHAME.get(), builder -> builder.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(empty)));
	}

	@SubscribeEvent
	public static void onAttributes(ItemAttributeModifierEvent event) {
		if (isAthame(event.getItemStack()))
			event.replaceModifier(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
	}

	// ---------------------------------------------------------------------------------- cuts
	@SubscribeEvent
	public static void onDamaged(LivingDamageEvent.Post event) {
		ItemStack athame = athameOf(event.getSource());
		LivingEntity target = event.getEntity();
		if (athame == null || event.getNewDamage() <= 0 || target.level().isClientSide())
			return;
		BloodSample blood = collectBlood(athame, target);
		if (blood == null)
			return;
		// The mana comes out with the blood and stays in it: whoever drinks the vial gets it back.
		double sapped = sapMana(target);
		if (sapped > 0)
			AthameData.setBlood(athame, blood.withMana(blood.mana() + sapped));
	}

	/**
	 * Draws this hit's blood into the hilt and returns what the hilt now holds, or null if the
	 * target doesn't bleed or someone else's blood already fills the hilt (it has to be bottled
	 * before it takes more). A full hilt of this target's blood still counts, so the cut keeps
	 * sapping mana into it.
	 */
	private static BloodSample collectBlood(ItemStack athame, LivingEntity target) {
		if (!bleeds(target))
			return null;
		BloodSample fresh = sampleOf(target);
		BloodSample held = AthameData.blood(athame);
		BloodSample result;
		if (held == null)
			result = fresh;
		else if (held.sameSource(fresh))
			result = held.withDoses(held.doses() + 1);
		else
			return null;
		AthameData.setBlood(athame, result);
		return result;
	}

	/**
	 * How much mana one cut takes: {@link #MANA_SAP_FRACTION} of the target's pool, so the stronger
	 * the source, the more each cut carries.
	 * <ul>
	 * <li>A player's character has a real pool (bigger with Attunement), and it's actually drained,
	 * so a drained victim gives nothing more.
	 * <li>NPCs and creatures have no pool; they give as if they had the size their kind suggests
	 * ({@link #NPC_MANA}, {@link #CREATURE_MANA}) and are never emptied.
	 * </ul>
	 */
	public static double sapMana(LivingEntity target) {
		return sapMana(target, 1.0);
	}

	/** As {@link #sapMana(LivingEntity)}, times {@code scale} - a vampire's bite takes more the harder it lands. */
	public static double sapMana(LivingEntity target, double scale) {
		if (Mana.hasMana(target))
			return Mana.drain(target, Mana.max(target) * MANA_SAP_FRACTION * scale);
		double pool;
		if (target instanceof DualityNpcEntity npc)
			pool = NPC_MANA.getOrDefault(npc.species().toLowerCase(), NPC_MANA_DEFAULT);
		else
			pool = CREATURE_MANA.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString(), 0.0);
		return pool * MANA_SAP_FRACTION * scale;
	}

	/** Whether this creature has blood to take at all - not skeletons, golems, slimes and the like. */
	public static boolean bleeds(LivingEntity target) {
		return !(target instanceof ArmorStand) && !target.getType().is(BLOODLESS);
	}

	/** A single dose of this target's blood, as the athame (or a vampire's bite) would take it. */
	public static BloodSample sampleOf(LivingEntity target) {
		double quality = target.getAttributes().hasAttribute(DualityModAttributes.BLOOD_QUALITY) ? target.getAttributeValue(DualityModAttributes.BLOOD_QUALITY) : 25;
		if (target instanceof ServerPlayer player) {
			JsonObject sheet = CharacterProgress.activeSheet(player);
			if (sheet == null)
				return new BloodSample(BloodSample.RED, BloodSample.Kind.CHARACTER, "player:" + player.getStringUUID(), player.getGameProfile().getName(), quality, 1, 0);
			boolean acidic = "scabber_demon".equals(CharacterCreation.subspeciesIdOf(sheet));
			String name = sheet.has("name") ? sheet.get("name").getAsString() : player.getGameProfile().getName();
			return new BloodSample(acidic ? BloodSample.GREEN : BloodSample.RED, BloodSample.Kind.CHARACTER, DualityDatabaseManager.getActiveCharacterId(player), name, quality, 1, 0);
		}
		boolean acidic = target.getType().is(ACIDIC_BLOOD);
		if (target instanceof DualityNpcEntity npc) {
			NpcRecord record = npc.record();
			String name = record != null ? record.name() : npc.getName().getString();
			return new BloodSample(acidic ? BloodSample.GREEN : BloodSample.RED, BloodSample.Kind.NPC, npc.npcId(), name, quality, 1, 0);
		}
		String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
		return new BloodSample(acidic ? BloodSample.GREEN : BloodSample.RED, BloodSample.Kind.CREATURE, typeId, target.getName().getString(), quality, 1, 0);
	}

	// --------------------------------------------------------------------------------- kills
	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		ItemStack athame = athameOf(event.getSource());
		LivingEntity victim = event.getEntity();
		if (athame == null || victim.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer killer))
			return;
		if (victim instanceof DualityNpcEntity && VampireRank.isVampire(killer) && CharacterProgress.grantProficiency(killer, CharacterProgress.SOULLESS))
			killer.sendSystemMessage(Component.literal("You took a life to feed. Something in you goes quiet for good. (Soulless)").withStyle(ChatFormatting.DARK_RED));
		StoredPower taken = takePower(victim);
		if (taken == null)
			return;
		StoredPower already = AthameData.power(athame);
		if (already != null) {
			killer.displayClientMessage(Component.literal("The blade already holds " + AthameData.abilityName(already.abilityId()) + ".").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		// Only now is it really taken: a player victim loses it from their sheet here, not before we
		// know the blade has room for it.
		if (victim instanceof ServerPlayer player) {
			JsonObject sheet = CharacterProgress.activeSheet(player);
			CharacterProgress.takeAbility(sheet, taken.abilityId());
			CharacterProgress.saveActive(player, sheet);
		}
		AthameData.setPower(athame, taken);
		killer.level().playSound(null, killer.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1f, 0.7f);
		killer.displayClientMessage(Component.literal("The athame drinks in " + AthameData.abilityName(taken.abilityId()) + " from " + taken.sourceName() + ".")
				.withStyle(taken.demonic() ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE), true);
	}

	/** The power this victim would give up, or null if they're neither demon nor witch (or have none). */
	private static StoredPower takePower(LivingEntity victim) {
		if (victim instanceof ServerPlayer player) {
			JsonObject sheet = CharacterProgress.activeSheet(player);
			if (sheet == null)
				return null;
			boolean demonic = "demon".equals(CharacterCreation.raceIdOf(sheet));
			boolean witch = "wiccan".equals(CharacterCreation.subspeciesIdOf(sheet));
			List<String> owned = CharacterProgress.abilities(sheet);
			if ((!demonic && !witch) || owned.isEmpty())
				return null;
			String ability = owned.get(victim.getRandom().nextInt(owned.size()));
			// A master's power carries their mastery - plus some of what made them good at it.
			int attunement = CharacterAttributes.readSkills(sheet).getOrDefault(SkillType.ATTUNEMENT, SkillType.MIN);
			int level = CharacterProgress.clamp(CharacterProgress.proficiency(sheet, ability) + (attunement - SkillType.MIN) / 2);
			String name = sheet.has("name") ? sheet.get("name").getAsString() : player.getGameProfile().getName();
			return new StoredPower(ability, level, name, demonic);
		}
		if (victim instanceof DualityNpcEntity npc) {
			String species = npc.species();
			List<String> pool = "Witch".equalsIgnoreCase(species) ? WITCH_POWERS : "Vampire".equalsIgnoreCase(species) ? VAMPIRE_POWERS : "Thrall".equalsIgnoreCase(species) ? THRALL_POWERS : null;
			if (pool == null)
				return null;
			NpcRecord record = npc.record();
			return new StoredPower(pool.get(npc.getRandom().nextInt(pool.size())), 1 + npc.getRandom().nextInt(3), record != null ? record.name() : npc.getName().getString(),
					!"Witch".equalsIgnoreCase(species));
		}
		String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
		List<String> witch = CREATURE_WITCHES.get(typeId);
		List<String> demon = CREATURE_DEMONS.get(typeId);
		List<String> pool = witch != null ? witch : demon;
		if (pool == null)
			return null;
		int level = typeId.equals("duality:spider_queen") ? 3 + victim.getRandom().nextInt(3) : 1 + victim.getRandom().nextInt(2);
		return new StoredPower(pool.get(victim.getRandom().nextInt(pool.size())), level, victim.getName().getString(), demon != null);
	}

	// ------------------------------------------------------------------------------ right-click
	@SubscribeEvent
	public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
		Player player = event.getEntity();
		InteractionHand hand = event.getHand();
		InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack used = event.getItemStack();
		ItemStack other = player.getItemInHand(otherHand);

		// Sneak-use with a bottle in the other hand, whichever hand the athame is in: the bottle's own
		// use (scooping water) comes first when it's in the main hand, so catch that one too.
		ItemStack athame = isAthame(used) && other.is(Items.GLASS_BOTTLE) ? used : used.is(Items.GLASS_BOTTLE) && isAthame(other) ? other : null;
		if (player.isShiftKeyDown() && athame != null && AthameData.blood(athame) != null) {
			if (!player.level().isClientSide())
				bottleBlood(player, athame, athame == used ? otherHand : hand);
			cancel(event);
			return;
		}

		if (isAthame(used) && AthameData.power(used) != null) {
			if (player instanceof ServerPlayer serverPlayer)
				takeIn(serverPlayer, used);
			cancel(event);
		}
	}

	private static void cancel(PlayerInteractEvent.RightClickItem event) {
		event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
		event.setCanceled(true);
	}

	/** The hilt's blood into the bottle in {@code bottleHand}, and the hilt back empty. */
	private static void bottleBlood(Player player, ItemStack athame, InteractionHand bottleHand) {
		ItemStack vial = BloodVialItem.of(AthameData.blood(athame));
		ItemStack bottles = player.getItemInHand(bottleHand);
		if (!player.getAbilities().instabuild)
			bottles.shrink(1);
		if (bottles.isEmpty())
			player.setItemInHand(bottleHand, vial);
		else if (!player.getInventory().add(vial))
			player.drop(vial, false);
		AthameData.setBlood(athame, null);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 0.9f);
	}

	/** The blade's power into the holder's character. */
	private static void takeIn(ServerPlayer player, ItemStack athame) {
		StoredPower power = AthameData.power(athame);
		JsonObject sheet = CharacterProgress.activeSheet(player);
		if (sheet == null) {
			player.displayClientMessage(Component.literal("There's no one here for the power to take root in.").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		String name = AthameData.abilityName(power.abilityId());
		boolean human = "human".equals(CharacterCreation.raceIdOf(sheet));
		int endurance = CharacterAttributes.readSkills(sheet).getOrDefault(SkillType.ENDURANCE, SkillType.MIN);
		if (!CharacterProgress.grantAbility(player, power.abilityId(), power.proficiency())) {
			// Already theirs, at least this well - nothing happens and the blade keeps it.
			player.displayClientMessage(Component.literal("You already command " + name + ".").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		AthameData.setPower(athame, null);
		player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 1f);
		player.displayClientMessage(Component.literal("You take in " + name + " (proficiency " + power.proficiency() + ").").withStyle(ChatFormatting.GOLD), true);
		if (human && power.demonic() && player.getRandom().nextDouble() < conversionChance(endurance))
			becomeLowerLevelDemon(player);
	}

	public static double conversionChance(int endurance) {
		return Math.max(CONVERSION_FLOOR, CONVERSION_BASE - CONVERSION_PER_ENDURANCE * (endurance - SkillType.MIN));
	}

	/** The demon's power took more than it gave: the character is a Lower-Level demon now, keeping
	 *  their stats and powers and gaining the lineage's own. */
	private static void becomeLowerLevelDemon(ServerPlayer player) {
		JsonObject sheet = CharacterProgress.activeSheet(player);
		RaceDefinition demon = RaceCatalog.get("demon");
		SubraceDefinition lowerLevel = demon == null ? null : demon.subrace("lower_level");
		if (sheet == null || lowerLevel == null)
			return;
		JsonObject profile = new JsonObject();
		profile.addProperty("class", demon.id());
		profile.addProperty("subspecies", lowerLevel.id());
		profile.addProperty("subtier", "Baseline");
		profile.addProperty("level", 1);
		profile.addProperty("xp", 0);
		profile.add("species_bound_unlocks", new JsonArray());
		JsonArray profiles = new JsonArray();
		profiles.add(profile);
		sheet.add("species_profiles", profiles);
		JsonArray abilities = sheet.has(CharacterAttributes.SHEET_ABILITIES) ? sheet.getAsJsonArray(CharacterAttributes.SHEET_ABILITIES) : new JsonArray();
		for (String granted : lowerLevel.grantedAbilities()) {
			if (!CharacterProgress.hasAbility(sheet, granted))
				abilities.add(granted);
		}
		sheet.add(CharacterAttributes.SHEET_ABILITIES, abilities);
		CharacterProgress.saveActive(player, sheet);
		CharacterCreation.applyActiveCharacter(player);
		player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.6f);
		player.sendSystemMessage(Component.literal("The demon's power takes root. You are no longer human. (Lower-Level Demon)").withStyle(ChatFormatting.DARK_RED));
	}
}
