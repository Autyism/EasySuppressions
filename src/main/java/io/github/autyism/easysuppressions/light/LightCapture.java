package io.github.autyism.easysuppressions.light;

import io.github.autyism.easysuppressions.EasySuppressions;
import io.github.autyism.easysuppressions.mixin.ChunkLightProviderAccessor;
import io.github.autyism.easysuppressions.mixin.LightStorageAccessor;
import io.github.autyism.easysuppressions.mixin.SkyLightStorageDataAccessor;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.ChunkLightingView;
import net.minecraft.world.chunk.light.LightStorage;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Records the real light of one chunk column inside a zone ("lock current").
 *
 * <p>The values must be exactly what a vanilla light query returns at this moment. Calling the
 * vanilla query for every block is exact but slow (several hash lookups per block), so each
 * section is read straight from the light engine's published storage with the same rules the
 * vanilla query uses (see {@code BlockLightStorage.getLight} and {@code SkyLightStorage.getLight}).
 * A sample of blocks is then compared with the real query; if anything differs (another mod
 * replaced the light engine), the column is recorded block by block through the real query.
 */
public final class LightCapture {
	private static final int SAMPLES_PER_SECTION = 24;
	private static boolean warnedMismatch;

	private LightCapture() {
	}

	/** Recorded light of column (cx, cz) for the channels in LOCK mode, or null if the column has no part of the zone in the world. */
	@Nullable
	public static ColumnData capture(World world, int cx, int cz, Zone zone, LightSettings settings) {
		LightingProvider provider = world.getLightingProvider();
		int bottomSection = provider.getBottomY();
		int topSection = provider.getTopY() - 1;
		int minSy = Math.max(zone.minY() >> 4, bottomSection);
		int maxSy = Math.min(zone.maxY() >> 4, topSection);
		if (minSy > maxSy) {
			return new ColumnData(0, settings.sky().mode() == ChannelMode.LOCK ? new byte[0][] : null,
					settings.block().mode() == ChannelMode.LOCK ? new byte[0][] : null);
		}
		byte[][] sky = settings.sky().mode() == ChannelMode.LOCK ? captureChannel(provider, LightType.SKY, cx, cz, minSy, maxSy, zone) : null;
		byte[][] block = settings.block().mode() == ChannelMode.LOCK ? captureChannel(provider, LightType.BLOCK, cx, cz, minSy, maxSy, zone) : null;
		return new ColumnData(minSy, sky, block);
	}

	private static byte[][] captureChannel(LightingProvider provider, LightType type, int cx, int cz, int minSy, int maxSy, Zone zone) {
		ChunkLightingView view = provider.get(type);
		byte[][] out = new byte[maxSy - minSy + 1][];
		if (!(view instanceof ChunkLightProviderAccessor accessor)) {
			// No light of this type in this dimension (e.g. sky light in the Nether): queries return 0.
			byte[] zero = {0};
			for (int i = 0; i < out.length; i++) {
				out[i] = zero;
			}
			return out;
		}
		LightStorage<?> storage = accessor.easysuppressions$getLightStorage();
		LightStorageAccessor raw = (LightStorageAccessor) storage;
		byte[] values = new byte[4096];
		for (int sy = minSy; sy <= maxSy; sy++) {
			boolean fast = false;
			try {
				fast = type == LightType.BLOCK ? readBlockSection(raw, cx, sy, cz, values) : readSkySection(raw, cx, sy, cz, values);
			} catch (RuntimeException | LinkageError e) {
				fast = false;
			}
			if (!fast || !matchesSample(raw, cx, sy, cz, values, zone)) {
				if (fast && !warnedMismatch) {
					warnedMismatch = true;
					EasySuppressions.LOGGER.warn("Light engine storage differs from light queries (another mod changes lighting?); recording block by block");
				}
				readSlow(raw, cx, sy, cz, values);
			}
			maskOutside(values, cx, sy, cz, zone);
			out[sy - minSy] = ColumnData.pack(values);
		}
		return out;
	}

	/** Same as {@code BlockLightStorage.getLight}: the published section array, or 0 when there is none. */
	private static boolean readBlockSection(LightStorageAccessor raw, int cx, int sy, int cz, byte[] values) {
		ChunkToNibbleArrayMap<?> data = raw.easysuppressions$getUncachedStorage();
		ChunkNibbleArray array = data.get(ChunkSectionPos.asLong(cx, sy, cz));
		fillFromArray(array, values, 0);
		return true;
	}

