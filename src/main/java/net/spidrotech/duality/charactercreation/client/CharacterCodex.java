package net.spidrotech.duality.charactercreation.client;

import net.spidrotech.duality.charactercreation.AbilityDefinition;
import net.spidrotech.duality.charactercreation.CreationStep;
import net.spidrotech.duality.charactercreation.RaceDefinition;
import net.spidrotech.duality.charactercreation.SkillType;
import net.spidrotech.duality.charactercreation.SubraceDefinition;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

/**
 * Text for the creator's codex panel - shared by every screen that has one, describing whichever
 * pick that screen is actually about: the race on screen one, the lineage on screen two.
 * {@link ClientCharacterCreation#step()} is what decides which, since it's kept in step with which
 * screen is physically open (see CharacterCreatorNavigation) - there's no need for a screen to say
 * "describe my race" vs "describe my lineage" itself.
 *
 * READS THE PREVIEW, NOT THE DRAFT, on both screens. {@link ClientCharacterCreation#previewedRace()}
 * and {@link ClientCharacterCreation#previewedSubrace()} are the last row clicked, whether or not the
 * server actually accepted the pick (a locked pick clears the draft's choice rather than keeping the
 * old one - see CharacterCreation#selectRace) - which matters because a player clicking a locked row
 * to see what it is would otherwise watch the panel do nothing. Reading the preview instead means
 * every row is clickable for its blurb even when it isn't yet pickable, with {@link #warningLine()}
 * saying so - that refusal used to only ever reach the player as a chat/actionbar message, which the
 * creator's own screen sits directly on top of and hides completely; the codex is what actually gets
 * seen.
 *
 * <p>{@link #warningLine()} is deliberately its own method rather than a line appended onto
 * {@link #description()}: a screen draws it as a SEPARATE label in a flat red, below wherever the
 * description actually finished wrapping, rather than trying to color part of one drawn string - see
 * CharacterSelectorScreen/CharacterSelectorPage2Screen's renderLabels for how the two calls chain
 * (the description draw returns how many lines it used).
 *
 * Every string comes from RaceCatalog, so adding a race or lineage to that catalog gives it a codex
 * entry for free - there is deliberately no text here to keep in sync. To change what a panel says,
 * edit its displayName/description in RaceCatalog/SubraceDefinition, which are already the files
 * meant for that.
 *
 * Empty string, never null and never the word "null", is the "nothing picked yet" (or "nothing to
 * warn about") answer - it's what a text helper can draw harmlessly.
 */
@OnlyIn(Dist.CLIENT)
public final class CharacterCodex {
	private CharacterCodex() {
	}

	/** The previewed row's display name - a race's on screen one, a lineage's on screen two - or ""
	 *  if nothing's been clicked yet. Note this is the presentable name, not the internal id the
	 *  button carries ("human"). */
	public static String title() {
		if (ClientCharacterCreation.step() == CreationStep.SKILLS) {
			SkillType skill = ClientCharacterCreation.previewedSkill();
			return skill == null ? "" : skill.displayName();
		}
		if (ClientCharacterCreation.step() == CreationStep.ABILITIES) {
			AbilityDefinition ability = ClientCharacterCreation.previewedAbility();
			return ability == null ? "" : ability.codexTitle();
		}
		if (ClientCharacterCreation.step() == CreationStep.SUBRACE) {
			SubraceDefinition subrace = ClientCharacterCreation.previewedSubrace();
			return subrace == null ? "" : subrace.displayName();
		}
		RaceDefinition race = ClientCharacterCreation.previewedRace();
		return race == null ? "" : race.displayName();
	}

	/** The previewed row's own blurb, or "" if nothing's been clicked yet - never the lock reason, see
	 *  {@link #warningLine()} for that. */
	public static String description() {
		if (ClientCharacterCreation.step() == CreationStep.SKILLS) {
			SkillType skill = ClientCharacterCreation.previewedSkill();
			return skill == null ? "" : skill.description();
		}
		if (ClientCharacterCreation.step() == CreationStep.ABILITIES) {
			AbilityDefinition ability = ClientCharacterCreation.previewedAbility();
			return ability == null ? "" : ability.codexDescription();
		}
		if (ClientCharacterCreation.step() == CreationStep.SUBRACE) {
			SubraceDefinition subrace = ClientCharacterCreation.previewedSubrace();
			return subrace == null ? "" : subrace.description();
		}
		RaceDefinition race = ClientCharacterCreation.previewedRace();
		return race == null ? "" : race.description();
	}

	/**
	 * "" if the previewed row can currently be picked; otherwise a line explaining why not.
	 *
	 * <p>On screens one and two this is worded to the SPECIFIC unmet requirement (see
	 * {@link RaceDefinition#requirementId()}/{@link SubraceDefinition#requirementId()}) rather than
	 * one generic line for every lock - a Vampire hint reads nothing like an Angelic one, because the
	 * thing standing in the way isn't the same thing. Screen three has no such requirement to name -
	 * a power is only ever held back by its cost, already visible as the points-remaining label, so
	 * this just says so plainly rather than inventing a reason.
	 */
	public static String warningLine() {
		if (ClientCharacterCreation.step() == CreationStep.SKILLS)
			return "";
		if (ClientCharacterCreation.step() == CreationStep.ABILITIES) {
			AbilityDefinition ability = ClientCharacterCreation.previewedAbility();
			if (ability == null || ClientCharacterCreation.hasChosen(ability.id()) || ClientCharacterCreation.grantedAbilities().contains(ability)
					|| ability.pointCost() <= ClientCharacterCreation.pointsRemaining())
				return "";
			return "Not enough points left for that..";
		}
		if (ClientCharacterCreation.step() == CreationStep.SUBRACE) {
			SubraceDefinition subrace = ClientCharacterCreation.previewedSubrace();
			if (subrace == null || ClientCharacterCreation.canSelectPreviewedSubrace())
				return "";
			return warningFor(subrace.requirementId());
		}
		RaceDefinition race = ClientCharacterCreation.previewedRace();
		if (race == null || ClientCharacterCreation.canSelectPreviewed())
			return "";
		// Not unlocked at all reads differently from unlocked-but-conditions-unmet.
		if (!ClientCharacterCreation.isUnlocked(race))
			return "You haven't discovered this path yet..";
		return warningFor(race.requirementId());
	}

	/** requirementId -> the hint that actually explains it. "" covers a lock that isn't a
	 *  requirementId miss at all (a race that's simply not in unlocked_species yet), so it still gets
	 *  a line rather than a blank one. */
	private static String warningFor(String requirementId) {
		return switch (requirementId) {
			case "vampire_lineage" -> "A link of blood and soul has yet to be made..";
			case "not_previously_evil" -> "Maybe one day you will prove you're worthy..";
			default -> "You haven't earned the right to this yet..";
		};
	}
}
