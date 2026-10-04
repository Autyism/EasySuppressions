package io.github.autyism.easysuppressions.mixin;

import io.github.autyism.easysuppressions.world.ServerZones;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Writes newly recorded light whenever the world saves (autosave, /save-all, stop). */
@Mixin(ServerWorld.class)
abstract class ServerWorldMixin {
	@Inject(method = "save", at = @At("HEAD"))
	private void easysuppressions$saveLight(ProgressListener progressListener, boolean flush, boolean savingDisabled, CallbackInfo ci) {
		if (!savingDisabled) {
			ServerZones.onWorldSave((ServerWorld) (Object) this);
		}
	}
}
