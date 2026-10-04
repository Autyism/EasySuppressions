package io.github.autyism.easysuppressions.world;

import io.github.autyism.easysuppressions.EasySuppressions;
import io.github.autyism.easysuppressions.block.LightSuppressorBlockEntity;
import io.github.autyism.easysuppressions.block.PortalCutterBlockEntity;
import io.github.autyism.easysuppressions.light.ColumnData;
import io.github.autyism.easysuppressions.light.LightCapture;
import io.github.autyism.easysuppressions.light.LightDataStore;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.light.LightZone;
import io.github.autyism.easysuppressions.light.LightZoneSet;
import io.github.autyism.easysuppressions.mixin.ServerChunkLoadingManagerAccessor;
import io.github.autyism.easysuppressions.net.ServerNet;
import io.github.autyism.easysuppressions.zone.Zone;
import io.github.autyism.easysuppressions.zone.ZoneIndex;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side registers of one world: which portal cutter zones are active and which light zones
 * are locked, plus the recorded light. Only touched on the server thread; the hooks read the
 * immutable snapshots in {@link ZoneHolder}.
 */
public final class WorldZones {
	/** Columns recorded per tick at most (lock of a large loaded area is spread over a few ticks). */
	private static final int CAPTURE_BUDGET_PER_TICK = 256;
	private static final int VALIDATE_INTERVAL = 200;

	private final ServerWorld world;
	private final EsWorldState state;
	private final ZoneHolder holder;
	private final LightDataStore store;
	private final Map<BlockPos, LightZone> lightZones = new LinkedHashMap<>();
	private final Map<BlockPos, Long2ObjectOpenHashMap<ColumnData>> unsaved = new HashMap<>();
	/** Columns waiting to be recorded, with the chunk from the load event (getWorldChunk may not see it yet). */
	private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<WorldChunk> captureQueue = new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();
	private boolean lightMetaDirty;
	private int ticks;

	public WorldZones(ServerWorld world) {
		this.world = world;
		this.state = world.getPersistentStateManager().getOrCreate(EsWorldState.TYPE);
		this.holder = ((ZoneHolderAccess) world).easysuppressions$zones();
		this.store = new LightDataStore(DimensionType.getSaveDirectory(world.getRegistryKey(),
				world.getServer().getSavePath(WorldSavePath.ROOT)).resolve("data").resolve(EasySuppressions.MOD_ID));
		for (EsWorldState.SuppressorEntry entry : state.suppressors.values()) {
			LightZone zone = new LightZone(entry.source(), entry.zone(), entry.settings());
			if (entry.settings().needsCapture()) {
				Long2ObjectOpenHashMap<ColumnData> columns = store.read(entry.source(), entry.zone(), entry.settings());
				if (columns == null) {
					EasySuppressions.LOGGER.warn("Recorded light of the light suppressor at {} is missing; its columns will be recorded again when they load", entry.source());
					store.create(entry.source(), entry.zone(), entry.settings());
				} else {
					zone.putColumns(columns);
				}
			}
			lightZones.put(entry.source(), zone);
		}
		rebuildCutters();
		rebuildLight();
	}

	public ServerWorld world() {
		return world;
	}

	// ---------------------------------------------------------------- portal cutters

	/** Sets (zone != null) or removes the active zone of the cutter at {@code source}. */
	public void setCutter(BlockPos source, @Nullable Zone zone) {
		EsWorldState.CutterEntry old = state.cutters.get(source);
		if (zone == null) {
			if (old == null) {
				return;
			}
			state.cutters.remove(source);
		} else {
			if (old != null && old.zone().equals(zone)) {
				return;
			}
			state.cutters.put(source.toImmutable(), new EsWorldState.CutterEntry(source.toImmutable(), zone));
		}
		state.markDirty();
		rebuildCutters();
		ServerNet.broadcastCutters(this);
	}

	public Collection<EsWorldState.CutterEntry> cutters() {
		return state.cutters.values();
	}

	private void rebuildCutters() {
		List<Zone> zones = new ArrayList<>();
		for (EsWorldState.CutterEntry e : state.cutters.values()) {
			zones.add(e.zone());
		}
		holder.cutters = zones.isEmpty() ? ZoneHolder.NO_CUTTERS : ZoneIndex.build(zones, z -> z);
	}

	// ---------------------------------------------------------------- light suppressors

	public sealed interface LockResult permits LockResult.Ok, LockResult.Overlap {
		record Ok() implements LockResult {
		}

