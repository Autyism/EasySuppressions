package io.github.autyism.easysuppressions.light;

import io.github.autyism.easysuppressions.zone.Zone;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * One locked light suppressor zone as the light hook sees it (server and client use the same class).
 * The settings and box never change; unlocking or editing creates a new object. Recorded columns are
 * added over time (copy on write, published through a volatile field) so readers on other threads
 * always see a consistent map.
 */
public final class LightZone {
	private static final java.util.concurrent.atomic.AtomicLong NEXT_ID = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());

	public final BlockPos source;
	/** Changes with every lock, so clients can tell a re-locked zone from the old one. */
	public final long lockId;
	public final Zone zone;
	public final LightSettings settings;
	private volatile Long2ObjectOpenHashMap<ColumnData> columns = new Long2ObjectOpenHashMap<>();

	public LightZone(BlockPos source, Zone zone, LightSettings settings) {
		this(source, NEXT_ID.incrementAndGet(), zone, settings);
	}

	public LightZone(BlockPos source, long lockId, Zone zone, LightSettings settings) {
		this.source = source.toImmutable();
		this.lockId = lockId;
		this.zone = zone;
		this.settings = settings;
	}

	public Zone zone() {
		return zone;
	}

	/** The overriding value for this block, or -1 when vanilla light applies. Caller checked containment. */
	public int get(LightChannel channel, int x, int y, int z) {
		ChannelSetting setting = settings.get(channel);
		switch (setting.mode()) {
			case NONE:
				return -1;
			case FIXED:
				return setting.value();
			default:
				ColumnData column = columns.get(ChunkPos.toLong(x >> 4, z >> 4));
				return column == null ? -1 : column.get(channel, x, y, z);
		}
	}

	public boolean affects(LightChannel channel) {
		return settings.get(channel).mode() != ChannelMode.NONE;
	}

	public ColumnData column(int cx, int cz) {
		return columns.get(ChunkPos.toLong(cx, cz));
	}

	public boolean hasColumn(long key) {
		return columns.containsKey(key);
	}

	public int columnCount() {
		return columns.size();
	}

	public LongCollection columnKeys() {
		return columns.keySet();
	}

	public Long2ObjectOpenHashMap<ColumnData> columnsView() {
		return columns;
	}

	/** Adds or replaces recorded columns in one copy-on-write step. */
	public synchronized void putColumns(Long2ObjectMap<ColumnData> added) {
		if (added.isEmpty()) {
			return;
		}
		Long2ObjectOpenHashMap<ColumnData> copy = new Long2ObjectOpenHashMap<>(columns);
		copy.putAll(added);
		columns = copy;
	}

	public synchronized void removeColumn(long key) {
		if (!columns.containsKey(key)) {
			return;
		}
		Long2ObjectOpenHashMap<ColumnData> copy = new Long2ObjectOpenHashMap<>(columns);
		copy.remove(key);
		columns = copy;
	}

	public synchronized void clearColumns() {
		columns = new Long2ObjectOpenHashMap<>();
	}
}
