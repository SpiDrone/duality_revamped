package net.spidrotech.duality.world.structure;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * A jigsaw structure that only generates when at least one of its doors opens
 * straight into a natural cavern.
 *
 * Doors are marked in the templates with a jigsaw block named {@code door_jigsaw},
 * pointing at {@code minecraft:empty} so assembly ignores it, facing out of the
 * structure. After assembling a layout, each door is tested against the raw
 * noise terrain: the first {@code door_clearance} blocks in front of it must
 * have two blocks of walkable air within one block of the door's floor level.
 *
 * A failed layout is retried from a different start offset and rotation up to
 * {@code placement_attempts} times before the chunk is given up on. Retries
 * draw from the chunk's worldgen random, so /locate and real generation agree.
 *
 * Doors on lower levels are allowed; they simply never pass the test, so the
 * main level still has to be the one that reaches the cavern.
 */
public class CavernJigsawStructure extends Structure {
	public static final MapCodec<CavernJigsawStructure> CODEC = RecordCodecBuilder.<CavernJigsawStructure>mapCodec(i -> i.group(
			settingsCodec(i),
			StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
			Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.maxDepth),
			HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
			Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
			ResourceLocation.CODEC.fieldOf("door_jigsaw").forGetter(s -> s.doorJigsaw),
			Codec.intRange(1, 32).optionalFieldOf("placement_attempts", 8).forGetter(s -> s.placementAttempts),
			Codec.intRange(1, 8).optionalFieldOf("door_clearance", 3).forGetter(s -> s.doorClearance)
	).apply(i, CavernJigsawStructure::new));

	private final Holder<StructureTemplatePool> startPool;
	private final int maxDepth;
	private final HeightProvider startHeight;
	private final int maxDistanceFromCenter;
	private final ResourceLocation doorJigsaw;
	private final int placementAttempts;
	private final int doorClearance;

	public CavernJigsawStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int maxDepth, HeightProvider startHeight,
			int maxDistanceFromCenter, ResourceLocation doorJigsaw, int placementAttempts, int doorClearance) {
		super(settings);
		this.startPool = startPool;
		this.maxDepth = maxDepth;
		this.startHeight = startHeight;
		this.maxDistanceFromCenter = maxDistanceFromCenter;
		this.doorJigsaw = doorJigsaw;
		this.placementAttempts = placementAttempts;
		this.doorClearance = doorClearance;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		WorldGenerationContext heightContext = new WorldGenerationContext(context.chunkGenerator(), context.heightAccessor());

		for (int attempt = 0; attempt < placementAttempts; attempt++) {
			int y = startHeight.sample(context.random(), heightContext);
			BlockPos origin = new BlockPos(chunk.getBlockX(context.random().nextInt(16)), y, chunk.getBlockZ(context.random().nextInt(16)));

			Optional<GenerationStub> stub = JigsawPlacement.addPieces(context, startPool, Optional.empty(), maxDepth, origin, false,
					Optional.empty(), maxDistanceFromCenter, PoolAliasLookup.EMPTY, DimensionPadding.ZERO, LiquidSettings.IGNORE_WATERLOGGING);
			if (stub.isEmpty())
				continue;

			// Assemble now rather than lazily so the layout can be inspected; the
			// built pieces are handed back so generation doesn't assemble twice.
			StructurePiecesBuilder builder = stub.get().getPiecesBuilder();
			if (hasCavernDoor(context, builder))
				return Optional.of(new GenerationStub(stub.get().position(), Either.right(builder)));
		}
		return Optional.empty();
	}

	private boolean hasCavernDoor(GenerationContext context, StructurePiecesBuilder builder) {
		// Separate random: jigsaw lookup shuffles, and that must not perturb worldgen.
		RandomSource scratch = RandomSource.create(0L);
		String doorName = doorJigsaw.toString();

		for (StructurePiece piece : builder.build().pieces()) {
			if (!(piece instanceof PoolElementStructurePiece poolPiece))
				continue;
			for (StructureTemplate.StructureBlockInfo jigsaw : poolPiece.getElement().getShuffledJigsawBlocks(context.structureTemplateManager(),
					poolPiece.getPosition(), poolPiece.getRotation(), scratch)) {
				if (jigsaw.nbt() == null || !doorName.equals(jigsaw.nbt().getString("name")))
					continue;
				if (opensIntoCavern(context, jigsaw.pos(), JigsawBlock.getFrontFacing(jigsaw.state())))
					return true;
			}
		}
		return false;
	}

	private boolean opensIntoCavern(GenerationContext context, BlockPos door, Direction facing) {
		if (facing.getAxis().isVertical())
			return false;
		for (int step = 1; step <= doorClearance; step++) {
			BlockPos ahead = door.relative(facing, step);
			NoiseColumn column = context.chunkGenerator().getBaseColumn(ahead.getX(), ahead.getZ(), context.heightAccessor(), context.randomState());
			if (!hasWalkableGap(column, door.getY()))
				return false;
		}
		return true;
	}

	/** Two stacked air blocks with their lower block within one step of the door's floor. */
	private static boolean hasWalkableGap(NoiseColumn column, int doorY) {
		for (int feet = doorY - 1; feet <= doorY + 1; feet++) {
			if (column.getBlock(feet).isAir() && column.getBlock(feet + 1).isAir())
				return true;
		}
		return false;
	}

	@Override
	public StructureType<?> type() {
		return DualityStructureTypes.CAVERN_JIGSAW;
	}
}