	/** Same as {@code SkyLightStorage.getLight(pos, false)}. */
	private static boolean readSkySection(LightStorageAccessor raw, int cx, int sy, int cz, byte[] values) {
		ChunkToNibbleArrayMap<?> data = raw.easysuppressions$getUncachedStorage();
		if (!(data instanceof SkyLightStorageDataAccessor sky)) {
			return false;
		}
		long section = ChunkSectionPos.asLong(cx, sy, cz);
		int top = sky.easysuppressions$getColumnToTopSection().get(ChunkSectionPos.withZeroY(section));
		int min = sky.easysuppressions$getMinSectionY();
		if (top == min || sy >= top) {
			java.util.Arrays.fill(values, (byte) 15);
			return true;
		}
		ChunkNibbleArray array = data.get(section);
		if (array != null) {
			fillFromArray(array, values, 15);
			return true;
		}
		// Empty section: every block takes the bottom layer of the first stored section above,
		// or 15 if there is none below the top of the column.
		int s = sy;
		long l = section;
		while (array == null) {
			if (++s >= top) {
				java.util.Arrays.fill(values, (byte) 15);
				return true;
			}
			l = ChunkSectionPos.offset(l, net.minecraft.util.math.Direction.UP);
			array = data.get(l);
		}
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				byte v = (byte) array.get(lx, 0, lz);
				for (int ly = 0; ly < 16; ly++) {
					values[ly << 8 | lz << 4 | lx] = v;
				}
			}
		}
		return true;
	}

	private static void fillFromArray(@Nullable ChunkNibbleArray array, byte[] values, int fallback) {
		if (array == null) {
			java.util.Arrays.fill(values, (byte) 0);
			return;
		}
		for (int ly = 0; ly < 16; ly++) {
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					values[ly << 8 | lz << 4 | lx] = (byte) array.get(lx, ly, lz);
				}
			}
		}
	}

	private static boolean matchesSample(LightStorageAccessor raw, int cx, int sy, int cz, byte[] values, Zone zone) {
		Random random = Random.create(ChunkSectionPos.asLong(cx, sy, cz));
		int baseX = cx << 4;
		int baseY = sy << 4;
		int baseZ = cz << 4;
		for (int n = 0; n < SAMPLES_PER_SECTION; n++) {
			int i = n == 0 ? 0 : n == 1 ? 4095 : random.nextInt(4096);
			int lx = i & 15;
			int lz = i >> 4 & 15;
			int ly = i >> 8;
			int real = raw.easysuppressions$getLight(BlockPos.asLong(baseX + lx, baseY + ly, baseZ + lz));
			if (real != values[i]) {
				return false;
			}
		}
		return true;
	}

	private static void readSlow(LightStorageAccessor raw, int cx, int sy, int cz, byte[] values) {
		int baseX = cx << 4;
		int baseY = sy << 4;
		int baseZ = cz << 4;
		for (int ly = 0; ly < 16; ly++) {
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					values[ly << 8 | lz << 4 | lx] = (byte) raw.easysuppressions$getLight(BlockPos.asLong(baseX + lx, baseY + ly, baseZ + lz));
				}
			}
		}
	}

	/** Blocks outside the zone are never asked for; zero them so more sections compress to one byte. */
	private static void maskOutside(byte[] values, int cx, int sy, int cz, Zone zone) {
		int baseX = cx << 4;
		int baseY = sy << 4;
		int baseZ = cz << 4;
		if (zone.minX() <= baseX && zone.maxX() >= baseX + 15 && zone.minY() <= baseY && zone.maxY() >= baseY + 15
				&& zone.minZ() <= baseZ && zone.maxZ() >= baseZ + 15) {
			return;
		}
		for (int ly = 0; ly < 16; ly++) {
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					if (!zone.contains(baseX + lx, baseY + ly, baseZ + lz)) {
						values[ly << 8 | lz << 4 | lx] = 0;
					}
				}
			}
		}
	}

	/** Raw vanilla light at a block, bypassing every override. Used by tests and the client view. */
	public static int rawLight(World world, LightType type, BlockPos pos) {
		ChunkLightingView view = world.getLightingProvider().get(type);
		if (view instanceof ChunkLightProviderAccessor accessor) {
			return ((LightStorageAccessor) accessor.easysuppressions$getLightStorage()).easysuppressions$getLight(pos.asLong());
		}
		return view.getLightLevel(pos);
	}
}
