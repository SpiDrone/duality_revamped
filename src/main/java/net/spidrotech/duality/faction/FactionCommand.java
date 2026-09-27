package net.spidrotech.duality.faction;

import net.spidrotech.duality.village.VillageRecord;
import net.spidrotech.duality.village.Villages;
import net.spidrotech.duality.DualityDatabaseManager;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-facing faction management, plus a small operator section for NPC-founded factions and
 * support. Mirrors net.spidrotech.duality.village.VillageCommand's conventions, but permission
 * here is membership-based (leader/officer), not op-based - founding and running a faction is
 * meant to be a normal player action.
 *
 * <pre>
 *   /faction create &lt;name&gt;                 found one, you become its leader
 *   /faction disband                        leader only - unclaims every settlement first
 *   /faction info [name]                    yours if omitted
 *   /faction list                           every faction
 *   /faction invite &lt;player&gt;                leader/officer
 *   /faction kick &lt;player&gt;                  leader/officer, can't kick your own leader
 *   /faction leave                          leader must transfer or disband first
 *   /faction promote &lt;player&gt;               leader only - MEMBER -&gt; OFFICER -&gt; LEADER (transfers)
 *   /faction demote &lt;player&gt;                leader only
 *   /faction settitle &lt;player&gt; &lt;title...&gt;   leader/officer - flavor text ("Queen", "Knight")
 *   /faction setleader &lt;player&gt;             leader only - transfer leadership
 *   /faction claim                          leader/officer - claims the village you're standing in
 *   /faction unclaim                        leader/officer - releases it
 *   /faction relation &lt;faction&gt; &lt;-100..100&gt; leader only - crossing into "allied" (50+) is refused
 *                                           between a GOOD and an EVIL faction, see FactionAlignment
 *   /faction setkind &lt;kind&gt;                 leader only - open|human|witch|vampire|demon|angel;
 *                                           restricts who may join from then on, see FactionKind
 *   /faction admin foundnpc &lt;npcId&gt; &lt;name&gt;  op - found one led by an NPC
 *   /faction admin delete &lt;name&gt;            op
 * </pre>
 *
 * <p>There's no /faction setalignment - a faction's alignment is read off its own duality score
 * (see FactionRecord#alignment), not declared by a leader; setkind only seeds that score to a
 * starting point (FactionKind#startingDualityScore).
 *
 * <p>Membership eligibility (see {@link Factions#isEligible}) and the good/evil alliance rule (see
 * {@link FactionAlignment#canAlly}) are enforced in {@link Factions} itself, not here, so the same
 * rules apply no matter what calls into it.
 */
@EventBusSubscriber(modid = "duality")
public final class FactionCommand {
	private static final SuggestionProvider<CommandSourceStack> FACTION_NAMES = (ctx, builder) -> {
		List<String> names = new ArrayList<>();
		if (Factions.isReady()) {
			for (FactionRecord faction : Factions.store().factions()) {
				names.add(faction.name());
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> KIND_NAMES = (ctx, builder) -> {
		List<String> names = new ArrayList<>();
		for (FactionKind kind : FactionKind.values()) {
			names.add(kind.name().toLowerCase());
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	private FactionCommand() {
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("faction") //
				.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))) //
				.then(Commands.literal("disband").executes(ctx -> disband(ctx.getSource()))) //
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource(), null))
						.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(FACTION_NAMES).executes(ctx -> info(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))) //
				.then(Commands.literal("list").executes(ctx -> list(ctx.getSource()))) //
				.then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> invite(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))) //
				.then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> kick(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))) //
				.then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource()))) //
				.then(Commands.literal("promote").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> promote(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))) //
				.then(Commands.literal("demote").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> demote(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))) //
				.then(Commands.literal("settitle").then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("title", StringArgumentType.greedyString()).executes(ctx -> setTitle(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "title")))))) //
				.then(Commands.literal("setleader").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> setLeader(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))) //
				.then(Commands.literal("claim").executes(ctx -> claim(ctx.getSource()))) //
				.then(Commands.literal("unclaim").executes(ctx -> unclaim(ctx.getSource()))) //
				.then(Commands.literal("relation").then(Commands.argument("faction", StringArgumentType.string()).suggests(FACTION_NAMES)
						.then(Commands.argument("value", IntegerArgumentType.integer(-100, 100)).executes(ctx -> relation(ctx.getSource(), StringArgumentType.getString(ctx, "faction"), IntegerArgumentType.getInteger(ctx, "value")))))) //
				.then(Commands.literal("setkind").then(Commands.argument("kind", StringArgumentType.word()).suggests(KIND_NAMES).executes(ctx -> setKind(ctx.getSource(), StringArgumentType.getString(ctx, "kind"))))) //
				.then(Commands.literal("admin").requires(source -> source.hasPermission(2)) //
						.then(Commands.literal("foundnpc").then(Commands.argument("npcId", StringArgumentType.string())
								.then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> foundNpc(ctx.getSource(), StringArgumentType.getString(ctx, "npcId"), StringArgumentType.getString(ctx, "name")))))) //
						.then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.greedyString()).suggests(FACTION_NAMES).executes(ctx -> adminDelete(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))));
	}

	// -------------------------------------------------------------------------------- helpers
	private static ServerPlayer requirePlayer(CommandSourceStack source) {
		return source.getEntity() instanceof ServerPlayer player ? player : null;
	}

	private static String characterOf(ServerPlayer player) {
		return DualityDatabaseManager.getActiveCharacterId(player);
	}

	private static FactionRecord requireOwnFaction(CommandSourceStack source, ServerPlayer player) {
		FactionRecord faction = Factions.factionOfPlayer(player);
		if (faction == null)
			source.sendFailure(Component.literal("You aren't in a faction."));
		return faction;
	}

	private static boolean requireManagesMembers(CommandSourceStack source, FactionRecord faction, String characterId) {
		FactionMember self = faction.member(characterId);
		if (self != null && self.role().canManageMembers())
			return true;
		source.sendFailure(Component.literal("Only a faction leader or officer can do that."));
		return false;
	}

	private static boolean requireLeader(CommandSourceStack source, FactionRecord faction, String characterId) {
		if (characterId.equals(faction.leaderId()))
			return true;
		source.sendFailure(Component.literal("Only the faction leader can do that."));
		return false;
	}

	// -------------------------------------------------------------------------------- create
	private static int create(CommandSourceStack source, String name) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		Factions.CreateOutcome outcome = Factions.createForPlayer(player, name);
		switch (outcome.result()) {
			case NAME_TAKEN -> source.sendFailure(Component.literal("That name is already taken."));
			case NO_ACTIVE_CHARACTER -> source.sendFailure(Component.literal("You need an active character first."));
			case ALREADY_IN_A_FACTION -> source.sendFailure(Component.literal("You're already in a faction - leave it first."));
			case OK -> source.sendSuccess(() -> Component.literal("Founded " + outcome.faction().name() + "."), false);
		}
		return outcome.ok() ? 1 : 0;
	}

	private static int foundNpc(CommandSourceStack source, String npcId, String name) {
		Factions.CreateOutcome outcome = Factions.createForNpc(npcId, name);
		switch (outcome.result()) {
			case NAME_TAKEN -> source.sendFailure(Component.literal("That name is already taken."));
			case ALREADY_IN_A_FACTION -> source.sendFailure(Component.literal("That NPC is already in a faction."));
			default -> source.sendSuccess(() -> Component.literal("Founded " + outcome.faction().name() + ", led by " + Factions.displayName(npcId) + "."), false);
		}
		return outcome.ok() ? 1 : 0;
	}

	private static int disband(CommandSourceStack source) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null)
			return 0;
		if (!requireLeader(source, faction, characterOf(player)))
			return 0;
		String name = faction.name();
		Factions.disband(faction);
		source.sendSuccess(() -> Component.literal("Disbanded " + name + "."), false);
		return 1;
	}

	private static int adminDelete(CommandSourceStack source, String name) {
		FactionRecord faction = Factions.store().resolve(name);
		if (faction == null) {
			source.sendFailure(Component.literal("No such faction."));
			return 0;
		}
		Factions.disband(faction);
		source.sendSuccess(() -> Component.literal("Deleted " + name + "."), false);
		return 1;
	}

	// ---------------------------------------------------------------------------- membership
	private static int invite(CommandSourceStack source, ServerPlayer target) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireManagesMembers(source, faction, characterOf(player)))
			return 0;
		Factions.MemberResult result = Factions.addPlayerMember(faction, target, FactionRole.MEMBER, "");
		return report(source, result, () -> target.getName().getString() + " joined " + faction.name() + ".");
	}

	private static int kick(CommandSourceStack source, ServerPlayer target) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireManagesMembers(source, faction, characterOf(player)))
			return 0;
		String targetCharacterId = characterOf(target);
		FactionMember targetMember = faction.member(targetCharacterId);
		if (targetMember != null && targetMember.role() == FactionRole.LEADER) {
			source.sendFailure(Component.literal("You can't kick the leader."));
			return 0;
		}
		Factions.MemberResult result = Factions.removeMember(faction, targetCharacterId);
		return report(source, result, () -> target.getName().getString() + " was removed from " + faction.name() + ".");
	}

	private static int leave(CommandSourceStack source) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null)
			return 0;
		Factions.MemberResult result = Factions.removeMember(faction, characterOf(player));
		if (result == Factions.MemberResult.LAST_LEADER) {
			source.sendFailure(Component.literal("Transfer leadership (or /faction disband) before leaving."));
			return 0;
		}
		return report(source, result, () -> "You left " + faction.name() + ".");
	}

	private static int promote(CommandSourceStack source, ServerPlayer target) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireLeader(source, faction, characterOf(player)))
			return 0;
		FactionMember targetMember = faction.member(characterOf(target));
		if (targetMember == null) {
			source.sendFailure(Component.literal("They aren't in your faction."));
			return 0;
		}
		if (targetMember.role() == FactionRole.MEMBER) {
			targetMember.setRole(FactionRole.OFFICER);
			Factions.store().save(faction);
			source.sendSuccess(() -> Component.literal(target.getName().getString() + " is now an officer."), false);
			return 1;
		}
		Factions.transferLeadership(faction, characterOf(target));
		source.sendSuccess(() -> Component.literal(target.getName().getString() + " is now the leader."), false);
		return 1;
	}

	private static int demote(CommandSourceStack source, ServerPlayer target) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireLeader(source, faction, characterOf(player)))
			return 0;
		Factions.MemberResult result = Factions.setRole(faction, characterOf(target), FactionRole.MEMBER);
		return report(source, result, () -> target.getName().getString() + " is now a member.");
	}

	private static int setTitle(CommandSourceStack source, ServerPlayer target, String title) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireManagesMembers(source, faction, characterOf(player)))
			return 0;
		Factions.MemberResult result = Factions.setTitle(faction, characterOf(target), title);
		return report(source, result, () -> target.getName().getString() + " is now titled \"" + title + "\".");
	}

	private static int setLeader(CommandSourceStack source, ServerPlayer target) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireLeader(source, faction, characterOf(player)))
			return 0;
		Factions.MemberResult result = Factions.transferLeadership(faction, characterOf(target));
		return report(source, result, () -> target.getName().getString() + " is now the leader of " + faction.name() + ".");
	}

	// --------------------------------------------------------------------------- settlements
	private static int claim(CommandSourceStack source) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireManagesMembers(source, faction, characterOf(player)))
			return 0;
		VillageRecord village = Villages.isReady() ? Villages.villageAt(player.serverLevel(), player.blockPosition()) : null;
		if (village == null) {
			source.sendFailure(Component.literal("You aren't standing in a village."));
			return 0;
		}
		Factions.ClaimResult result = Factions.claimVillage(faction, village);
		if (result == Factions.ClaimResult.ALREADY_CLAIMED) {
			source.sendFailure(Component.literal(village.name() + " is already claimed."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(faction.name() + " has claimed " + village.name() + "."), false);
		return 1;
	}

	private static int unclaim(CommandSourceStack source) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireManagesMembers(source, faction, characterOf(player)))
			return 0;
		VillageRecord village = Villages.isReady() ? Villages.villageAt(player.serverLevel(), player.blockPosition()) : null;
		if (village == null) {
			source.sendFailure(Component.literal("You aren't standing in a village."));
			return 0;
		}
		Factions.ClaimResult result = Factions.unclaimVillage(faction, village);
		if (result == Factions.ClaimResult.NOT_CLAIMED_BY_YOU) {
			source.sendFailure(Component.literal("Your faction doesn't own " + village.name() + "."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(faction.name() + " has released " + village.name() + "."), false);
		return 1;
	}

	// ------------------------------------------------------------------------------ relations
	private static int relation(CommandSourceStack source, String otherName, int value) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireLeader(source, faction, characterOf(player)))
			return 0;
		FactionRecord other = Factions.store().resolve(otherName);
		if (other == null) {
			source.sendFailure(Component.literal("No such faction."));
			return 0;
		}
		Factions.RelationResult result = Factions.setRelation(faction, other.factionId(), value);
		if (result == Factions.RelationResult.ALIGNMENT_BLOCKS_ALLIANCE) {
			source.sendFailure(Component.literal("Good and evil can't ally - " + faction.name() + " is " + faction.alignment() + ", " + other.name() + " is " + other.alignment() + "."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(faction.name() + "'s relation to " + other.name() + " is now " + value + "."), false);
		return 1;
	}

	// ------------------------------------------------------------------------------ kind
	/** No /faction setalignment - alignment isn't a leader's call, it's read off the faction's own
	 *  duality score (see FactionRecord#alignment). Setting a kind seeds that score to a starting
	 *  point (FactionKind#startingDualityScore); nothing lets a player move it directly today. */
	private static int setKind(CommandSourceStack source, String kindName) {
		ServerPlayer player = requirePlayer(source);
		if (player == null)
			return 0;
		FactionRecord faction = requireOwnFaction(source, player);
		if (faction == null || !requireLeader(source, faction, characterOf(player)))
			return 0;
		FactionKind kind = FactionKind.parse(kindName);
		Factions.setKind(faction, kind);
		source.sendSuccess(() -> Component.literal(faction.name() + " is now a " + kind + " faction (" + faction.alignment() + ", " + faction.dualityDescriptor() + ")."), false);
		return 1;
	}

	// ----------------------------------------------------------------------------- reporting
	private static int report(CommandSourceStack source, Factions.MemberResult result, java.util.function.Supplier<String> onOk) {
		switch (result) {
			case ALREADY_IN_A_FACTION -> source.sendFailure(Component.literal("They're already in a faction."));
			case NOT_A_MEMBER -> source.sendFailure(Component.literal("They aren't in your faction."));
			case LAST_LEADER -> source.sendFailure(Component.literal("Transfer leadership first."));
			case NO_ACTIVE_CHARACTER -> source.sendFailure(Component.literal("They don't have an active character."));
			case KIND_NOT_ALLOWED -> source.sendFailure(Component.literal("They aren't the kind of member this faction accepts."));
			case OK -> source.sendSuccess(() -> Component.literal(onOk.get()), false);
		}
		return result == Factions.MemberResult.OK ? 1 : 0;
	}

	// ----------------------------------------------------------------------------------- info
	private static int info(CommandSourceStack source, String name) {
		FactionRecord faction;
		if (name == null) {
			ServerPlayer player = requirePlayer(source);
			if (player == null)
				return 0;
			faction = requireOwnFaction(source, player);
			if (faction == null)
				return 0;
		} else {
			faction = Factions.store().resolve(name);
			if (faction == null) {
				source.sendFailure(Component.literal("No such faction."));
				return 0;
			}
		}
		FactionRecord finalFaction = faction;
		source.sendSuccess(() -> Component.literal(finalFaction.name() + " - " + finalFaction.kind() + "/" + finalFaction.alignment() + " (" + finalFaction.dualityDescriptor() + "), "
				+ finalFaction.size() + " member(s), " + finalFaction.ownedVillageIds().size() + " settlement(s), founded day " + finalFaction.foundedDay()), false);
		FactionMember leader = faction.leader();
		if (leader != null)
			source.sendSuccess(() -> Component.literal("Led by " + leader.title() + " " + Factions.displayName(leader.memberId())), false);
		for (FactionMember member : faction.members().values()) {
			if (member.role() == FactionRole.LEADER)
				continue;
			source.sendSuccess(() -> Component.literal("  " + member.title() + " " + Factions.displayName(member.memberId())), false);
		}
		for (String villageId : faction.ownedVillageIds()) {
			VillageRecord village = Villages.isReady() ? Villages.store().village(villageId) : null;
			source.sendSuccess(() -> Component.literal("  settlement: " + (village != null ? village.name() : villageId)), false);
		}
		return 1;
	}

	private static int list(CommandSourceStack source) {
		if (!Factions.isReady() || Factions.store().factions().isEmpty()) {
			source.sendSuccess(() -> Component.literal("No factions yet."), false);
			return 1;
		}
		for (FactionRecord faction : Factions.store().factions()) {
			source.sendSuccess(() -> Component.literal(faction.name() + " - " + faction.size() + " member(s), " + faction.ownedVillageIds().size() + " settlement(s)"), false);
		}
		return 1;
	}
}
