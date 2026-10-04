package io.github.autyism.easysuppressions.block;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.world.EsWorldState;
import io.github.autyism.easysuppressions.world.ServerZones;
import io.github.autyism.easysuppressions.world.WorldZones;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** "Active" means locked. While locked the zone and settings cannot change. */
public class LightSuppressorBlockEntity extends SuppressionBlockEntity {
	private LightSettings settings = LightSettings.DEFAULT;

	public LightSuppressorBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.LIGHT_SUPPRESSOR_BLOCK_ENTITY, pos, state);
	}

	@Override
	public SuppressorKind kind() {
		return SuppressorKind.LIGHT_SUPPRESSOR;
	}

	public LightSettings getSettings() {
		return settings;
	}

	public boolean isLocked() {
		return active;
	}

	/** The register is what the effect uses; make this block agree with it. */
	@Override
	protected void onFirstTick(ServerWorld world) {
		EsWorldState.SuppressorEntry entry = ServerZones.of(world).lightEntry(pos);
		if (entry != null) {
			if (!active || !entry.zone().equals(zone) || !entry.settings().equals(settings)) {
				active = true;
				zone = entry.zone();
				settings = entry.settings();
				changed();
			}
		} else if (active) {
			active = false;
			changed();
		}
	}

	public void setZone(Zone newZone) {
		zone = newZone;
		changed();
	}

	public void setSettings(LightSettings newSettings) {
		settings = newSettings;
		changed();
	}

	public WorldZones.LockResult lock(ServerWorld world, @Nullable UUID owner) {
		WorldZones.LockResult result = ServerZones.of(world).lock(pos, zone, settings, owner);
		if (result instanceof WorldZones.LockResult.Ok) {
			active = true;
			changed();
		}
		return result;
	}

	public void unlock(ServerWorld world) {
		ServerZones.of(world).unlock(pos);
		active = false;
		changed();
	}

	@Override
	public void onRemovedFromWorld(ServerWorld world) {
		ServerZones.of(world).unlock(pos);
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		view.put("settings", LightSettings.CODEC, settings);
	}

	@Override
	protected void readData(ReadView view) {
		settings = view.read("settings", LightSettings.CODEC).orElse(LightSettings.DEFAULT);
		super.readData(view);
	}

	@Override
	public void removeFromCopiedStackData(WriteView view) {
		super.removeFromCopiedStackData(view);
		view.remove("settings");
	}
}
