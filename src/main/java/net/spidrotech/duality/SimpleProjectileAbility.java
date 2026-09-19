package net.spidrotech.duality;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;

/**
 * The one Ability class shared by every projectile-based power - all ~300 of them, potentially -
 * driven entirely by whichever ProjectileDefinition it was built with. INSTANT type: no charge-
 * up, matching "simpler powers, basically just projectiles."
 *
 * Register once at startup - though in practice you'll never call this directly, since
 * ProjectileRegistry#register/registerVariant already do it for you automatically.
 */
public final class SimpleProjectileAbility extends Ability {
	// Rough guess at "chest/arm height" as a fraction of the caster's own height - tune this if
	// it should sit higher or lower. 0.6 = 60% up from feet.
	private static final double CHEST_HEIGHT_FRACTION = 0.6;
	private final ProjectileDefinition definition;

	public SimpleProjectileAbility(ProjectileDefinition definition) {
		super(definition.id(), AbilityType.INSTANT);
		this.definition = definition;
		setCooldown(AbilityValue.constant(definition.cooldownTicks()));
		definition.castConditions().forEach(this::addCastCondition);
	}

	public ProjectileDefinition definition() {
		return definition;
	}

	@Override
	public void onActivate(AbilityContext ctx) {
		LivingEntity caster = ctx.caster();
		if (!(caster.level() instanceof ServerLevel level))
			return;
		AbilityProjectileBase projectile = definition.spawner().apply(level);
		Vec3 chest = caster.position().add(0, caster.getBbHeight() * CHEST_HEIGHT_FRACTION, 0);
		// A caller that knows what it's shooting at (a mob's attack goal) sets a target position and
		// the shot goes straight at it. Everyone else - players - fires along their look, as before.
		// Mobs need the explicit aim: their body rotation lags their target, so their look vector
		// would send the shot off to the side.
		Vec3 direction = ctx.targetPos().map(target -> target.subtract(chest)).filter(v -> v.lengthSqr() > 1.0E-6).map(Vec3::normalize).orElseGet(caster::getLookAngle);
		// Push the spawn point out along the look direction, clearing both the caster's own
		// hitbox and the projectile's own (scaled) width - otherwise it spawns centered inside
		// the caster's body on X/Z. Scales with projectile size, so a huge one doesn't spawn
		// half-clipped back into the caster the way a fixed offset would.
		double scaledWidth = projectile.getType().getDimensions().width() * definition.widthScale();
		double forwardDistance = (caster.getBbWidth() / 2.0) + (scaledWidth / 2.0) + 0.1;
		// Center the projectile (not just its feet-anchor) at roughly chest/arm height on the
		// caster, rather than eye level - previously the entity's feet always sat at eye level,
		// so its visible bulk only ever extended upward from there, worse the bigger the
		// projectile.
		Vec3 centerPoint = chest.add(direction.scale(forwardDistance));
		// Computed from the definition directly (base registered height * heightScale) rather
		// than the entity's own getBbHeight(), since dimensions aren't correctly scaled until
		// configure() runs below - and configure() needs to run AFTER setPos() so its internal
		// previousTickPosition capture reflects the real spawn point, not a placeholder.
		double scaledHeight = projectile.getType().getDimensions().height() * definition.heightScale();
		Vec3 spawnPos = centerPoint.add(0, -scaledHeight / 2.0, 0);
		projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		projectile.configure(definition, caster, direction);
		level.addFreshEntity(projectile);
		ProjectileCastNetwork.announce(caster, definition.id());
	}
}