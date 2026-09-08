package net.spidrotech.duality;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.ArrayList;

/**
 * Fluent way to define an ability with lambdas instead of writing a new Ability subclass - the
 * go-to path for the bulk of a large power roster. Reach for extending Ability directly only
 * when a power needs real per-cast state machinery (see OrbAbility).
 */
public final class AbilityBuilder {
	private final ResourceLocation id;
	private final AbilityType type;
	private AbilityValue chargeTime = AbilityValue.constant(0);
	private AbilityValue duration = AbilityValue.constant(0);
	private AbilityValue cooldown = AbilityValue.constant(0);
	private boolean interruptOnDamage = false;
	private Consumer<AbilityContext> onChargeStart = ctx -> {
	};
	private BiConsumer<AbilityContext, Float> onChargeTick = (ctx, progress) -> {
	};
	private Consumer<AbilityContext> onChargeInterrupted = ctx -> {
	};
	private Consumer<AbilityContext> onActivate = ctx -> {
	};
	private Consumer<AbilityContext> onTick = ctx -> {
	};
	private Consumer<AbilityContext> onDeactivate = ctx -> {
	};
	private final List<AbilityCondition> castConditions = new ArrayList<>();
	private final List<AbilityCondition> maintainConditions = new ArrayList<>();

	AbilityBuilder(ResourceLocation id, AbilityType type) {
		this.id = id;
		this.type = type;
	}

	public AbilityBuilder chargeTime(AbilityValue value) {
		this.chargeTime = value;
		return this;
	}

	public AbilityBuilder duration(AbilityValue value) {
		this.duration = value;
		return this;
	}

	public AbilityBuilder cooldown(AbilityValue value) {
		this.cooldown = value;
		return this;
	}

	public AbilityBuilder interruptOnDamage(boolean value) {
		this.interruptOnDamage = value;
		return this;
	}

	public AbilityBuilder castCondition(AbilityCondition condition) {
		castConditions.add(condition);
		return this;
	}

	public AbilityBuilder maintainCondition(AbilityCondition condition) {
		maintainConditions.add(condition);
		return this;
	}

	public AbilityBuilder onChargeStart(Consumer<AbilityContext> callback) {
		this.onChargeStart = callback;
		return this;
	}

	public AbilityBuilder onChargeTick(BiConsumer<AbilityContext, Float> callback) {
		this.onChargeTick = callback;
		return this;
	}

	public AbilityBuilder onChargeInterrupted(Consumer<AbilityContext> callback) {
		this.onChargeInterrupted = callback;
		return this;
	}

	public AbilityBuilder onActivate(Consumer<AbilityContext> callback) {
		this.onActivate = callback;
		return this;
	}

	public AbilityBuilder onTick(Consumer<AbilityContext> callback) {
		this.onTick = callback;
		return this;
	}

	public AbilityBuilder onDeactivate(Consumer<AbilityContext> callback) {
		this.onDeactivate = callback;
		return this;
	}

	public Ability build() {
		Ability ability = new Ability(id, type) {
			@Override
			public void onChargeStart(AbilityContext ctx) {
				onChargeStart.accept(ctx);
			}

			@Override
			public void onChargeTick(AbilityContext ctx, float progress) {
				onChargeTick.accept(ctx, progress);
			}

			@Override
			public void onChargeInterrupted(AbilityContext ctx) {
				onChargeInterrupted.accept(ctx);
			}

			@Override
			public void onActivate(AbilityContext ctx) {
				onActivate.accept(ctx);
			}

			@Override
			public void onTick(AbilityContext ctx) {
				onTick.accept(ctx);
			}

			@Override
			public void onDeactivate(AbilityContext ctx) {
				onDeactivate.accept(ctx);
			}
		};
		ability.setChargeTime(chargeTime);
		ability.setDuration(duration);
		ability.setCooldown(cooldown);
		ability.setInterruptOnDamage(interruptOnDamage);
		castConditions.forEach(ability::addCastCondition);
		maintainConditions.forEach(ability::addMaintainCondition);
		return ability;
	}
}