		record Overlap(BlockPos other) implements LockResult {
		}
	}

	@Nullable
	public EsWorldState.SuppressorEntry lightEntry(BlockPos source) {
		return state.suppressors.get(source);
	}

	public Collection<EsWorldState.SuppressorEntry> lightEntries() {
		return state.suppressors.values();
	}

	@Nullable
	public LightZone lightZone(BlockPos source) {
		return lightZones.get(source);
	}

	public Collection<LightZone> lightZones() {
		return lightZones.values();
	}

	/** Locks a zone. Fails if it overlaps another locked zone of this world. */
	public LockResult lock(BlockPos source, Zone zone, LightSettings settings, @Nullable UUID owner) {
		for (EsWorldState.SuppressorEntry other : state.suppressors.values()) {
			if (!other.source().equals(source) && other.zone().intersects(zone)) {
				return new LockResult.Overlap(other.source());
			}
		}
		removeLight(source);
		source = source.toImmutable();
		state.suppressors.put(source, new EsWorldState.SuppressorEntry(source, zone, settings, Optional.ofNullable(owner), !settings.needsCapture()));
		state.markDirty();
		LightZone lightZone = new LightZone(source, zone, settings);
		lightZones.put(source, lightZone);
		if (settings.needsCapture()) {
			store.create(source, zone, settings);
			captureLoaded(lightZone);
			if (progress(lightZone).pending() == 0) {
				// Everything was loaded and recorded right away: nothing to announce later.
				state.suppressors.put(source, state.suppressors.get(source).withCompleteNotified());
			}
		}
		rebuildLight();
		markLightChanged();
		ServerNet.broadcastLightZoneReset(this, source);
		return new LockResult.Ok();
	}

	/** Unlocks (GUI, or the block was removed). */
	public void unlock(BlockPos source) {
		if (removeLight(source)) {
			rebuildLight();
			markLightChanged();
			ServerNet.broadcastLightZoneReset(this, source);
		}
	}

	private boolean removeLight(BlockPos source) {
		boolean had = state.suppressors.remove(source) != null;
		lightZones.remove(source);
		unsaved.remove(source);
		if (had) {
			state.markDirty();
			store.delete(source);
		}
		return had;
	}

	private void rebuildLight() {
		holder.light = LightZoneSet.of(new ArrayList<>(lightZones.values()));
	}

	private void markLightChanged() {
		lightMetaDirty = true;
	}

	/**
	 * A loaded full chunk, also at the border of the loaded area. getWorldChunk only returns ticking
	 * chunks; a border chunk that later becomes ticking fires no load event, so it must be seen here.
	 */
	@Nullable
	private WorldChunk fullChunk(long key) {
		var holder = ((ServerChunkLoadingManagerAccessor) world.getChunkManager().chunkLoadingManager).easysuppressions$getChunkHolders().get(key);
		return holder == null ? null : holder.getAccessibleFuture().getNow(net.minecraft.server.world.ChunkHolder.UNLOADED_WORLD_CHUNK).orElse(null);
	}

	/** Records the columns of a just-locked zone that are loaded right now (the rest when they load). */
	private void captureLoaded(LightZone zone) {
		Zone box = zone.zone;
		Map<Long, WorldChunk> loaded = new LinkedHashMap<>();
		var holders = ((ServerChunkLoadingManagerAccessor) world.getChunkManager().chunkLoadingManager).easysuppressions$getChunkHolders();
		if (box.columnCount() <= holders.size()) {
			for (int cx = box.minChunkX(); cx <= box.maxChunkX(); cx++) {
				for (int cz = box.minChunkZ(); cz <= box.maxChunkZ(); cz++) {
					WorldChunk chunk = fullChunk(ChunkPos.toLong(cx, cz));
					if (chunk != null) {
						loaded.put(ChunkPos.toLong(cx, cz), chunk);
					}
				}
			}
		} else {
			for (var holderEntry : holders.values()) {
				WorldChunk chunk = holderEntry.getAccessibleFuture().getNow(net.minecraft.server.world.ChunkHolder.UNLOADED_WORLD_CHUNK).orElse(null);
				if (chunk != null && box.intersectsColumn(chunk.getPos().x, chunk.getPos().z)) {
					loaded.put(chunk.getPos().toLong(), chunk);
				}
			}
		}
		Long2ObjectOpenHashMap<ColumnData> captured = new Long2ObjectOpenHashMap<>();
		for (Map.Entry<Long, WorldChunk> e : loaded.entrySet()) {
			WorldChunk chunk = e.getValue();
			if (!chunk.isLightOn()) {
				captureQueue.put(e.getKey().longValue(), chunk);
				continue;
			}
			captured.put(e.getKey().longValue(), LightCapture.capture(world, chunk.getPos().x, chunk.getPos().z, box, zone.settings));
		}
		addColumns(zone, captured);
	}

