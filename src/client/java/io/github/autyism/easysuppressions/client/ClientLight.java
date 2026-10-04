package io.github.autyism.easysuppressions.client;

import io.github.autyism.easysuppressions.light.LightChannel;
import io.github.autyism.easysuppressions.light.LightZone;
import io.github.autyism.easysuppressions.light.LightZoneSet;
import io.github.autyism.easysuppressions.mixin.ChunkLightProviderAccessor;
import io.github.autyism.easysuppressions.mixin.LightStorageAccessor;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the light section the client draws with, when a light zone covers part of it: a copy of
 * the real section with the zone's values written in. Sodium reads light per section through
 * {@code getLightSection}, so this is what makes the suppressed light visible with Sodium; vanilla
 * meshing asks per block and is handled by the getLightLevel hook.
 */
final class ClientLight {
	private ClientLight() {
	}

	static ChunkNibbleArray buildSection(ChunkLightProvider<?, ?> provider, LightZoneSet set, LightChannel channel,
			ChunkSectionPos pos, @Nullable ChunkNibbleArray original) {
		ChunkNibbleArray out;
		int baseX = pos.getMinX();
		int baseY = pos.getMinY();
		int baseZ = pos.getMinZ();
		if (original != null) {
			out = original.copy();
		} else {
			// No stored section: start from the real per-block values (vanilla derives empty sky
			// sections from the section above).
			out = new ChunkNibbleArray();
			LightStorageAccessor raw = (LightStorageAccessor) ((ChunkLightProviderAccessor) provider).easysuppressions$getLightStorage();
			for (int ly = 0; ly < 16; ly++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						out.set(lx, ly, lz, raw.easysuppressions$getLight(BlockPos.asLong(baseX + lx, baseY + ly, baseZ + lz)));
					}
				}
			}
		}
		for (LightZone zone : set.index().inColumn(pos.getSectionX(), pos.getSectionZ())) {
			if (!zone.affects(channel)) {
				continue;
			}
			Zone box = zone.zone;
			int x0 = Math.max(box.minX(), baseX);
			int x1 = Math.min(box.maxX(), baseX + 15);
			int y0 = Math.max(box.minY(), baseY);
			int y1 = Math.min(box.maxY(), baseY + 15);
			int z0 = Math.max(box.minZ(), baseZ);
			int z1 = Math.min(box.maxZ(), baseZ + 15);
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						int value = zone.get(channel, x, y, z);
						if (value >= 0) {
							out.set(x - baseX, y - baseY, z - baseZ, value);
						}
					}
				}
			}
		}
		return out;
	}
}
