package io.github.autyism.easysuppressions.mixin;

import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkLightProvider.class)
public interface ChunkLightProviderAccessor {
	@Accessor("lightStorage")
	LightStorage<?> easysuppressions$getLightStorage();

	@Accessor("chunkProvider")
	ChunkProvider easysuppressions$getChunkProvider();
}
