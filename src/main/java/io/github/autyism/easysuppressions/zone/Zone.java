package io.github.autyism.easysuppressions.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

/**
 * An axis-aligned box of blocks, both corners inclusive. The dimension is stored next to it
 * wherever it matters (a zone always lives in the same dimension as its block).
 */
public record Zone(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	public static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("min").forGetter(Zone::min),
			BlockPos.CODEC.fieldOf("max").forGetter(Zone::max)
	).apply(i, Zone::of));

	public static final PacketCodec<ByteBuf, Zone> PACKET_CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, Zone::min,
			BlockPos.PACKET_CODEC, Zone::max,
			Zone::of);

	public static final PacketCodec<ByteBuf, java.util.Optional<Zone>> OPTIONAL_PACKET_CODEC = PacketCodecs.optional(PACKET_CODEC);

	public Zone {
		if (minX > maxX || minY > maxY || minZ > maxZ) {
			throw new IllegalArgumentException("min > max");
		}
	}

	/** The box spanned by two corners given in any order. */
	public static Zone of(BlockPos a, BlockPos b) {
		return new Zone(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
				Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
	}

	public BlockPos min() {
		return new BlockPos(minX, minY, minZ);
	}

	public BlockPos max() {
		return new BlockPos(maxX, maxY, maxZ);
	}

	public boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	public boolean contains(BlockPos pos) {
		return contains(pos.getX(), pos.getY(), pos.getZ());
	}

	public boolean intersects(Zone o) {
		return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
	}

	/** Whether the zone touches the 16x16x16 section at section coordinates (sx, sy, sz). */
	public boolean intersectsSection(int sx, int sy, int sz) {
		return minX <= (sx << 4) + 15 && maxX >= sx << 4
				&& minY <= (sy << 4) + 15 && maxY >= sy << 4
				&& minZ <= (sz << 4) + 15 && maxZ >= sz << 4;
	}

	public boolean intersectsColumn(int cx, int cz) {
		return minX <= (cx << 4) + 15 && maxX >= cx << 4 && minZ <= (cz << 4) + 15 && maxZ >= cz << 4;
	}

	public int minChunkX() {
		return minX >> 4;
	}

	public int maxChunkX() {
		return maxX >> 4;
	}

	public int minChunkZ() {
		return minZ >> 4;
	}

	public int maxChunkZ() {
		return maxZ >> 4;
	}

	public long sizeX() {
		return (long) maxX - minX + 1;
	}

	public long sizeY() {
		return (long) maxY - minY + 1;
	}

	public long sizeZ() {
		return (long) maxZ - minZ + 1;
	}

	/** Number of chunk columns the zone touches. */
	public long columnCount() {
		return ((long) maxChunkX() - minChunkX() + 1) * ((long) maxChunkZ() - minChunkZ() + 1);
	}

	public String describe() {
		return "(" + minX + ", " + minY + ", " + minZ + ") ~ (" + maxX + ", " + maxY + ", " + maxZ + ")";
	}
}
