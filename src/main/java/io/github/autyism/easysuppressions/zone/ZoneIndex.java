package io.github.autyism.easysuppressions.zone;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Immutable lookup "which entries cover this block", indexed by chunk column. Built once whenever
 * the set of zones changes and then published through a volatile field, so it can be read from
 * any thread (light queries come from worldgen and chunk meshing threads too) without locking.
 *
 * <p>Zones are unlimited in size. A zone covering more than {@link #MAX_INDEXED_COLUMNS} chunk
 * columns is not put into the per-chunk map but into a short list that is checked linearly.
 */
public final class ZoneIndex<T> {
	static final long MAX_INDEXED_COLUMNS = 16384;

	private final Long2ObjectOpenHashMap<Object[]> byColumn;
	private final Object[] large;
	private final Function<T, Zone> zoneOf;
	private final List<T> all;

	private ZoneIndex(Long2ObjectOpenHashMap<Object[]> byColumn, Object[] large, Function<T, Zone> zoneOf, List<T> all) {
		this.byColumn = byColumn;
		this.large = large;
		this.zoneOf = zoneOf;
		this.all = all;
	}

	public static <T> ZoneIndex<T> empty(Function<T, Zone> zoneOf) {
		return new ZoneIndex<>(new Long2ObjectOpenHashMap<>(), new Object[0], zoneOf, List.of());
	}

	public static <T> ZoneIndex<T> build(Collection<T> entries, Function<T, Zone> zoneOf) {
		Long2ObjectOpenHashMap<List<T>> lists = new Long2ObjectOpenHashMap<>();
		List<T> large = new ArrayList<>();
		for (T entry : entries) {
			Zone zone = zoneOf.apply(entry);
			if (zone.columnCount() > MAX_INDEXED_COLUMNS) {
				large.add(entry);
				continue;
			}
			for (int cx = zone.minChunkX(); cx <= zone.maxChunkX(); cx++) {
				for (int cz = zone.minChunkZ(); cz <= zone.maxChunkZ(); cz++) {
					lists.computeIfAbsent(ChunkPos.toLong(cx, cz), k -> new ArrayList<>(1)).add(entry);
				}
			}
		}
		Long2ObjectOpenHashMap<Object[]> byColumn = new Long2ObjectOpenHashMap<>(lists.size());
		for (Long2ObjectMap.Entry<List<T>> e : lists.long2ObjectEntrySet()) {
			byColumn.put(e.getLongKey(), e.getValue().toArray());
		}
		byColumn.trim();
		return new ZoneIndex<>(byColumn, large.toArray(), zoneOf, List.copyOf(entries));
	}

	public boolean isEmpty() {
		return all.isEmpty();
	}

	public List<T> all() {
		return all;
	}

	/** First entry whose zone contains the block, or null. */
	@SuppressWarnings("unchecked")
	public T find(int x, int y, int z) {
		Object[] column = byColumn.get(ChunkPos.toLong(x >> 4, z >> 4));
		if (column != null) {
			for (Object o : column) {
				if (zoneOf.apply((T) o).contains(x, y, z)) {
					return (T) o;
				}
			}
		}
		for (Object o : large) {
			if (zoneOf.apply((T) o).contains(x, y, z)) {
				return (T) o;
			}
		}
		return null;
	}

	/** All entries whose zone touches the given chunk column (unordered, may be empty). */
	@SuppressWarnings("unchecked")
	public List<T> inColumn(int cx, int cz) {
		List<T> out = new ArrayList<>();
		Object[] column = byColumn.get(ChunkPos.toLong(cx, cz));
		if (column != null) {
			for (Object o : column) {
				out.add((T) o);
			}
		}
		for (Object o : large) {
			if (zoneOf.apply((T) o).intersectsColumn(cx, cz)) {
				out.add((T) o);
			}
		}
		return out;
	}

	/** Whether any entry touches the chunk column; cheap check before doing real work. */
	public boolean touchesColumn(int cx, int cz) {
		if (byColumn.containsKey(ChunkPos.toLong(cx, cz))) {
			return true;
		}
		for (Object o : large) {
			@SuppressWarnings("unchecked") T t = (T) o;
			if (zoneOf.apply(t).intersectsColumn(cx, cz)) {
				return true;
			}
		}
		return false;
	}
}
