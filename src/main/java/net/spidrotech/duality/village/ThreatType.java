package net.spidrotech.duality.village;

/**
 * What a village has to survive an event with. Splitting this out is what stops "defense" from
 * being one number: a wall is worth nothing against a curse, and a coven's wards are worth nothing
 * against a hundred bandits with ladders. It also gives the player two distinct ways to reinforce
 * a village, and two distinct ways to get it wrong.
 */
public enum ThreatType {
	/** Steel and claws. Answered by militia and fortification. */
	PHYSICAL,
	/** Curses, possession, blood magic. Answered by wards, and barely dented by walls. */
	MAGICAL,
	/** Fear, coercion, corruption, a bad harvest's worth of desperation. Answered by morale and
	 *  prosperity - a rich, confident village shrugs off what a starving one doesn't. */
	SOCIAL,
	/** Not a threat at all; a boon event that lands without being resisted. */
	NONE
}
