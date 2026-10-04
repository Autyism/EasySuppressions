package io.github.autyism.easysuppressions.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.autyism.easysuppressions.block.SuppressionBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every removal of a block entity from a loaded chunk goes through {@code removeBlockEntity}:
 * breaking, /setblock, /fill, explosions, other mods replacing the block. Chunk unloading does
 * not (it clears the chunk instead), so this is exactly "the block is gone" and the register
 * entry is dropped here.
 */
@Mixin(WorldChunk.class)
abstract class WorldChunkMixin {
	@Shadow
	@Final
	World world;

	@Inject(method = "removeBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/BlockEntity;markRemoved()V"))
	private void easysuppressions$onRemoved(BlockPos pos, CallbackInfo ci, @Local BlockEntity blockEntity) {
		if (blockEntity instanceof SuppressionBlockEntity be && world instanceof ServerWorld serverWorld) {
			be.onRemovedFromWorld(serverWorld);
		}
	}
}
