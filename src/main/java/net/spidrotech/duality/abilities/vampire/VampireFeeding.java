package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.village.entity.DualityNpcEntity;
import net.spidrotech.duality.item.athame.BloodSample;
import net.spidrotech.duality.item.athame.AthameEvents;
import net.spidrotech.duality.charactercreation.SkillType;
import net.spidrotech.duality.charactercreation.CharacterAttributes;

import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.ChatFormatting;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.Map;

/**
 * Feeding: a vampire with the vampire out (VampireMode) sneaks and right-clicks someone, empty
 * handed, to drink a little - a sip, not a bite. Each sip is {@link #SIP_DOSES} of a dose and costs
 * the donor {@link #SIP_DAMAGE} health; one per {@link #COOLDOWN_TICKS}.
 *
 * <p>What sets feeding apart from the bite is consent. A person who gives their blood freely
 * doesn't cost a Vegan vampire the trait (see BloodDrinking#drink) - though it's still a person's
 * blood, so their eyes run red until it's burned off. A donor is willing if:
 * <ul>
 * <li>they're a player who is sneaking too - crouching to offer their neck;
 * <li>they're a village NPC who is a vampire's Thrall, or who the vampire can charm: Charisma of
 * {@link #PERSUADE_CHARISMA} or more. (A stand-in until NPCs have their own attitudes.)
 * </ul>
 * Anyone else is fed on unwillingly, which costs Vegan like a bite would. Animals can be fed from
 * too, under animal blood's usual rules. Draining someone to death this way is killing a person to
 * feed - Soulless, and the end of the Vegan road.
 */
@EventBusSubscriber(modid = "duality")
public final class VampireFeeding {
	public static final double SIP_DOSES = 0.5;
	public static final float SIP_DAMAGE = 1.0f;
	public static final int COOLDOWN_TICKS = 20;
	public static final int PERSUADE_CHARISMA = 4;

	private static final Map<UUID, Long> LAST_SIP = new ConcurrentHashMap<>();
	/** Set while a sip's damage is being dealt, so the bite (VampireBite) doesn't also count it. */
	private static boolean sipping;

	private VampireFeeding() {
	}

	public static boolean isSipping() {
		return sipping;
	}

	/** Whether this donor gives their blood freely - see class doc. */
	public static boolean isWilling(ServerPlayer vampire, LivingEntity donor) {
		if (donor instanceof Player player)
			return player.isShiftKeyDown();
		if (donor instanceof DualityNpcEntity npc)
			return "Thrall".equalsIgnoreCase(npc.species()) || CharacterAttributes.skillOf(vampire, SkillType.CHARISMA) >= PERSUADE_CHARISMA;
		return false;
	}

	@SubscribeEvent
	public static void onInteract(PlayerInteractEvent.EntityInteract event) {
		Player player = event.getEntity();
		if (event.getHand() != InteractionHand.MAIN_HAND || !BiteNetwork.isFeed(player, event.getTarget()))
			return;
		LivingEntity donor = (LivingEntity) event.getTarget();
		event.setCanceled(true);
		// CONSUME, not SUCCESS: handled, but no arm swing - feeding is a lean in (BiteLean), not a grab.
		event.setCancellationResult(InteractionResult.CONSUME);
		if (player instanceof ServerPlayer vampire) {
			// Holding use repeats this every few ticks; each one keeps the lean going for everyone
			// watching, for as long as they keep drinking.
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(vampire, new BiteNetwork.FeedLeanPayload(vampire.getId()));
			sip(vampire, donor);
		}
	}

	private static void sip(ServerPlayer vampire, LivingEntity donor) {
		long now = vampire.level().getGameTime();
		Long last = LAST_SIP.get(vampire.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS)
			return;
		BloodSample blood = AthameEvents.sampleOf(donor);
		String refusal = BloodDrinking.refusal(vampire, blood);
		if (refusal != null) {
			vampire.displayClientMessage(Component.literal(refusal).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		LAST_SIP.put(vampire.getUUID(), now);
		boolean willing = blood.kind().sapient() && isWilling(vampire, donor);
		double mana = AthameEvents.sapMana(donor, SIP_DOSES);

		boolean wasAlive = donor.isAlive();
		sipping = true;
		try {
			donor.hurt(vampire.damageSources().playerAttack(vampire), SIP_DAMAGE);
		} finally {
			sipping = false;
		}
		BloodDrinking.drink(vampire, blood, SIP_DOSES, mana, willing);
		Vampirism.tryInfect(vampire, donor, Vampirism.Via.FEEDING);
		if (wasAlive && !donor.isAlive() && BloodDrinking.isPerson(donor))
			BloodDrinking.becomeSoulless(vampire, "You drank them dry. Something in you goes quiet for good. (Soulless)");

		vampire.level().playSound(null, donor.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6f, 0.7f);
		if (vampire.level() instanceof ServerLevel level)
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), donor.getX(), donor.getY() + donor.getBbHeight() * 0.8,
					donor.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
		if (blood.kind().sapient())
			vampire.displayClientMessage(Component.literal(willing ? "They let you drink." : "You drink from them against their will.")
					.withStyle(willing ? ChatFormatting.GOLD : ChatFormatting.DARK_RED), true);
	}
}
