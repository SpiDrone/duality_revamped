package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.init.DualityModAttributes;
import net.spidrotech.duality.abilities.shapeshift.Shapeshift;
import net.spidrotech.duality.abilities.AbilityToggles;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.function.Consumer;

/**
 * Test hooks for the vampire kit, so it can be exercised before the radials exist. Op only.
 *
 *   /vampire become | cure        add/remove the "vampire" marker (VampireRank#isVampire)
 *   /vampire rank 0-3             0 Thrall, 1 Fledgling, 2 Zealot, 3 Queen
 *   /vampire mode on | off        vampire mode vs humanized (skips the toggle cooldown)
 *   /vampire status               everything above plus toggles, form, unlocked forms, concealment
 */
@EventBusSubscriber(modid = "duality")
public final class VampireCommand {
	private static final String VAMPIRE_TAG = "vampire";

	private VampireCommand() {
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("vampire").requires(source -> source.hasPermission(2)) //
				.then(Commands.literal("become").executes(ctx -> run(ctx, player -> setVampire(player, true)))) //
				.then(Commands.literal("cure").executes(ctx -> run(ctx, player -> setVampire(player, false)))) //
				.then(Commands.literal("rank").then(Commands.argument("rank", IntegerArgumentType.integer(0, VampireRank.QUEEN.level()))
						.executes(ctx -> run(ctx, player -> setRank(player, IntegerArgumentType.getInteger(ctx, "rank")))))) //
				.then(Commands.literal("mode") //
						.then(Commands.literal("on").executes(ctx -> run(ctx, player -> VampireMode.setActive(player, true)))) //
						.then(Commands.literal("off").executes(ctx -> run(ctx, player -> VampireMode.setActive(player, false))))) //
				.then(Commands.literal("status").executes(ctx -> run(ctx, player -> status(ctx.getSource(), player)))));
	}

	private static int run(CommandContext<CommandSourceStack> ctx, Consumer<ServerPlayer> action) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		action.accept(player);
		if (!ctx.getNodes().get(ctx.getNodes().size() - 1).getNode().getName().equals("status"))
			status(ctx.getSource(), player);
		return 1;
	}

	private static void setVampire(ServerPlayer player, boolean vampire) {
		DualityModVariables.PlayerVariables vars = player.getData(DualityModVariables.PLAYER_VARIABLES);
		// isVampire is a substring check, so strip every occurrence before (re)adding one.
		String others = vars.EquippedAbilities.replace(VAMPIRE_TAG, "").trim();
		vars.EquippedAbilities = vampire ? (others.isEmpty() ? VAMPIRE_TAG : others + " " + VAMPIRE_TAG) : others;
		vars.markSyncDirty();
		if (!vampire)
			VampireMode.setActive(player, false);
	}

	private static void setRank(ServerPlayer player, int rank) {
		AttributeInstance attribute = player.getAttribute(DualityModAttributes.VAMPIRE_RANK);
		if (attribute != null)
			attribute.setBaseValue(rank);
		VampireMode.refreshAccess(player);
		Shapeshift.revertIfNotAllowed(player);
	}

	private static void status(CommandSourceStack source, ServerPlayer player) {
		boolean vampire = VampireRank.isVampire(player);
		String report = "vampire=" + vampire //
				+ "  rank=" + VampireRank.fromAttribute(player) //
				+ "  mode=" + (VampireMode.isActive(player) ? "vampire" : "humanized") //
				+ "\ntoggled=" + AbilityToggles.all(player) //
				+ "\nform=" + Shapeshift.current(player).map(form -> form.id().toString()).orElse("none") //
				+ "  unlocked=" + Shapeshift.unlockedForms(player) //
				+ "\nconcealment=" + VampireMode.concealmentEffectiveness(player);
		source.sendSuccess(() -> Component.literal(report), false);
	}
}
