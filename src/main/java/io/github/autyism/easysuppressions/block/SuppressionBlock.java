package io.github.autyism.easysuppressions.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import eu.pb4.polymer.core.api.utils.PolymerKeepModel;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.config.EsConfig;
import io.github.autyism.easysuppressions.vanilla.EsClients;
import io.github.autyism.easysuppressions.vanilla.StandIns;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

/**
 * Common behaviour of the portal cutter and the light suppressor: fixed survival break time,
 * no drops (settings), immovable, explosion proof, takes redstone power without passing it on
 * or showing it in its block state.
 *
 * <p>Players without the mod see a vanilla stand-in (see StandIns); their mining runs on the server
 * with the configured break time.
 */
public abstract class SuppressionBlock extends BlockWithEntity implements PolymerBlock, PolymerClientDecoded, PolymerKeepModel {
	private final SuppressorKind kind;

	protected SuppressionBlock(Settings settings, SuppressorKind kind) {
		super(settings);
		this.kind = kind;
	}

	public SuppressorKind kind() {
		return kind;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return EsClients.has(context) ? state : StandIns.block(kind);
	}

	@Override
	public boolean canSyncRawToClient(PacketContext context) {
		return EsClients.has(context);
	}

	@Override
	public boolean handleMiningOnServer(ItemStack tool, BlockState state, BlockPos pos, ServerPlayerEntity player) {
		return !EsClients.has(player);
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
