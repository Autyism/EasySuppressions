package io.github.autyism.easysuppressions.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkLoadingManagerAccessor {
	/** The published map of chunk holders (main thread). */
	@Accessor("chunkHolders")
	Long2ObjectLinkedOpenHashMap<ChunkHolder> easysuppressions$getChunkHolders();
}
