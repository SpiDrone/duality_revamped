package net.spidrotech.duality.vanquish;

import net.spidrotech.duality.charactercreation.SkillType;
import net.spidrotech.duality.charactercreation.CharacterProgress;
import net.spidrotech.duality.charactercreation.CharacterCreation;
import net.spidrotech.duality.charactercreation.CharacterAttributes;
import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.DualityMod;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Where vanquished demons go - the record of it, kept in {@code duality_data/wasteland.json}.
 *
 * <p>Only demons strong enough that someone might one day bring them back are kept here; a
 * vanquished Lower-Level demon is simply gone. On arrival a demon loses its powers, as in the show:
 * they're stripped from the character sheet, but the entry remembers what they were, so a
 * resurrection can hand them back.
 *
 * <p>The Wasteland is not kind. Once every in-game day, each demon here rolls against
 * {@link #perishChance}: fail, and they fade for good - removed from the list, their sheet marked
 * {@code PERISHED}, never to be resurrected. The stronger the demon, the smaller that chance, so
 * the powerful linger. Pass the check {@link #DAYS_TO_ENDURE} times and they've learned how to
 * survive the place: they stop rolling and stay for as long as the world does.
 *
 * <p>So the pool of demons that could come back starts with everyone worth keeping and thins
 * day by day, leaving the strongest and the survivors. Resurrecting one - and the Wasteland
 * dimension itself - is still to come; this is the list those will read.
 */
@EventBusSubscriber(modid = "duality")
public final class Wasteland {
	private static final String FILE = "wasteland.json";
	/** A fresh demon's chance of perishing each day, before its power brings that down. */
	public static final double BASE_PERISH_CHANCE = 0.30;
	/** Power that halves the chance (power 10 -> 15%, 20 -> 10%, 40 -> 6%). */
	public static final double POWER_SCALE = 10;
	/** The chance never falls below this until they endure. */
	public static final double MIN_PERISH_CHANCE = 0.02;
	/** Daily checks passed before a demon has figured out how to survive here for good. */
	public static final int DAYS_TO_ENDURE = 7;
	/** Lineages too minor to be worth keeping. */
	private static final List<String> NOT_KEPT = List.of("lower_level");
	/** Extra power a lineage carries beyond its stats and powers. */
	private static final Map<String, Integer> LINEAGE_POWER = Map.of("vampiric_queen", 15, "vampire", 5, "scabber_demon", 3);
	/** How many missed days to roll at most when the server catches up after being off. */
	private static final int MAX_CATCH_UP_DAYS = 30;

	private Wasteland() {
	}

	// ------------------------------------------------------------------------------- admission
	/**
	 * Takes a vanquished demon in, if they're worth keeping. Called by Vanquish as the death becomes
	 * canon, before the character leaves the player. Returns false for a demon not kept.
	 */
	public static boolean admit(MinecraftServer server, String characterId, String ownerUuid, String cause) {
		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(server, characterId);
		if (sheet == null)
			return false;
		String race = CharacterCreation.raceIdOf(sheet), subrace = CharacterCreation.subspeciesIdOf(sheet);
		if (!"demon".equals(race) || NOT_KEPT.contains(subrace))
			return false;

		JsonObject entry = new JsonObject();
		entry.addProperty("character_id", characterId);
		entry.addProperty("owner_uuid", ownerUuid);
		entry.addProperty("name", sheet.has("name") ? sheet.get("name").getAsString() : characterId);
		entry.addProperty("race", race);
		entry.addProperty("subrace", subrace);
		entry.addProperty("power", powerOf(sheet));
		entry.addProperty("vanquished_by", cause);
		entry.addProperty("vanquished_day", today(server));
		entry.addProperty("checks_survived", 0);
		entry.addProperty("endured", false);
		// Stripped from the sheet, kept here - see class doc.
		JsonArray powers = new JsonArray();
		CharacterProgress.abilities(sheet).forEach(powers::add);
		entry.add("powers", powers);
		if (sheet.has(CharacterProgress.SHEET_ABILITY_PROFICIENCY))
			entry.add("proficiency", sheet.getAsJsonObject(CharacterProgress.SHEET_ABILITY_PROFICIENCY).deepCopy());
		sheet.add(CharacterAttributes.SHEET_ABILITIES, new JsonArray());
		sheet.addProperty("afterlife_realm", Vanquish.AFTERLIFE);
		DualityDatabaseManager.saveCharacterSheet(server, characterId, sheet);

		JsonObject file = load(server);
		JsonArray demons = file.getAsJsonArray("demons");
		for (int i = demons.size() - 1; i >= 0; i--) {
			if (demons.get(i).getAsJsonObject().get("character_id").getAsString().equals(characterId))
				demons.remove(i); // vanquished again - a fresh stay
		}
		demons.add(entry);
		save(server, file);
		DualityMod.LOGGER.info("[duality] {} enters the Wasteland (power {}, {}% a day to perish)", entry.get("name").getAsString(), entry.get("power").getAsInt(),
				Math.round(perishChance(entry.get("power").getAsInt()) * 100));
		return true;
	}

	/**
	 * How much of a threat this demon was: every stat point above the floor, three for each power
	 * plus whatever proficiency they'd built in it, and a bonus for a strong lineage.
	 */
	public static int powerOf(JsonObject sheet) {
		int power = 0;
		for (Map.Entry<SkillType, Integer> skill : CharacterAttributes.readSkills(sheet).entrySet()) {
			power += Math.max(0, skill.getValue() - SkillType.MIN);
		}
		for (String ability : CharacterProgress.abilities(sheet)) {
			power += 3 + (CharacterProgress.proficiency(sheet, ability) - CharacterProgress.MIN_PROFICIENCY);
		}
		return power + LINEAGE_POWER.getOrDefault(CharacterCreation.subspeciesIdOf(sheet), 0);
	}

	public static double perishChance(int power) {
		return Math.max(MIN_PERISH_CHANCE, BASE_PERISH_CHANCE / (1 + power / POWER_SCALE));
	}

	// -------------------------------------------------------------------------------- the days
	private static long today(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		return overworld == null ? 0 : overworld.getDayTime() / 24000L;
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		if (server.getTickCount() % 200 != 0)
			return;
		JsonObject file = load(server);
		long today = today(server);
		long lastDay = file.get("last_day").getAsLong();
		if (lastDay < 0 || today < lastDay) {
			// First run, or time was wound back: start counting from here.
			file.addProperty("last_day", today);
			save(server, file);
			return;
		}
		if (today == lastDay)
			return;
		long days = Math.min(MAX_CATCH_UP_DAYS, today - lastDay);
		for (long d = 0; d < days; d++) {
			rollDay(server, file, server.overworld().getRandom());
		}
		file.addProperty("last_day", today);
		save(server, file);
	}

	/** One day in the Wasteland: every demon who hasn't yet endured rolls to perish. */
	private static void rollDay(MinecraftServer server, JsonObject file, RandomSource random) {
		JsonArray demons = file.getAsJsonArray("demons");
		JsonArray perished = file.getAsJsonArray("perished");
		for (int i = demons.size() - 1; i >= 0; i--) {
			JsonObject demon = demons.get(i).getAsJsonObject();
			if (demon.get("endured").getAsBoolean())
				continue;
			if (random.nextDouble() < perishChance(demon.get("power").getAsInt())) {
				demons.remove(i);
				demon.addProperty("perished_day", today(server));
				perished.add(demon);
				JsonObject sheet = DualityDatabaseManager.getCharacterSheet(server, demon.get("character_id").getAsString());
				if (sheet != null) {
					sheet.addProperty("afterlife_realm", "PERISHED");
					DualityDatabaseManager.saveCharacterSheet(server, demon.get("character_id").getAsString(), sheet);
				}
				DualityMod.LOGGER.info("[duality] {} has perished in the Wasteland", demon.get("name").getAsString());
				continue;
			}
			int survived = demon.get("checks_survived").getAsInt() + 1;
			demon.addProperty("checks_survived", survived);
			if (survived >= DAYS_TO_ENDURE) {
				demon.addProperty("endured", true);
				DualityMod.LOGGER.info("[duality] {} has learned to survive the Wasteland", demon.get("name").getAsString());
			}
		}
	}

	// --------------------------------------------------------------------------------- the file
	private static JsonObject load(MinecraftServer server) {
		JsonObject file = DualityDatabaseManager.readDataFile(server, FILE);
		if (file == null)
			file = new JsonObject();
		if (!file.has("last_day"))
			file.addProperty("last_day", -1L);
		if (!file.has("demons"))
			file.add("demons", new JsonArray());
		if (!file.has("perished"))
			file.add("perished", new JsonArray());
		return file;
	}

	private static void save(MinecraftServer server, JsonObject file) {
		DualityDatabaseManager.writeDataFile(server, FILE, file);
	}

	/** Every demon still in the Wasteland - what a resurrection would choose from. */
	public static List<JsonObject> demons(MinecraftServer server) {
		List<JsonObject> out = new ArrayList<>();
		for (JsonElement element : load(server).getAsJsonArray("demons")) {
			out.add(element.getAsJsonObject());
		}
		return out;
	}

	// ------------------------------------------------------------------------------ admin view
	/**
	 * <pre>
	 *   /dualityadmin wasteland          who's in the Wasteland, their odds, and who has endured
	 *   /dualityadmin wasteland rollday  run one day's checks now (testing)
	 * </pre>
	 */
	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("dualityadmin").requires(source -> source.hasPermission(2))
				.then(Commands.literal("wasteland").executes(ctx -> list(ctx.getSource())).then(Commands.literal("rollday").executes(ctx -> {
					MinecraftServer server = ctx.getSource().getServer();
					JsonObject file = load(server);
					rollDay(server, file, server.overworld().getRandom());
					save(server, file);
					return list(ctx.getSource());
				}))));
	}

	private static int list(CommandSourceStack source) {
		List<JsonObject> demons = demons(source.getServer());
		source.sendSuccess(() -> Component.literal("The Wasteland holds " + demons.size() + " demon(s)").withStyle(ChatFormatting.DARK_RED), false);
		for (JsonObject demon : demons) {
			int power = demon.get("power").getAsInt();
			String state = demon.get("endured").getAsBoolean() ? "has endured"
					: Math.round(perishChance(power) * 100) + "% a day to perish, survived " + demon.get("checks_survived").getAsInt() + "/" + DAYS_TO_ENDURE;
			source.sendSuccess(() -> Component.literal("  " + demon.get("name").getAsString() + " (" + demon.get("subrace").getAsString() + ", power " + power + ") - " + state)
					.withStyle(ChatFormatting.GRAY), false);
		}
		return demons.size();
	}
}
