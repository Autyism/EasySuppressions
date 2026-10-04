package io.github.autyism.easysuppressions.block;

import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.config.EsConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

/**
 * Common behaviour of the portal cutter and the light suppressor: fixed survival break time,
 * no drops (settings), immovable, explosion proof, takes redstone power without passing it on
 * or showing it in its block state.
 */
public abstract class SuppressionBlock extends BlockWithEntity {
	private final SuppressorKind kind;

	protected SuppressionBlock(Settings settings, SuppressorKind kind) {
		super(settings);
		this.kind = kind;
	}

	public SuppressorKind kind() {
		return kind;
	}

	/** Survival break time comes only from the config: no tool, effect, water or air modifiers. */
	@Override
	protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
		return EsConfig.deltaFor(EsConfig.forSide(player.getEntityWorld().isClient()).blockBreakSeconds());
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
		if (world instanceof ServerWorld serverWorld && world.getBlockEntity(pos) instanceof SuppressionBlockEntity be) {
			be.onNeighborUpdate(serverWorld);
		}
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient()) {
			return null;
		}
		return (w, pos, s, be) -> {
			if (be instanceof SuppressionBlockEntity sbe) {
				sbe.serverTick((ServerWorld) w);
			}
		};
	}
}
