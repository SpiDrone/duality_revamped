package net.spidrotech.duality;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Per-cast runtime data. A fresh context is created every time {@link AbilityManager} activates
 * an ability, and it lives for as long as that cast is charging/active.
 *
 * caster is a LivingEntity, not specifically a player - mobs/NPCs can cast abilities too.
 */
public final class AbilityContext {
	private final LivingEntity caster;
	private final Ability ability;
	private final Map<String, Object> data = new HashMap<>();
	private final List<Entity> targets = new ArrayList<>();
	private Vec3 targetPos;

	public AbilityContext(LivingEntity caster, Ability ability) {
		this.caster = caster;
		this.ability = ability;
	}

	public LivingEntity caster() {
		return caster;
	}

	public Ability ability() {
		return ability;
	}

	public Map<String, Object> data() {
		return data;
	}

	/** Entities selected to be affected/carried along with this cast (e.g. Orb's passengers). */
	public List<Entity> targets() {
		return targets;
	}

	public void addTarget(Entity entity) {
		targets.add(entity);
	}

	public void removeTarget(Entity entity) {
		targets.remove(entity);
	}

	public Optional<Vec3> targetPos() {
		return Optional.ofNullable(targetPos);
	}

	public void setTargetPos(Vec3 pos) {
		this.targetPos = pos;
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key, T fallback) {
		return (T) data.getOrDefault(key, fallback);
	}

	public void set(String key, Object value) {
		data.put(key, value);
	}
}