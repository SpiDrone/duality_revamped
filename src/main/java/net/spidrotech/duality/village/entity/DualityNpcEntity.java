package net.spidrotech.duality.village.entity;

import net.spidrotech.duality.village.NpcRecord;
import net.spidrotech.duality.village.VillageRecord;
import net.spidrotech.duality.village.Villages;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The entity a village NPC record is rendered as - the piece VillageNpcTypes has been waiting for.
 *
 * <p>The record is still the truth: this class holds no state of its own beyond the id of the
 * record it belongs to, and anything worth remembering (job, status, fate, what happened to them)
 * lives in the file. That is deliberate, and it is what lets a raid despawn this entity, carry the
 * record off to a clan hold and hand back a prisoner - or a corpse - days later.
 *
 * <p>So treat {@link #record()} as the source of truth and this entity as a view of it. The synced
 * job and species fields exist only so the client can label and (later) skin the NPC without
 * needing the server's files.
 *
 * <p>Registering this as {@code duality:npc} is all it took to stop villages populating with
 * vanilla villagers: VillageNpcTypes resolves the most specific registered id for a record's
 * species and job, and only falls back to {@code minecraft:villager} when the mod has none. Adding
 * variants later (a {@code duality:npc_vampire_guard}) needs no migration - existing records pick
 * the new entity up the next time they spawn.
 */
public class DualityNpcEntity extends PathfinderMob {
	private static final EntityDataAccessor<String> DATA_NPC_ID = SynchedEntityData.defineId(DualityNpcEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> DATA_JOB = SynchedEntityData.defineId(DualityNpcEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> DATA_SPECIES = SynchedEntityData.defineId(DualityNpcEntity.class, EntityDataSerializers.STRING);

	public DualityNpcEntity(EntityType<? extends DualityNpcEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_NPC_ID, "");
		builder.define(DATA_JOB, "Idle");
		builder.define(DATA_SPECIES, "Human");
	}

	@Override
	protected void registerGoals() {
		// Every goal that knows anything about village life reads it from the record, so an NPC
		// whose village changes under them (abduction, a village deleted) behaves correctly without
		// anything having to notify the entity. Standing a shift at a workstation is the next layer
		// and wants a job-to-building mapping, which doesn't exist yet.
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.2));
		this.goalSelector.addGoal(2, new NpcStayHomeGoal(this, 0.7));
		this.goalSelector.addGoal(3, new NpcVisitPantryGoal(this, 0.6));
		this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8f));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 24);
	}

	/** Points this entity at a record and copies across the parts the client needs to see. */
	public void bindTo(NpcRecord npc) {
		if (npc == null)
			return;
		this.entityData.set(DATA_NPC_ID, npc.npcId());
		this.entityData.set(DATA_JOB, npc.job().name());
		this.entityData.set(DATA_SPECIES, npc.species());
	}

	public String npcId() {
		return this.entityData.get(DATA_NPC_ID);
	}

	public String jobId() {
		return this.entityData.get(DATA_JOB);
	}

	public String species() {
		return this.entityData.get(DATA_SPECIES);
	}

	/**
	 * The record behind this entity, or null if there isn't one - which is normal for an NPC spawned
	 * with a spawn egg, and for one whose village was deleted out from under it.
	 */
	public NpcRecord record() {
		String id = this.npcId();
		if (id.isEmpty() || !Villages.isReady())
			return null;
		return Villages.findNpc(id);
	}

	/**
	 * The village this NPC is currently in, or null - including when the village is real but sits in
	 * another dimension, since every goal here compares raw coordinates and would otherwise walk the
	 * NPC toward a position that means nothing in the world they're standing in.
	 */
	public VillageRecord village() {
		NpcRecord npc = this.record();
		if (npc == null)
			return null;
		VillageRecord village = Villages.store().village(npc.currentVillageId());
		if (village == null || village.center() == null)
			return null;
		return village.center().dimension().equals(this.level().dimension().location().toString()) ? village : null;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		// A named resident is referenced by their village's file. Letting the mob cap delete one
		// would leave a record pointing at an entity that no longer exists.
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("NpcId", this.npcId());
		tag.putString("NpcJob", this.jobId());
		tag.putString("NpcSpecies", this.species());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_NPC_ID, tag.getString("NpcId"));
		if (tag.contains("NpcJob"))
			this.entityData.set(DATA_JOB, tag.getString("NpcJob"));
		if (tag.contains("NpcSpecies"))
			this.entityData.set(DATA_SPECIES, tag.getString("NpcSpecies"));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VILLAGER_DEATH;
	}
}
