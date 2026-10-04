package io.github.autyism.easysuppressions.mixin;

import io.github.autyism.easysuppressions.world.ZoneHolderAccess;
import net.minecraft.block.BlockState;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Portal cutter: inside an active zone a nether portal block ignores neighbour updates, so it
 * never breaks, whatever happens to its frame. Only the register is consulted (one chunk-indexed
 * lookup; an empty register costs one volatile read). The client runs the same check with the
 * zones the server sent, so its prediction agrees with the server.
 */
@Mixin(NetherPortalBlock.class)
abstract class NetherPortalBlockMixin {
	@Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
	private void easysuppressions$keepInActiveZone(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, Random random, CallbackInfoReturnable<BlockState> cir) {
		if (world instanceof ZoneHolderAccess access && access.easysuppressions$zones().isPortalProtected(pos.getX(), pos.getY(), pos.getZ())) {
			cir.setReturnValue(state);
		}
	}
}
