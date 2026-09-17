package net.spidrotech.duality.village;

import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Operator tools for the village system. Everything the simulation does on its own can be forced
 * from here, which is how you test a chain that otherwise takes twenty in-game days to play out.
 *
 * <pre>
 *   /village list                        every settlement, nearest info at a glance
 *   /village here                        what village you're standing in
 *   /village info &lt;village&gt;              defenses, roster, captives, recent history
 *   /village log &lt;village&gt; [count]       what has happened there
 *   /village create &lt;name&gt; &lt;faction&gt;     register one where you're standing
 *   /village delete &lt;village&gt;
 *   /village event &lt;village&gt; &lt;event&gt;     fire an event now and read the result
 *   /village simulate [days]             run the background simulation forward
 *   /village set &lt;village&gt; &lt;stat&gt; &lt;n&gt;    militia|fortification|wards|morale|prosperity|population|radius
 *   /village npc add [role]              enroll the nearest mob as a resident
 *   /village npc list &lt;village&gt;
 *   /village npc info &lt;npc&gt;              status, fate, where they are and how long they have
 *   /village npc rescue &lt;npc&gt;            free a captive
 *   /village world [duality &lt;n&gt;|add &lt;n&gt;] read or move the world duality score
 * </pre>
 */
@EventBusSubscriber(modid = "duality")
public final class VillageCommand {
	private static final SuggestionProvider<CommandSourceStack> VILLAGE_NAMES = (ctx, builder) -> {
		List<String> names = new ArrayList<>();
		if (Villages.isReady()) {
			for (VillageRecord village : Villages.store().villages()) {
				names.add(village.name());
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> EVENT_IDS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		for (VillageEvent event : VillageEvent.values()) {
			ids.add(event.name());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> FACTIONS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		for (VillageFaction faction : VillageFaction.values()) {
			ids.add(faction.name());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> STATS = (ctx, builder) -> SharedSuggestionProvider
			.suggest(List.of("militia", "fortification", "wards", "morale", "prosperity", "population", "radius"), builder);

	private static final SuggestionProvider<CommandSourceStack> NPC_IDS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		if (Villages.isReady()) {
			for (NpcRecord npc : Villages.store().npcs()) {
				ids.add(npc.npcId());
			}
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private VillageCommand() {
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("village").requires(source -> source.hasPermission(2)) //
				.then(Commands.literal("list").executes(VillageCommand::list)) //
				.then(Commands.literal("here").executes(VillageCommand::here)) //
				.then(Commands.literal("info").then(Commands.argument("village", StringArgumentType.string()).suggests(VILLAGE_NAMES).executes(VillageCommand::info))) //
				.then(Commands.literal("log").then(Commands.argument("village", StringArgumentType.string()).suggests(VILLAGE_NAMES) //
						.executes(ctx -> log(ctx, 8)) //
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 60)).executes(ctx -> log(ctx, IntegerArgumentType.getInteger(ctx, "count")))))) //
				.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.string()) //
						.then(Commands.argument("faction", StringArgumentType.word()).suggests(FACTIONS).executes(VillageCommand::create)))) //
				.then(Commands.literal("delete").then(Commands.argument("village", StringArgumentType.string()).suggests(VILLAGE_NAMES).executes(VillageCommand::delete))) //
				.then(Commands.literal("event").then(Commands.argument("village", StringArgumentType.string()).suggests(VILLAGE_NAMES) //
						.then(Commands.argument("event", StringArgumentType.word()).suggests(EVENT_IDS).executes(VillageCommand::event)))) //
				.then(Commands.literal("simulate").executes(ctx -> simulate(ctx, 1)) //
						.then(Commands.argument("days", IntegerArgumentType.integer(1, VillageSimulator.MAX_CATCHUP_DAYS))
								.executes(ctx -> simulate(ctx, IntegerArgumentType.getInteger(ctx, "days"))))) //
				.then(Commands.literal("set").then(Commands.argument("village", StringArgumentType.string()).suggests(VILLAGE_NAMES) //
						.then(Commands.argument("stat", StringArgumentType.word()).suggests(STATS) //
								.then(Commands.argument("value", DoubleArgumentType.doubleArg()).executes(VillageCommand::set))))) //
				.then(Commands.literal("npc") //
						.then(Commands.literal("add").executes(ctx -> npcAdd(ctx, "")) //
								.then(Commands.argument("role", StringArgumentType.word()).executes(ctx -> npcAdd(ctx, StringArgumentType.getString(ctx, "role"))))) //
						.then(Commands.literal("list").then(Commands.argument("village", StringArgumentType.string()).suggests(VILLAGE_NAMES).executes(VillageCommand::npcList))) //
						.then(Commands.literal("info").then(Commands.argument("npc", StringArgumentType.string()).suggests(NPC_IDS).executes(VillageCommand::npcInfo))) //
						.then(Commands.literal("rescue").then(Commands.argument("npc", StringArgumentType.string()).suggests(NPC_IDS).executes(VillageCommand::npcRescue)))) //
				.then(Commands.literal("world").executes(VillageCommand::world) //
						.then(Commands.literal("duality").then(Commands.argument("value", DoubleArgumentType.doubleArg(-WorldDualityState.LIMIT, WorldDualityState.LIMIT))
								.executes(ctx -> setDuality(ctx, DoubleArgumentType.getDouble(ctx, "value"), false)))) //
						.then(Commands.literal("add").then(Commands.argument("value", DoubleArgumentType.doubleArg())
								.executes(ctx -> setDuality(ctx, DoubleArgumentType.getDouble(ctx, "value"), true))))));
	}

	// ------------------------------------------------------------------------------- readouts
	private static int list(CommandContext<CommandSourceStack> ctx) {
		if (notReady(ctx))
			return 0;
		if (Villages.store().villages().isEmpty()) {
			reply(ctx, "§7No villages registered. Stand somewhere and use /village create <name> <faction>.");
			return 1;
		}
		reply(ctx, "§6" + Villages.store().villages().size() + " settlement(s), day " + Villages.currentDay() + ":");
		for (VillageRecord village : Villages.store().villages()) {
			reply(ctx, "  " + colorFor(village) + village.name() + "§7 (" + village.faction().displayName() + ") pop " + village.population() + ", def "
					+ Math.round(village.defenseAgainst(ThreatType.PHYSICAL)) + "/" + Math.round(village.defenseAgainst(ThreatType.MAGICAL)) + " phys/magic, "
					+ village.center() + (village.captives().isEmpty() ? "" : " §c[" + village.captives().size() + " held]"));
		}
		return 1;
	}

	private static int here(CommandContext<CommandSourceStack> ctx) {
		if (notReady(ctx))
			return 0;
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
			reply(ctx, "§cRun this as a player.");
			return 0;
		}
		VillageRecord village = Villages.villageAt(player.serverLevel(), player.blockPosition());
		if (village != null) {
			printVillage(ctx, village);
			return 1;
		}
		List<VillageRecord> near = Villages.villagesNear(player.serverLevel(), player.blockPosition(), 512);
		if (near.isEmpty()) {
			reply(ctx, "§7Nothing of ours within 512 blocks.");
			return 1;
		}
		reply(ctx, "§7Not inside a village. Nearest:");
		for (VillageRecord candidate : near) {
			reply(ctx, "  " + colorFor(candidate) + candidate.name() + "§7 - " + Math.round(Math.sqrt(candidate.center().horizontalDistanceSqr(
					Villages.pointOf(player.serverLevel(), player.blockPosition())))) + " blocks");
		}
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		VillageRecord village = requireVillage(ctx);
		if (village == null)
			return 0;
		printVillage(ctx, village);
		return 1;
	}

	private static void printVillage(CommandContext<CommandSourceStack> ctx, VillageRecord village) {
		reply(ctx, colorFor(village) + village.name() + " §7[" + village.faction().displayName() + "] " + village.villageId());
		reply(ctx, "  §7at " + village.center() + ", radius " + village.radius() + ", founded day " + village.foundedDay() + ", simulated to day "
				+ village.lastSimulatedDay());
		reply(ctx, String.format("  §7pop §f%d§7  militia §f%d§7  walls §f%d§7  wards §f%.1f§7  morale §f%.2f§7  prosperity §f%.0f",
				village.population(), village.militia(), village.fortification(), village.wards(), village.morale(), village.prosperity()));
		reply(ctx, String.format("  §7defense: physical §f%.0f§7  magical §f%.0f§7  social §f%.0f",
				village.defenseAgainst(ThreatType.PHYSICAL), village.defenseAgainst(ThreatType.MAGICAL), village.defenseAgainst(ThreatType.SOCIAL)));
		if (!village.buildings().isEmpty())
			reply(ctx, "  §7buildings: §f" + village.buildings().size());
		if (!village.flags().isEmpty())
			reply(ctx, "  §7flags: §f" + String.join(", ", village.flags()));
		List<NpcRecord> residents = Villages.store().residentsOf(village);
		reply(ctx, "  §7named residents (" + residents.size() + "):");
		for (NpcRecord npc : residents) {
			reply(ctx, "    §f" + npc.name() + " §8" + npc.role().toLowerCase() + ", " + npc.species().toLowerCase() + " §8" + npc.npcId());
		}
		List<NpcRecord> captives = Villages.store().captivesOf(village);
		if (!captives.isEmpty()) {
			reply(ctx, "  §cheld here (" + captives.size() + "):");
			for (NpcRecord npc : captives) {
				reply(ctx, "    §c" + npc.name() + " §8" + describeFate(npc));
			}
		}
		List<VillageRecord.LogEntry> recent = village.recentLog(3);
		if (!recent.isEmpty()) {
			reply(ctx, "  §7lately:");
			for (VillageRecord.LogEntry entry : recent) {
				reply(ctx, "    §8day " + entry.day() + ": " + entry.text());
			}
		}
	}

	private static int log(CommandContext<CommandSourceStack> ctx, int count) {
		VillageRecord village = requireVillage(ctx);
		if (village == null)
			return 0;
		List<VillageRecord.LogEntry> entries = village.recentLog(count);
		if (entries.isEmpty()) {
			reply(ctx, "§7Nothing has happened at " + village.name() + " yet.");
			return 1;
		}
		reply(ctx, "§6" + village.name() + " - last " + entries.size() + ":");
		for (VillageRecord.LogEntry entry : entries) {
			reply(ctx, String.format("  §8day %d §7[%s/%s] §f%s §8(%+.1f)", entry.day(), entry.eventId(), entry.outcome(), entry.text(),
					entry.dualityDelta()));
		}
		return 1;
	}

	private static int world(CommandContext<CommandSourceStack> ctx) {
		if (notReady(ctx))
			return 0;
		WorldDualityState state = Villages.store().world();
		reply(ctx, String.format("§6World duality: §f%.1f§7 / %.0f (%s), day %d, %d days simulated.", state.dualityScore(), WorldDualityState.LIMIT,
				state.descriptor(), Villages.currentDay(), state.simulatedDayCount()));
		return 1;
	}

	// ------------------------------------------------------------------------------ mutations
	private static int create(CommandContext<CommandSourceStack> ctx) {
		if (notReady(ctx))
			return 0;
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
			reply(ctx, "§cRun this as a player - the village is placed where you're standing.");
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "name");
		VillageFaction faction = VillageFaction.parse(StringArgumentType.getString(ctx, "faction"));
		VillageRecord village = Villages.create(name, faction, player.serverLevel(), player.blockPosition());
		if (village == null) {
			reply(ctx, "§cThere is already a village called " + name + ".");
			return 0;
		}
		reply(ctx, "§aFounded " + village.name() + " (" + faction.displayName() + ") at " + village.center() + ".");
		printVillage(ctx, village);
		return 1;
	}

	private static int delete(CommandContext<CommandSourceStack> ctx) {
		VillageRecord village = requireVillage(ctx);
		if (village == null)
			return 0;
		String name = village.name();
		Villages.store().delete(village.villageId());
		reply(ctx, "§cRemoved " + name + ". Its named residents are now unaccounted for.");
		return 1;
	}

	private static int event(CommandContext<CommandSourceStack> ctx) {
		VillageRecord village = requireVillage(ctx);
		if (village == null)
			return 0;
		String raw = StringArgumentType.getString(ctx, "event");
		VillageEvent event = VillageEvent.parse(raw);
		if (event == null) {
			reply(ctx, "§cNo such event \"" + raw + "\".");
			return 0;
		}
		VillageEventResult result = Villages.run(village.name(), event);
		for (String line : result.fullReport()) {
			reply(ctx, (result.happened() ? "§f" : "§8") + line);
		}
		return result.happened() ? 1 : 0;
	}

	private static int simulate(CommandContext<CommandSourceStack> ctx, int days) {
		if (notReady(ctx))
			return 0;
		int reported = 0;
		for (int i = 0; i < days; i++) {
			VillageSimulator.DayReport report = Villages.simulateDay();
			for (VillageEventResult result : report.events()) {
				reply(ctx, "§7day " + report.day() + " §f" + result.summary());
				for (String line : result.narrative()) {
					reply(ctx, "    §8" + line);
				}
				reported++;
			}
			for (String note : report.fateNotes()) {
				reply(ctx, "§7day " + report.day() + " §c" + note);
				reported++;
			}
		}
		reply(ctx, "§6Simulated " + days + " day(s); " + reported + " thing(s) happened. World duality now "
				+ String.format("%.1f", Villages.store().world().dualityScore()) + ".");
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> ctx) {
		VillageRecord village = requireVillage(ctx);
		if (village == null)
			return 0;
		String stat = StringArgumentType.getString(ctx, "stat").toLowerCase();
		double value = DoubleArgumentType.getDouble(ctx, "value");
		switch (stat) {
			case "militia" -> village.setMilitia((int) value);
			case "fortification", "walls" -> village.setFortification((int) value);
			case "wards" -> village.setWards(value);
			case "morale" -> village.setMorale(value);
			case "prosperity" -> village.setProsperity(value);
			case "population" -> village.setPopulation((int) value);
			case "radius" -> village.setRadius((int) value);
			default -> {
				reply(ctx, "§cUnknown stat \"" + stat + "\".");
				return 0;
			}
		}
		Villages.store().save(village);
		reply(ctx, "§a" + village.name() + " " + stat + " set.");
		printVillage(ctx, village);
		return 1;
	}

	private static int setDuality(CommandContext<CommandSourceStack> ctx, double value, boolean relative) {
		if (notReady(ctx))
			return 0;
		WorldDualityState state = Villages.store().world();
		if (relative)
			state.addDualityScore(value);
		else
			state.setDualityScore(value);
		Villages.store().saveWorld();
		return world(ctx);
	}

	// ----------------------------------------------------------------------------------- npcs
	private static int npcAdd(CommandContext<CommandSourceStack> ctx, String role) {
		if (notReady(ctx))
			return 0;
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
			reply(ctx, "§cRun this as a player.");
			return 0;
		}
		ServerLevel level = player.serverLevel();
		VillageRecord village = Villages.villageAt(level, player.blockPosition());
		if (village == null) {
			reply(ctx, "§cYou aren't standing in a village. /village here will tell you what's nearby.");
			return 0;
		}
		Entity target = nearestMob(level, player);
		if (target == null) {
			reply(ctx, "§cNothing within 16 blocks to enroll.");
			return 0;
		}
		NpcRecord npc = Villages.enroll(village, target, role);
		reply(ctx, "§a" + npc.name() + " (" + npc.role().toLowerCase() + ") is now a resident of " + village.name() + ". §8" + npc.npcId());
		return 1;
	}

	private static int npcList(CommandContext<CommandSourceStack> ctx) {
		VillageRecord village = requireVillage(ctx);
		if (village == null)
			return 0;
		List<NpcRecord> residents = Villages.store().residentsOf(village);
		reply(ctx, "§6" + village.name() + " - " + residents.size() + " named resident(s), " + village.captives().size() + " held:");
		for (NpcRecord npc : residents) {
			reply(ctx, "  §f" + npc.name() + " §8" + npc.role().toLowerCase() + " / " + npc.species().toLowerCase() + " / " + npc.npcId());
		}
		for (NpcRecord npc : Villages.store().captivesOf(village)) {
			reply(ctx, "  §c" + npc.name() + " §8" + describeFate(npc) + " / " + npc.npcId());
		}
		return 1;
	}

	private static int npcInfo(CommandContext<CommandSourceStack> ctx) {
		if (notReady(ctx))
			return 0;
		NpcRecord npc = Villages.findNpc(StringArgumentType.getString(ctx, "npc"));
		if (npc == null) {
			reply(ctx, "§cNo such NPC.");
			return 0;
		}
		VillageRecord home = Villages.store().village(npc.homeVillageId());
		VillageRecord current = Villages.store().village(npc.currentVillageId());
		reply(ctx, "§6" + npc.name() + " §7(" + npc.role().toLowerCase() + ", " + npc.species() + ") §8" + npc.npcId());
		reply(ctx, "  §7status §f" + npc.status() + "§7, " + describeFate(npc));
		reply(ctx, "  §7home §f" + (home != null ? home.name() : "-") + "§7, currently at §f" + (current != null ? current.name() : "unknown"));
		if (npc.corpseLocation() != null)
			reply(ctx, "  §cbody at §f" + npc.corpseLocation());
		reply(ctx, "  §7entity §f" + npc.entityType() + (npc.inWorld() ? " §a(in world)" : " §8(not spawned)"));
		for (String line : npc.log()) {
			reply(ctx, "  §8" + line);
		}
		return 1;
	}

	private static int npcRescue(CommandContext<CommandSourceStack> ctx) {
		if (notReady(ctx))
			return 0;
		NpcRecord npc = Villages.findNpc(StringArgumentType.getString(ctx, "npc"));
		if (npc == null) {
			reply(ctx, "§cNo such NPC.");
			return 0;
		}
		if (!VillageSimulator.rescue(Villages.store(), Villages.bridge(), npc, Villages.currentDay())) {
			reply(ctx, "§c" + npc.name() + " isn't a captive (" + npc.status() + ").");
			return 0;
		}
		reply(ctx, "§a" + npc.name() + " is free.");
		return 1;
	}

	// -------------------------------------------------------------------------------- helpers
	private static Entity nearestMob(ServerLevel level, ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(16);
		Entity best = null;
		double bestDistSqr = Double.MAX_VALUE;
		for (Entity entity : level.getEntities(player, box, candidate -> candidate instanceof LivingEntity && !(candidate instanceof ServerPlayer))) {
			double distSqr = entity.distanceToSqr(player);
			if (distSqr < bestDistSqr) {
				bestDistSqr = distSqr;
				best = entity;
			}
		}
		return best;
	}

	private static String describeFate(NpcRecord npc) {
		if (npc.status() == NpcStatus.DEAD)
			return "dead";
		if (npc.fate() == NpcFate.NONE)
			return "no fate pending";
		if (!npc.fate().resolvesOnTimer())
			return npc.fate().name().toLowerCase() + ", open-ended";
		long left = npc.fateDay() - Villages.currentDay();
		return npc.fate().name().toLowerCase() + ", " + (left <= 0 ? "due now" : left + " day(s) left");
	}

	private static VillageRecord requireVillage(CommandContext<CommandSourceStack> ctx) {
		if (notReady(ctx))
			return null;
		String name = StringArgumentType.getString(ctx, "village");
		VillageRecord village = Villages.store().resolve(name);
		if (village == null)
			reply(ctx, "§cNo village known as \"" + name + "\".");
		return village;
	}

	private static boolean notReady(CommandContext<CommandSourceStack> ctx) {
		if (Villages.isReady())
			return false;
		reply(ctx, "§cThe village system isn't loaded.");
		return true;
	}

	private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}

	private static String colorFor(VillageRecord village) {
		if (village.faction().isEvil())
			return "§c";
		return village.faction().isGood() ? "§b" : "§f";
	}
}
