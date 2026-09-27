package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.mana.Mana;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.food.FoodData;
import net.minecraft.server.level.ServerPlayer;

/**
 * A vampire's blood stands in for hunger. The HUD shows blood where the hunger row was (see
 * ClientMana), and this makes the body match it.
 *
 * <p>Vanilla still does all the bookkeeping: sprinting, jumping, fighting and healing wear the food
 * bar down exactly as they would for anyone. This just reads how many food points were lost since
 * last time, takes {@link #BLOOD_PER_FOOD_POINT} blood for each, and puts the food bar back. So the
 * food bar never moves, and blood falls the way hunger would. With saturation held at 0, every
 * point of exertion lands on blood straight away.
 *
 * <p>With blood left, the food bar is held full, so the vampire heals naturally (paying blood for
 * it) and can sprint. With none, it's held at {@link #STARVING_FOOD_LEVEL}: no healing, no
 * sprinting, until they feed.
 *
 * <p>Food does nothing for a vampire.
 */
@EventBusSubscriber(modid = "duality")
public final class VampireHunger {
	/** Blood per food point of exertion. Vanilla hunger is 20 points; this makes the 100-blood reserve
	 *  last as long as 20 full hunger bars would. */
	public static final double BLOOD_PER_FOOD_POINT = 0.25;
	/** At or below this much blood a vampire can't keep up a human disguise - see loseDisguiseWhenHungry. */
	public static final double DISGUISE_LOST_AT = BloodDrinking.RESERVE_MAX * BloodDrinking.DESPERATE_RESERVE;
	private static final int FULL_FOOD_LEVEL = 20;
	/** Below vanilla's sprint threshold (7), and too low to heal, but no starvation damage. */
	private static final int STARVING_FOOD_LEVEL = 6;

	/** The food level last set on each vampire. In memory only: after a relog the first check just
	 *  re-establishes it, charging nothing. */
	private static final java.util.Map<java.util.UUID, Integer> LAST_HELD = new java.util.concurrent.ConcurrentHashMap<>();

	private VampireHunger() {
	}

	@SubscribeEvent
	public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
		LAST_HELD.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0 || !VampireRank.isVampire(player) || player.getAbilities().invulnerable)
			return;
		FoodData food = player.getFoodData();
		// Spent = how far the bar fell from where WE last put it - not from where it "should" be now.
		// Measuring from the new target turned the jump from starving (6) back up to full (20) after
		// a drink into 14 points "spent", which ate the blood that had just been drunk.
		Integer lastHeld = LAST_HELD.get(player.getUUID());
		int spent = lastHeld == null ? 0 : lastHeld - food.getFoodLevel();
		if (spent > 0 && Mana.current(player) > 0)
			Mana.drain(player, spent * BLOOD_PER_FOOD_POINT);
		int now = Mana.current(player) > 0 ? FULL_FOOD_LEVEL : STARVING_FOOD_LEVEL;
		if (food.getFoodLevel() != now)
			food.setFoodLevel(now);
		LAST_HELD.put(player.getUUID(), now);
		if (food.getSaturationLevel() != 0)
			food.setSaturation(0);
		loseDisguiseWhenHungry(player);
	}

	/**
	 * Human form is a disguise, and a hungry vampire can't hold it: at {@link #DISGUISE_LOST_AT} blood
	 * or less, vampire mode is forced on, and AbilityCosts refuses to switch it back off until they've
	 * fed. Called every 10 ticks.
	 */
	private static void loseDisguiseWhenHungry(ServerPlayer player) {
		if (Mana.current(player) > DISGUISE_LOST_AT || VampireMode.isActive(player))
			return;
		VampireMode.setActive(player, true);
		player.displayClientMessage(net.minecraft.network.chat.Component.literal("Your hunger tears the human mask away.").withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
	}

	/** Whether this vampire is too hungry to keep up a human disguise. */
	public static boolean tooHungryToHide(ServerPlayer player) {
		return Mana.current(player) <= DISGUISE_LOST_AT;
	}
}
