package io.github.autyism.easysuppressions.mixin;

import net.minecraft.world.chunk.ChunkToNibbleArrayMap;
import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LightStorage.class)
public interface LightStorageAccessor {
	/** The published copy that light queries read. */
	@Accessor("uncachedStorage")
	ChunkToNibbleArrayMap<?> easysuppressions$getUncachedStorage();

	/** Exactly what a vanilla light query returns, without our hook. */
	@Invoker("getLight")
	int easysuppressions$getLight(long blockPos);
}
