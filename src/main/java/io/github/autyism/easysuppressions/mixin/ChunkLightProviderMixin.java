package io.github.autyism.easysuppressions.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.autyism.easysuppressions.light.LightChannel;
import io.github.autyism.easysuppressions.light.LightHooks;
import io.github.autyism.easysuppressions.light.LightZoneSet;
import io.github.autyism.easysuppressions.world.ZoneHolder;
import io.github.autyism.easysuppressions.world.ZoneHolderAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.ChunkSkyLightProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Light suppressor: every light query of the game ends in {@code ChunkLightProvider.getLightLevel}
 * (World.getLightLevel, getBaseLightLevel, isSkyVisible, ...). Inside a locked zone the stored or
 * fixed value is returned instead; the light engine and its data are never touched.
 *
 * <p>On the client, {@code getLightSection} is also adjusted (only for drawing): Sodium copies
 * whole light sections from there instead of asking per block.
 */
@Mixin(ChunkLightProvider.class)
abstract class ChunkLightProviderMixin {
	@Shadow
	@Final
	protected ChunkProvider chunkProvider;

	@Unique
	private volatile ZoneHolder easysuppressions$holder;
	@Unique
	private LightChannel easysuppressions$channel;
	@Unique
	private boolean easysuppressions$client;

	@Unique
	private ZoneHolder easysuppressions$holder() {
		ZoneHolder holder = easysuppressions$holder;
		if (holder == null) {
			BlockView world = chunkProvider.getWorld();
			if (!(world instanceof ZoneHolderAccess access)) {
				return null;
			}
			easysuppressions$channel = ((Object) this) instanceof ChunkSkyLightProvider ? LightChannel.SKY : LightChannel.BLOCK;
			easysuppressions$client = world instanceof World w && w.isClient();
			holder = access.easysuppressions$zones();
			easysuppressions$holder = holder;
		}
		return holder;
	}

	@ModifyReturnValue(method = "getLightLevel", at = @At("RETURN"))
	private int easysuppressions$override(int original, @Local(argsOnly = true) BlockPos pos) {
		ZoneHolder holder = easysuppressions$holder();
		if (holder == null) {
			return original;
		}
		LightZoneSet set = holder.light;
		if (set.isEmpty()) {
			return original;
		}
		int value = set.get(easysuppressions$channel, pos.getX(), pos.getY(), pos.getZ());
		return value >= 0 ? value : original;
	}

	@ModifyReturnValue(method = "getLightSection", at = @At("RETURN"))
	private ChunkNibbleArray easysuppressions$overrideSection(ChunkNibbleArray original, @Local(argsOnly = true) ChunkSectionPos pos) {
		ZoneHolder holder = easysuppressions$holder();
		if (holder == null || !easysuppressions$client) {
			return original;
		}
		LightZoneSet set = holder.light;
		LightHooks.SectionOverride hook = LightHooks.clientSectionOverride;
		if (set.isEmpty() || hook == null || !set.affectsSection(easysuppressions$channel, pos.getSectionX(), pos.getSectionY(), pos.getSectionZ())) {
			return original;
		}
		return hook.apply((ChunkLightProvider<?, ?>) (Object) this, set, easysuppressions$channel, pos, original);
	}
}
