package net.spidrotech.duality.faction;

import com.google.gson.JsonObject;

/**
 * One entry on a faction's roster. memberId is a character id ("char_...", a player's character -
 * see DualityDatabaseManager) or an npc id ("npc_...", see NpcRecord) - the two are deliberately
 * interchangeable everywhere in this package, which is what lets a player-led faction and an
 * NPC-led one (a vampire queen's court, say) run through identical code.
 */
public class FactionMember {
	private final String memberId;
	private FactionRole role;
	/** Display flavor over the mechanical role - "Queen", "Underling", "Knight". Falls back to
	 *  the role's own name when blank. */
	private String title;
	private final long joinedDay;

	public FactionMember(String memberId, FactionRole role, String title, long joinedDay) {
		this.memberId = memberId;
		this.role = role;
		this.title = title == null ? "" : title;
		this.joinedDay = joinedDay;
	}

	public String memberId() {
		return memberId;
	}

	public FactionRole role() {
		return role;
	}

	public void setRole(FactionRole role) {
		this.role = role == null ? FactionRole.MEMBER : role;
	}

	public String title() {
		return title.isEmpty() ? displayRole() : title;
	}

	public void setTitle(String title) {
		this.title = title == null ? "" : title;
	}

	private String displayRole() {
		return switch (role) {
			case LEADER -> "Leader";
			case OFFICER -> "Officer";
			case MEMBER -> "Member";
		};
	}

	public long joinedDay() {
		return joinedDay;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("member_id", memberId);
		json.addProperty("role", role.name());
		json.addProperty("title", title);
		json.addProperty("joined_day", joinedDay);
		return json;
	}

	public static FactionMember fromJson(JsonObject json) {
		return new FactionMember(json.get("member_id").getAsString(), FactionRole.parse(json.has("role") ? json.get("role").getAsString() : null),
				json.has("title") ? json.get("title").getAsString() : "", json.has("joined_day") ? json.get("joined_day").getAsLong() : 0L);
	}
}
