package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.charactercreation.client.ClientCharacterCreation;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * All character-creator networking, in the same shape as SkinNetwork.
 *
 * <pre>
 *   SyncRaceCatalogPayload   S-&gt;C, on join. Every race and lineage, so screens one and two can
 *                            draw options without hard-coding any.
 *   SyncDraftPayload         S-&gt;C, after every change. The whole DraftView - what's chosen, what's
 *                            left to spend, which steps are ticked, and a message to show.
 *   CreationActionPayload    C-&gt;S. One packet for every button on every screen. VALIDATED: the
 *                            server re-runs every rule, so this is a request, not an instruction.
 * </pre>
 *
 * <p>There is deliberately no packet for the skin editor. Screen five uses the existing
 * SkinNetwork.EquipPartPayload, which already works with no active character - SkinManager keeps
 * the loadout in memory mid-creation, and {@link CharacterCreation#commit} writes it to the new
 * sheet. So the live preview is free.
 */
@EventBusSubscriber(modid = "duality")
public final class CharacterCreationNetwork {
	private CharacterCreationNetwork() {
	}

	// ------------------------------------------------------------------------------- payloads
	public record SyncRaceCatalogPayload(List<RaceDefinition> races) implements CustomPacketPayload {
		public static final Type<SyncRaceCatalogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "sync_race_catalog"));
		public static final StreamCodec<FriendlyByteBuf, SyncRaceCatalogPayload> STREAM_CODEC = StreamCodec.of(
				(FriendlyByteBuf buf, SyncRaceCatalogPayload payload) -> {
					buf.writeVarInt(payload.races().size());
					for (RaceDefinition race : payload.races()) {
						writeRace(buf, race);
					}
				}, (FriendlyByteBuf buf) -> {
					int count = buf.readVarInt();
					List<RaceDefinition> races = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						races.add(readRace(buf));
					}
					return new SyncRaceCatalogPayload(List.copyOf(races));
				});

		@Override
		public Type<SyncRaceCatalogPayload> type() {
			return TYPE;
		}
	}

	public record SyncDraftPayload(DraftView view) implements CustomPacketPayload {
		public static final Type<SyncDraftPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "sync_character_draft"));
		public static final StreamCodec<FriendlyByteBuf, SyncDraftPayload> STREAM_CODEC = StreamCodec.of((FriendlyByteBuf buf, SyncDraftPayload payload) -> {
			DraftView view = payload.view();
			buf.writeBoolean(view.active());
			buf.writeVarInt(view.step().ordinal());
			buf.writeUtf(view.raceId());
			buf.writeUtf(view.subraceId());
			writeStrings(buf, view.abilityIds());
			writeStrings(buf, view.grantedAbilityIds());
			writeInts(buf, view.skillValues());
			writeInts(buf, view.allocatedPoints());
			buf.writeVarInt(view.pointsRemaining());
			buf.writeVarInt(view.pointsBudget());
			buf.writeVarInt(view.raceCost());
			buf.writeVarInt(view.abilityPicksRemaining());
			buf.writeUtf(view.name());
			buf.writeBoolean(view.nameUsable());
			buf.writeVarInt(view.completedSteps().size());
			for (CreationStep step : view.completedSteps()) {
				buf.writeVarInt(step.ordinal());
			}
			buf.writeBoolean(view.complete());
			writeStrings(buf, view.selectableRaceIds());
			buf.writeUtf(view.statusMessage());
		}, (FriendlyByteBuf buf) -> {
			boolean active = buf.readBoolean();
			CreationStep step = CreationStep.values()[buf.readVarInt()];
			String raceId = buf.readUtf();
			String subraceId = buf.readUtf();
			List<String> abilities = readStrings(buf);
			List<String> granted = readStrings(buf);
			List<Integer> skills = readInts(buf);
			List<Integer> allocated = readInts(buf);
			int points = buf.readVarInt();
			int budget = buf.readVarInt();
			int raceCost = buf.readVarInt();
			int picks = buf.readVarInt();
			String name = buf.readUtf();
			boolean nameUsable = buf.readBoolean();
			int stepCount = buf.readVarInt();
			List<CreationStep> completed = new ArrayList<>(stepCount);
			for (int i = 0; i < stepCount; i++) {
				completed.add(CreationStep.values()[buf.readVarInt()]);
			}
			boolean complete = buf.readBoolean();
			List<String> selectable = readStrings(buf);
			String message = buf.readUtf();
			return new SyncDraftPayload(new DraftView(active, step, raceId, subraceId, abilities, granted, skills, allocated, points, budget, raceCost, picks, name,
					nameUsable, completed, complete, selectable, message));
		});

		@Override
		public Type<SyncDraftPayload> type() {
			return TYPE;
		}
	}

	/** One packet for every button. See {@link CreationAction} for which fields each action reads. */
	public record CreationActionPayload(CreationAction action, String arg, int amount) implements CustomPacketPayload {
		public static final Type<CreationActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("duality", "character_creation_action"));
		public static final StreamCodec<FriendlyByteBuf, CreationActionPayload> STREAM_CODEC = StreamCodec.of(
				(FriendlyByteBuf buf, CreationActionPayload payload) -> {
					buf.writeVarInt(payload.action().ordinal());
					buf.writeUtf(payload.arg(), 256);
					buf.writeVarInt(payload.amount());
				}, (FriendlyByteBuf buf) -> {
					int ordinal = buf.readVarInt();
					CreationAction action = ordinal >= 0 && ordinal < CreationAction.values().length ? CreationAction.values()[ordinal] : null;
					return new CreationActionPayload(action, buf.readUtf(256), buf.readVarInt());
				});

		@Override
		public Type<CreationActionPayload> type() {
			return TYPE;
		}
	}

	// ----------------------------------------------------------------------------- outgoing
	public static void sendCatalog(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new SyncRaceCatalogPayload(new ArrayList<>(RaceCatalog.all())));
	}

	public static void sendView(ServerPlayer player, DraftView view) {
		PacketDistributor.sendToPlayer(player, new SyncDraftPayload(view));
	}

	/** Tells the client the creator is over, so it can close the screen. */
	public static void sendInactive(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new SyncDraftPayload(DraftView.INACTIVE));
	}

	// --------------------------------------------------------------------------- registration
	@SubscribeEvent
	public static void register(final RegisterPayloadHandlersEvent event) {
		final PayloadRegistrar registrar = event.registrar("duality");
		registrar.playToClient(SyncRaceCatalogPayload.TYPE, SyncRaceCatalogPayload.STREAM_CODEC, CharacterCreationNetwork::handleCatalog);
		registrar.playToClient(SyncDraftPayload.TYPE, SyncDraftPayload.STREAM_CODEC, CharacterCreationNetwork::handleDraft);
		registrar.playToServer(CreationActionPayload.TYPE, CreationActionPayload.STREAM_CODEC, CharacterCreationNetwork::handleAction);
	}

	private static void handleCatalog(final SyncRaceCatalogPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> RaceCatalog.replaceAll(payload.races()));
	}

	private static void handleDraft(final SyncDraftPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> ClientCharacterCreation.accept(payload.view()));
	}

	private static void handleAction(final CreationActionPayload payload, final IPayloadContext context) {
		context.enqueueWork(() -> {
			if (context.player() instanceof ServerPlayer player)
				CharacterCreation.act(player, payload.action(), payload.arg(), payload.amount());
		});
	}

	// -------------------------------------------------------------------------------- hooks
	/**
	 * On join: hand over the catalog, re-apply whatever character they already have, and open the
	 * creator if they need one.
	 */
	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		sendCatalog(player);
		CharacterCreation.applyActiveCharacter(player);
		if (!CharacterCreation.beginIfNeeded(player))
			sendInactive(player);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			CharacterCreation.onPlayerLeave(player);
	}

	/**
	 * A respawning player gets their stat line put back on - a death respawn builds a fresh entity,
	 * and permanent attribute modifiers don't survive that.
	 *
	 * <p>And if the death was their character's last one, this is where the creator opens again:
	 * a canon death clears the active character, so the player comes back as nobody until they make
	 * someone new.
	 */
	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		CharacterCreation.applyActiveCharacter(player);
		if (!CharacterCreation.beginIfNeeded(player))
			sendInactive(player);
	}

	// -------------------------------------------------------------------------- codec helpers
	private static void writeRace(FriendlyByteBuf buf, RaceDefinition race) {
		buf.writeUtf(race.id());
		buf.writeUtf(race.displayName());
		buf.writeUtf(race.description(), 512);
		buf.writeUtf(race.iconHint());
		writeStrings(buf, race.abilityPool());
		buf.writeVarInt(race.abilityPicks());
		writeInts(buf, race.baseSkills());
		buf.writeBoolean(race.startsUnlocked());
		buf.writeUtf(race.equippedTag());
		buf.writeVarInt(race.pointCost());
		buf.writeVarInt(race.subraces().size());
		for (SubraceDefinition subrace : race.subraces()) {
			writeSubrace(buf, subrace);
		}
	}

	private static RaceDefinition readRace(FriendlyByteBuf buf) {
		String id = buf.readUtf();
		String displayName = buf.readUtf();
		String description = buf.readUtf(512);
		String iconHint = buf.readUtf();
		List<String> pool = readStrings(buf);
		int picks = buf.readVarInt();
		List<Integer> baseSkills = readInts(buf);
		boolean startsUnlocked = buf.readBoolean();
		String tag = buf.readUtf();
		int pointCost = buf.readVarInt();
		int count = buf.readVarInt();
		List<SubraceDefinition> subraces = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			subraces.add(readSubrace(buf));
		}
		return new RaceDefinition(id, displayName, description, iconHint, subraces, pool, picks, baseSkills, startsUnlocked, tag, pointCost);
	}

	private static void writeSubrace(FriendlyByteBuf buf, SubraceDefinition subrace) {
		buf.writeUtf(subrace.id());
		buf.writeUtf(subrace.displayName());
		buf.writeUtf(subrace.description(), 512);
		buf.writeUtf(subrace.iconHint());
		writeStrings(buf, subrace.grantedAbilities());
		writeStrings(buf, subrace.extraAbilityPool());
		writeInts(buf, subrace.skillBonuses());
		buf.writeVarInt(subrace.pointCost());
	}

	private static SubraceDefinition readSubrace(FriendlyByteBuf buf) {
		return new SubraceDefinition(buf.readUtf(), buf.readUtf(), buf.readUtf(512), buf.readUtf(), readStrings(buf), readStrings(buf), readInts(buf),
				buf.readVarInt());
	}

	private static void writeStrings(FriendlyByteBuf buf, List<String> values) {
		buf.writeVarInt(values.size());
		for (String value : values) {
			buf.writeUtf(value);
		}
	}

	private static List<String> readStrings(FriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<String> values = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			values.add(buf.readUtf());
		}
		return List.copyOf(values);
	}

	private static void writeInts(FriendlyByteBuf buf, List<Integer> values) {
		buf.writeVarInt(values.size());
		for (int value : values) {
			buf.writeVarInt(value);
		}
	}

	private static List<Integer> readInts(FriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<Integer> values = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			values.add(buf.readVarInt());
		}
		return List.copyOf(values);
	}
}
