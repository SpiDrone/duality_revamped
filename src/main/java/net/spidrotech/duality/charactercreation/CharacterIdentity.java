package net.spidrotech.duality.charactercreation;

import java.util.UUID;

/**
 * Who a player currently is, in the smallest form worth sending to everyone.
 *
 * <p>The character sheet stays on the server - it has stats, a history and an inventory of choices
 * nobody else needs. What every client does need is the answer to "whose name goes over that head",
 * and that's this: a player id, a character name, and the race, which is there so a screen or a
 * nameplate can style by species later without another round trip.
 *
 * @param present false when the player has no living character - mid-creation, or just dead. The
 *                name then falls back to their account name everywhere.
 */
public record CharacterIdentity(UUID playerId, String characterName, String raceId, boolean present) {

	public static CharacterIdentity absent(UUID playerId) {
		return new CharacterIdentity(playerId, "", "", false);
	}

	public CharacterIdentity {
		characterName = CharacterNameFormat.clean(characterName);
		raceId = raceId == null ? "" : raceId;
	}

	/** Whether there's a name here worth showing instead of the account name. */
	public boolean hasName() {
		return present && !characterName.isEmpty();
	}

	/** The race definition, resolved against whichever catalog copy is local, or null. */
	public RaceDefinition race() {
		return RaceCatalog.get(raceId);
	}
}
