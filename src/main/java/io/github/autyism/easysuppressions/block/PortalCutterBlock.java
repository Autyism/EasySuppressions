package io.github.autyism.easysuppressions.block;

import com.mojang.serialization.MapCodec;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.net.ServerNet;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PortalCutterBlock extends SuppressionBlock {
	public static final MapCodec<PortalCutterBlock> CODEC = createCodec(PortalCutterBlock::new);

	public PortalCutterBlock(Settings settings) {
		super(settings, SuppressorKind.PORTAL_CUTTER);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new PortalCutterBlockEntity(pos, state);
	}

	/** The GUI opens with or without redstone power. */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(pos) instanceof PortalCutterBlockEntity be) {
			ServerNet.openScreen(serverPlayer, be);
		}
		return ActionResult.SUCCESS;
	}
}
