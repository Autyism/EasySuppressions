package io.github.autyism.easysuppressions.block;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.world.ServerZones;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Active while powered; its zone is then in the world's register of protected portal zones. */
public class PortalCutterBlockEntity extends SuppressionBlockEntity {
	public PortalCutterBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.PORTAL_CUTTER_BLOCK_ENTITY, pos, state);
	}

	@Override
	public SuppressorKind kind() {
		return SuppressorKind.PORTAL_CUTTER;
	}

	@Override
	protected void onFirstTick(ServerWorld world) {
		refreshPower(world, true);
	}

	@Override
	protected void onNeighborUpdate(ServerWorld world) {
		refreshPower(world, false);
	}

	private void refreshPower(ServerWorld world, boolean forceRegister) {
		boolean powered = world.isReceivingRedstonePower(pos);
		boolean changed = powered != active;
		if (changed) {
			active = powered;
			changed();
		}
		if (changed || forceRegister) {
			register(world);
		}
	}

	private void register(ServerWorld world) {
		ServerZones.of(world).setCutter(pos, active ? zone : null);
	}

	public void setZone(ServerWorld world, Zone newZone) {
		zone = newZone;
		changed();
		register(world);
	}

	@Override
	public void onRemovedFromWorld(ServerWorld world) {
		ServerZones.of(world).setCutter(pos, null);
	}
}
