package net.spidrotech.duality.abilities.teleportation;

import net.spidrotech.duality.init.DualityModAttributes;

import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.regex.Pattern;

/**
 * Watches chat for someone saying a whitelighter's name - Charmed-style "calling" a whitelighter
 * to come orb to them. Any online player whose ORBING_PROFICIENCY attribute is present and > 0
 * counts as a "whitelighter" for this purpose - reusing the same attribute OrbAbility already
 * gates on, rather than inventing a separate flag.
 *
 * Matching is whole-word, case-insensitive against each whitelighter's current scoreboard name -
 * deliberately word-bounded (\b...\b) so "Leo" doesn't false-positive on "Leon" or "yellow".
 */
@EventBusSubscriber(modid = "duality")
public final class WhitelighterCallListener {
	private WhitelighterCallListener() {
	}

	// TODO: verify getRawText() against your actual NeoForge version - ServerChatEvent's exact
	// accessor has moved around across 1.19+'s chat-signing rework.
	@SubscribeEvent
	public static void onChat(ServerChatEvent event) {
		ServerPlayer speaker = event.getPlayer();
		String message = event.getRawText();
		MinecraftServer server = speaker.getServer();
		if (server == null)
			return;
		long now = speaker.level().getGameTime();
		for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
			if (candidate == speaker)
				continue;
			if (candidate.getAttributeValue(DualityModAttributes.ORBING_PROFICIENCY) <= 0)
				continue;
			String name = candidate.getGameProfile().getName();
			if (name.isBlank())
				continue;
			Pattern wholeWord = Pattern.compile("\\b" + Pattern.quote(name) + "\\b", Pattern.CASE_INSENSITIVE);
			if (wholeWord.matcher(message).find()) {
				WhitelighterCallRegistry.get().recordCall(candidate.getUUID(), speaker.getUUID(), speaker.getGameProfile().getName(), now);
			}
		}
	}
}
