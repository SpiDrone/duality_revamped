package net.spidrotech.duality.faction;

/**
 * A member's permission tier within their faction - orthogonal to any personal power rank a member
 * might separately hold (a vampire's VampireRank, say). This is "what they're allowed to do to the
 * faction itself" (invite, kick, claim a settlement, promote), not "how strong they are".
 *
 * <p>Display flavor ("Queen", "Underling") is a free-text title carried alongside this on
 * {@link FactionMember}, not a fourth role - a Queen and a rank-and-file Underling can both be
 * LEADER/MEMBER mechanically while reading completely differently in chat.
 */
public enum FactionRole {
	LEADER, OFFICER, MEMBER;

	public boolean canManageMembers() {
		return this == LEADER || this == OFFICER;
	}

	public boolean canManageSettlements() {
		return this == LEADER || this == OFFICER;
	}

	public boolean outranks(FactionRole other) {
		return this.ordinal() < other.ordinal();
	}

	public static FactionRole parse(String raw) {
		if (raw == null)
			return MEMBER;
		for (FactionRole role : values()) {
			if (role.name().equalsIgnoreCase(raw))
				return role;
		}
		return MEMBER;
	}
}