	private void addColumns(LightZone zone, Long2ObjectOpenHashMap<ColumnData> columns) {
		if (columns.isEmpty()) {
			return;
		}
		zone.putColumns(columns);
		unsaved.computeIfAbsent(zone.source, k -> new Long2ObjectOpenHashMap<>()).putAll(columns);
		ServerNet.sendLightColumns(this, zone, columns);
		markLightChanged();
	}

	/**
	 * Safety net for chunk loading paths that fire no event: every loaded full chunk that still
	 * waits for its light to be recorded is queued.
	 */
	private void sweepLoadedPending() {
		List<LightZone> pendingZones = new ArrayList<>();
		for (LightZone zone : lightZones.values()) {
			if (progress(zone).pending() > 0) {
				pendingZones.add(zone);
			}
		}
		if (pendingZones.isEmpty()) {
			return;
		}
		var holders = ((ServerChunkLoadingManagerAccessor) world.getChunkManager().chunkLoadingManager).easysuppressions$getChunkHolders();
		for (var chunkHolder : holders.values()) {
			WorldChunk chunk = chunkHolder.getAccessibleFuture().getNow(net.minecraft.server.world.ChunkHolder.UNLOADED_WORLD_CHUNK).orElse(null);
			if (chunk == null) {
				continue;
			}
			long key = chunk.getPos().toLong();
			for (LightZone zone : pendingZones) {
				if (zone.zone.intersectsColumn(chunk.getPos().x, chunk.getPos().z) && !zone.hasColumn(key)) {
					captureQueue.put(key, chunk);
					break;
				}
			}
		}
	}

	/** A chunk became a full chunk on the server. */
	public void onChunkLoad(WorldChunk chunk) {
		LightZoneSet set = holder.light;
		if (set.isEmpty()) {
			return;
		}
		int cx = chunk.getPos().x;
		int cz = chunk.getPos().z;
		long key = chunk.getPos().toLong();
		for (LightZone zone : set.index().inColumn(cx, cz)) {
			if (zone.settings.needsCapture() && !zone.hasColumn(key)) {
				captureQueue.put(key, chunk);
				break;
			}
		}
		if (!captureQueue.isEmpty() && world.getServer().isOnThread()) {
			processCaptureQueue();
		}
	}

	public void tick() {
		ticks++;
		if (!captureQueue.isEmpty()) {
			processCaptureQueue();
		}
		if (ticks % VALIDATE_INTERVAL == 0) {
			validate();
		}
		if (ticks % 100 == 50) {
			sweepLoadedPending();
		}
		if (lightMetaDirty && ticks % 10 == 0) {
			lightMetaDirty = false;
			checkCompletion();
			ServerNet.broadcastLightZones(this);
		}
	}

	/** The chunk unloaded before its light could be recorded: it is recorded on its next load. */
	public void onChunkUnload(WorldChunk chunk) {
		captureQueue.remove(chunk.getPos().toLong());
	}

	private void processCaptureQueue() {
		Map<LightZone, Long2ObjectOpenHashMap<ColumnData>> batch = new HashMap<>();
		int budget = CAPTURE_BUDGET_PER_TICK;
		var it = captureQueue.long2ObjectEntrySet().fastIterator();
		Long2ObjectOpenHashMap<WorldChunk> retry = new Long2ObjectOpenHashMap<>();
		while (it.hasNext() && budget > 0) {
			var entry = it.next();
			long key = entry.getLongKey();
			WorldChunk chunk = entry.getValue();
			it.remove();
			int cx = ChunkPos.getPackedX(key);
			int cz = ChunkPos.getPackedZ(key);
			if (!chunk.isLightOn()) {
				retry.put(key, chunk);
				continue;
			}
			for (LightZone zone : holder.light.index().inColumn(cx, cz)) {
				if (zone.settings.needsCapture() && !zone.hasColumn(key)) {
					batch.computeIfAbsent(zone, z -> new Long2ObjectOpenHashMap<>())
							.put(key, LightCapture.capture(world, cx, cz, zone.zone, zone.settings));
					budget--;
				}
			}
		}
		captureQueue.putAll(retry);
		batch.forEach(this::addColumns);
	}

