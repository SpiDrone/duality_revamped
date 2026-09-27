package net.spidrotech.duality.item.athame;

import net.spidrotech.duality.abilities.vampire.BloodDrinking;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/**
 * Blood poured off an athame's glass hilt into a bottle (see AthameEvents). It remembers whose it
 * was and how strong, which is what a vampire drinking it (see BloodDrinking) - or a witch using it
 * in a ritual, later - cares about.
 */
public class BloodVialItem extends Item {
	public BloodVialItem() {
		super(new Item.Properties().stacksTo(1).craftRemainder(Items.GLASS_BOTTLE));
	}

	/** A filled vial carrying this sample. */
	public static ItemStack of(BloodSample sample) {
		ItemStack stack = new ItemStack(DualityBloodItems.BLOOD_VIAL.get());
		AthameData.setBlood(stack, sample);
		return stack;
	}

	@Override
	public Component getName(ItemStack stack) {
		BloodSample sample = AthameData.blood(stack);
		return Component.literal(sample != null && sample.color() == BloodSample.GREEN ? "Vial of Acidic Blood" : "Vial of Blood");
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		BloodSample sample = AthameData.blood(stack);
		if (sample != null)
			AthameTooltips.describeBlood(sample, tooltip);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 32;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (AthameData.blood(player.getItemInHand(hand)) == null)
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		BloodSample sample = AthameData.blood(stack);
		if (!(entity instanceof ServerPlayer player) || sample == null)
			return stack;
		String refusal = BloodDrinking.refusal(player, sample);
		if (refusal != null) {
			// Not consumed - they just couldn't bring themselves to.
			player.displayClientMessage(Component.literal(refusal).withStyle(ChatFormatting.GRAY), true);
			return stack;
		}
		BloodDrinking.drink(player, sample);
		return player.getAbilities().instabuild ? stack : ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE), false);
	}
}
