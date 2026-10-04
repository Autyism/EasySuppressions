package io.github.autyism.easysuppressions.light;

import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.jetbrains.annotations.Nullable;

/** Hooks the client fills in (common code cannot reference client classes). */
public final class LightHooks {
	/** Builds the light section the client draws with (see ChunkLightProviderMixin). */
	public static volatile SectionOverride clientSectionOverride;

	private LightHooks() {
	}

	@FunctionalInterface
	public interface SectionOverride {
		ChunkNibbleArray apply(ChunkLightProvider<?, ?> provider, LightZoneSet set, LightChannel channel, ChunkSectionPos pos, @Nullable ChunkNibbleArray original);
	}
}
