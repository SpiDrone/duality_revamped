package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.AbilityValue;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.Ability;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;

/**
 * "Screech" - sonic AOE. Damages and disorients everyone in range who isn't deafened, shatters
 * glass blocks in range, and shatters glass/potions carried by any player hit (splash/lingering
 * potions apply their own effects to the holder as they break).
 *
 * isDeafened() always returns false for now - there's no deafness status (an item, an effect, a
 * mob flag) anywhere in the mod yet, so Screech currently affects everyone including the caster's
 * allies. Hook a real check in there once one exists; nothing else here needs to change.
 */
public final class ScreechAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "screech");
	// ---- tunable ----
	private static final double RADIUS = 12.0;
	private static final float DAMAGE = 4.0f;
	private static final int DISORIENT_DURATION_TICKS = 100;
	private static final double COOLDOWN_TICKS = 15 * 20;

	private ScreechAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.INSTANT) //
				.cooldown(AbilityValue.constant(COOLDOWN_TICKS)) //
				.onActivate(ctx -> {
					LivingEntity caster = ctx.caster();
					if (!(caster.level() instanceof ServerLevel level))
						return;
					level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_SONIC_BOOM, caster.getSoundSource(), 3.0f, 1.6f);
					AABB area = caster.getBoundingBox().inflate(RADIUS);
					for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != caster && !isDeafened(e))) {
						target.hurt(caster.damageSources().sonicBoom(caster), DAMAGE);
						target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DISORIENT_DURATION_TICKS, 0));
						if (target instanceof Player player)
							shatterCarriedGlass(player);
					}
					shatterGlassBlocks(level, caster.blockPosition(), RADIUS);
				}).build();
	}

	/** No "deafened" concept exists yet - see class doc. */
	public static boolean isDeafened(LivingEntity entity) {
		return false;
	}

	private static void shatterGlassBlocks(ServerLevel level, BlockPos center, double radius) {
		int r = (int) Math.ceil(radius);
		double radiusSq = radius * radius;
		BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r)).forEach(cursor -> {
			if (cursor.distSqr(center) > radiusSq)
				return;
			Block block = level.getBlockState(cursor).getBlock();
			if (isGlass(block))
				level.destroyBlock(cursor.immutable(), false);
		});
	}

	private static void shatterCarriedGlass(Player player) {
		boolean shattered = false;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.isEmpty() || !isGlassOrPotion(stack))
				continue;
			applyIfPotion(player, stack);
			player.getInventory().setItem(slot, ItemStack.EMPTY);
			shattered = true;
		}
		if (shattered)
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	private static boolean isGlassOrPotion(ItemStack stack) {
		if (stack.is(Items.GLASS_BOTTLE) || stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))
			return true;
		return stack.getItem() instanceof BlockItem blockItem && isGlass(blockItem.getBlock());
	}

	private static boolean isGlass(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath().contains("glass");
	}

	private static void applyIfPotion(Player player, ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null)
			return;
		for (MobEffectInstance effect : contents.getAllEffects())
			player.addEffect(new MobEffectInstance(effect));
	}
}