	/** Columns of a zone that still have to be recorded; total and recorded counts. */
	public record Progress(long total, long recorded) {
		public long pending() {
			return Math.max(0, total - recorded);
		}
	}

	public static Progress progress(LightZone zone) {
		if (!zone.settings.needsCapture()) {
			return new Progress(0, 0);
		}
		return new Progress(zone.zone.columnCount(), zone.columnCount());
	}

	/** Up to {@code limit} pending columns of the zone nearest to (centerCx, centerCz), searched in rings. */
	public static LongList nearestPending(LightZone zone, int centerCx, int centerCz, int limit, int maxRadius) {
		LongList out = new LongArrayList();
		if (!zone.settings.needsCapture()) {
			return out;
		}
		Zone box = zone.zone;
		int cx0 = Math.max(box.minChunkX(), Math.min(box.maxChunkX(), centerCx));
		int cz0 = Math.max(box.minChunkZ(), Math.min(box.maxChunkZ(), centerCz));
		for (int r = 0; r <= maxRadius && out.size() < limit; r++) {
			for (int dx = -r; dx <= r && out.size() < limit; dx++) {
				for (int dz = -r; dz <= r && out.size() < limit; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int cx = cx0 + dx;
					int cz = cz0 + dz;
					if (cx < box.minChunkX() || cx > box.maxChunkX() || cz < box.minChunkZ() || cz > box.maxChunkZ()) {
						continue;
					}
					long key = ChunkPos.toLong(cx, cz);
					if (!zone.hasColumn(key)) {
						out.add(key);
					}
				}
			}
		}
		return out;
	}

	private void checkCompletion() {
		for (EsWorldState.SuppressorEntry entry : new ArrayList<>(state.suppressors.values())) {
			if (entry.completeNotified()) {
				continue;
			}
			LightZone zone = lightZones.get(entry.source());
			if (zone != null && progress(zone).pending() == 0) {
				state.suppressors.put(entry.source(), entry.withCompleteNotified());
				state.markDirty();
				notifyComplete(entry);
			}
		}
	}

	private void notifyComplete(EsWorldState.SuppressorEntry entry) {
		Text text = Text.translatable("easysuppressions.message.all_recorded",
				entry.source().getX(), entry.source().getY(), entry.source().getZ()).formatted(Formatting.GREEN);
		for (ServerPlayerEntity player : world.getPlayers()) {
			boolean owner = entry.owner().isPresent() && entry.owner().get().equals(player.getUuid());
			if (owner || player.getBlockPos().getSquaredDistance(entry.source()) < 256 * 256) {
				player.sendMessage(text, true);
			}
		}
	}

	/**
	 * Drops register entries whose block is gone although its chunk is loaded. The block entity
	 * removal hook catches every normal way of removing a block; this also covers anything that
	 * edits chunks behind the game's back.
	 */
	public void validate() {
		for (EsWorldState.CutterEntry entry : new ArrayList<>(state.cutters.values())) {
			BlockPos pos = entry.source();
			if (world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
				BlockEntity be = world.getBlockEntity(pos);
				if (!(be instanceof PortalCutterBlockEntity cutter) || !cutter.isActive() || cutter.getZone() == null) {
					EasySuppressions.LOGGER.info("Removing stale portal cutter zone of {}", pos);
					setCutter(pos, null);
				}
			}
		}
		for (EsWorldState.SuppressorEntry entry : new ArrayList<>(state.suppressors.values())) {
			BlockPos pos = entry.source();
			if (world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
				if (!(world.getBlockEntity(pos) instanceof LightSuppressorBlockEntity)) {
					EasySuppressions.LOGGER.info("Removing stale light suppressor zone of {}", pos);
					unlock(pos);
				}
			}
		}
	}

	/** Called when the world saves: appends newly recorded columns to the zone files. */
	public void save() {
		if (unsaved.isEmpty()) {
			return;
		}
		for (Map.Entry<BlockPos, Long2ObjectOpenHashMap<ColumnData>> e : unsaved.entrySet()) {
			if (state.suppressors.containsKey(e.getKey())) {
				store.append(e.getKey(), e.getValue());
			}
		}
		unsaved.clear();
	}
}
