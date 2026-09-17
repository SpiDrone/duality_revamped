package net.spidrotech.duality.abilities.demon;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;

import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;

/** A named, linked fire - see FireLinkData. */
public record FireWaypoint(ResourceKey<Level> dimension, BlockPos pos) {
	public static final Codec<FireWaypoint> CODEC = RecordCodecBuilder.create(instance -> instance.group( //
			ResourceKey.codec(net.minecraft.core.registries.Registries.DIMENSION).fieldOf("dimension").forGetter(FireWaypoint::dimension), //
			BlockPos.CODEC.fieldOf("pos").forGetter(FireWaypoint::pos) //
	).apply(instance, FireWaypoint::new));
}
