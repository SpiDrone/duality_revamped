package net.spidrotech.duality.item.athame;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

/** Tooltip lines for what an athame or a vial is carrying. */
public final class AthameTooltips {
	private AthameTooltips() {
	}

	public static void describeBlood(BloodSample sample, List<Component> tooltip) {
		ChatFormatting color = sample.color() == BloodSample.GREEN ? ChatFormatting.GREEN : ChatFormatting.DARK_RED;
		String kind = sample.color() == BloodSample.GREEN ? "Acidic blood" : "Blood";
		String source = sample.kind() == BloodSample.Kind.CREATURE ? sample.sourceName() + " (" + sample.sourceId() + ")" : sample.sourceName();
		tooltip.add(Component.literal(kind + " of " + source).withStyle(color));
		String strength = sample.toxic() ? "Tainted" : sample.quality() >= 35 ? "Potent" : sample.quality() >= 15 ? "Ordinary" : "Thin";
		tooltip.add(Component.literal("  " + strength + " (" + Math.round(sample.quality()) + "), " + sample.doses() + "/" + BloodSample.MAX_DOSES + " doses").withStyle(ChatFormatting.GRAY));
		if (sample.mana() >= 1)
			tooltip.add(Component.literal("  Carries " + Math.round(sample.mana()) + " mana").withStyle(ChatFormatting.BLUE));
	}

	public static void describePower(StoredPower power, List<Component> tooltip) {
		tooltip.add(Component.literal("Holds " + AthameData.abilityName(power.abilityId()) + " (proficiency " + power.proficiency() + ")").withStyle(power.demonic() ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.literal("  Taken from " + power.sourceName()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.literal("  Use to take it in").withStyle(ChatFormatting.DARK_GRAY));
	}
}
