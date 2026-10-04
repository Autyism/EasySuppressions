package io.github.autyism.easysuppressions.mixin;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.chunk.light.SkyLightStorage$Data")
public interface SkyLightStorageDataAccessor {
	@Accessor("minSectionY")
	int easysuppressions$getMinSectionY();

	@Accessor("columnToTopSection")
	Long2IntOpenHashMap easysuppressions$getColumnToTopSection();
}
