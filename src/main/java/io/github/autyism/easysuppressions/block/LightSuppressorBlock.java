package io.github.autyism.easysuppressions.block;

import com.mojang.serialization.MapCodec;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.net.ServerNet;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class LightSuppressorBlock extends SuppressionBlock {
	public static final MapCodec<LightSuppressorBlock> CODEC = createCodec(LightSuppressorBlock::new);

	public LightSuppressorBlock(Settings settings) {
		super(settings, SuppressorKind.LIGHT_SUPPRESSOR);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new LightSuppressorBlockEntity(pos, state);
	}

	/** Redstone power only decides whether the GUI may be opened; the effect does not depend on it. */
	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (player instanceof ServerPlayerEntity serverPlayer && world.getBlockEntity(pos) instanceof LightSuppressorBlockEntity be) {
			if (!world.isReceivingRedstonePower(pos)) {
				serverPlayer.sendMessage(Text.translatable("easysuppressions.message.needs_redstone").formatted(Formatting.RED), true);
			} else {
				ServerNet.openScreen(serverPlayer, be);
			}
		}
		return ActionResult.SUCCESS;
	}
}
