package io.github.autyism.easysuppressions.block;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.item.BoundZone;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Block entity shared by both blocks. Holds the zone and the "active" flag (cutter: powered, light
 * suppressor: locked). The active flag lives here and never in the block state, so observers see
 * nothing and no neighbour is updated when it changes; the client learns it through the block
 * entity update packet and redraws the block with the matching texture.
 */
public abstract class SuppressionBlockEntity extends BlockEntity {
	@Nullable
	protected Zone zone;
	protected boolean active;
	private boolean initialized;

	protected SuppressionBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public abstract SuppressorKind kind();

	@Nullable
	public Zone getZone() {
		return zone;
	}

	public boolean isActive() {
		return active;
	}

	public void serverTick(ServerWorld world) {
		if (!initialized) {
			initialized = true;
			onFirstTick(world);
		}
	}

	/** First tick after placing or after the chunk loaded: bring the register in line with this block. */
	protected abstract void onFirstTick(ServerWorld world);

	protected void onNeighborUpdate(ServerWorld world) {
	}

	/** The block entity was removed because the block was replaced or removed (not chunk unloading). */
	public abstract void onRemovedFromWorld(ServerWorld world);

	/** Saves and sends the new state to clients, without touching neighbours or comparators. */
	protected void changed() {
		if (world != null) {
			world.markDirty(pos);
			if (world instanceof ServerWorld serverWorld) {
				serverWorld.getChunkManager().markForUpdate(pos);
			}
		}
	}

	/** Vanilla also updates comparators here; this block has no comparator output, so only save. */
	@Override
	public void markDirty() {
		if (world != null) {
			world.markDirty(pos);
		}
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		view.putNullable("zone", Zone.CODEC, zone);
		view.putBoolean("active", active);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		boolean wasActive = active;
		Zone oldZone = zone;
		zone = view.read("zone", Zone.CODEC).orElse(null);
		active = view.getBoolean("active", false);
		if (world != null && world.isClient() && (wasActive != active || !Objects.equals(oldZone, zone))) {
			// Redraw the chunk section so the model switches between the inactive and active texture.
			BlockState state = getCachedState();
			world.updateListeners(pos, state, state, Block.REDRAW_ON_MAIN_THREAD);
		}
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
		return createComponentlessNbt(registries);
	}

	@Override
	protected void readComponents(ComponentsAccess components) {
		super.readComponents(components);
		BoundZone bound = components.get(ModContent.BOUND_ZONE);
		if (bound != null && (world == null || bound.dimension().equals(world.getRegistryKey()))) {
			zone = bound.zone();
		}
	}

	@Override
	protected void addComponents(ComponentMap.Builder builder) {
		super.addComponents(builder);
		if (zone != null && world != null) {
			builder.add(ModContent.BOUND_ZONE, new BoundZone(world.getRegistryKey(), zone));
		}
	}

	@Override
	public void removeFromCopiedStackData(WriteView view) {
		super.removeFromCopiedStackData(view);
		view.remove("zone");
		view.remove("active");
	}

	/** Read by the client model (Fabric render data) to pick the texture. */
	@Override
	public Object getRenderData() {
		return active;
	}
}
