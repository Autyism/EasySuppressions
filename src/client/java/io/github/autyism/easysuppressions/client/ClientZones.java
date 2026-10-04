package io.github.autyism.easysuppressions.client;

import io.github.autyism.easysuppressions.block.SuppressionBlockEntity;
import io.github.autyism.easysuppressions.light.ColumnData;
import io.github.autyism.easysuppressions.light.LightZone;
import io.github.autyism.easysuppressions.light.LightZoneSet;
import io.github.autyism.easysuppressions.net.Payloads;
import io.github.autyism.easysuppressions.world.ZoneHolder;
import io.github.autyism.easysuppressions.world.ZoneHolderAccess;
import io.github.autyism.easysuppressions.zone.Zone;
import io.github.autyism.easysuppressions.zone.ZoneIndex;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the client knows about zones in its current world: active portal cutter zones (for its own
 * portal prediction and the boxes), locked light zones with the recorded light of loaded columns
 * (for drawing), and the mod's block entities in loaded chunks. Client thread only, except the
 * hooks, which read the snapshots in the world's {@link ZoneHolder}.
 */
public final class ClientZones {
	private static ClientWorld world;
	private static List<Payloads.CutterInfo> cutters = List.of();
	private static final Map<BlockPos, Payloads.LightZoneInfo> lightInfo = new LinkedHashMap<>();
	private static final Map<BlockPos, LightZone> lightZones = new LinkedHashMap<>();
	private static LightZoneSet lightSet = LightZoneSet.EMPTY;
	private static final Map<BlockPos, LongLinkedOpenHashSet> requests = new HashMap<>();
	private static final Set<SuppressionBlockEntity> blockEntities = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	private ClientZones() {
	}

	// ------------------------------------------------------------------ accessors for renderer / GUI

	public static List<Payloads.CutterInfo> cutters() {
		return cutters;
	}

	public static Map<BlockPos, Payloads.LightZoneInfo> lightInfo() {
		return lightInfo;
	}

	public static LightZone lightZone(BlockPos source) {
		return lightZones.get(source);
	}

	public static LightZoneSet lightSet() {
		return lightSet;
	}

	public static Set<SuppressionBlockEntity> blockEntities() {
		return blockEntities;
	}

	// ------------------------------------------------------------------ lifecycle

	private static void ensureWorld(ClientWorld current) {
		if (current != world) {
			reset(current);
		}
	}

	public static void tick(MinecraftClient client) {
		ensureWorld(client.world);
		if (world == null || requests.isEmpty()) {
			return;
		}
		for (Map.Entry<BlockPos, LongLinkedOpenHashSet> e : requests.entrySet()) {
			LightZone zone = lightZones.get(e.getKey());
			if (zone == null || e.getValue().isEmpty()) {
				continue;
			}
			LongList keys = new LongArrayList();
			var it = e.getValue().iterator();
			while (it.hasNext() && keys.size() < 512) {
				keys.add(it.nextLong());
				it.remove();
			}
			if (ClientPlayNetworking.canSend(Payloads.RequestLightColumns.ID)) {
				ClientPlayNetworking.send(new Payloads.RequestLightColumns(zone.source, zone.lockId, keys));
			}
		}
		requests.values().removeIf(LongLinkedOpenHashSet::isEmpty);
	}

	private static void reset(ClientWorld newWorld) {
		world = newWorld;
		cutters = List.of();
		lightInfo.clear();
		lightZones.clear();
		lightSet = LightZoneSet.EMPTY;
		requests.clear();
		blockEntities.clear();
	}

	public static void onDisconnect() {
		reset(null);
	}

	private static ZoneHolder holder() {
		return world == null ? null : ((ZoneHolderAccess) world).easysuppressions$zones();
	}

	// ------------------------------------------------------------------ portal cutters

	public static void onCutterZones(Payloads.CutterZones payload) {
		ensureWorld(MinecraftClient.getInstance().world);
		if (world == null || !world.getRegistryKey().equals(payload.dimension())) {
			return;
		}
		cutters = List.copyOf(payload.zones());
		List<Zone> zones = new ArrayList<>();
		for (Payloads.CutterInfo info : cutters) {
			zones.add(info.zone());
		}
		holder().cutters = zones.isEmpty() ? ZoneHolder.NO_CUTTERS : ZoneIndex.build(zones, z -> z);
	}

	// ------------------------------------------------------------------ light suppressors

	public static void onLightZones(Payloads.LightZones payload) {
		ensureWorld(MinecraftClient.getInstance().world);
		if (world == null || !world.getRegistryKey().equals(payload.dimension())) {
			return;
		}
		List<Zone> changed = new ArrayList<>();
		Map<BlockPos, LightZone> next = new LinkedHashMap<>();
		lightInfo.clear();
		for (Payloads.LightZoneInfo info : payload.zones()) {
			lightInfo.put(info.source(), info);
			LightZone existing = lightZones.get(info.source());
			if (existing != null && existing.lockId == info.lockId()) {
				next.put(info.source(), existing);
			} else {
				LightZone zone = new LightZone(info.source(), info.lockId(), info.zone(), info.settings());
				next.put(info.source(), zone);
				changed.add(info.zone());
				if (existing != null) {
					changed.add(existing.zone);
				}
				requestLoaded(zone);
			}
		}
		for (Map.Entry<BlockPos, LightZone> old : lightZones.entrySet()) {
			if (!next.containsKey(old.getKey()) || next.get(old.getKey()) != old.getValue()) {
				changed.add(old.getValue().zone);
				requests.remove(old.getKey());
			}
		}
		lightZones.clear();
		lightZones.putAll(next);
		lightSet = LightZoneSet.of(new ArrayList<>(lightZones.values()));
		applyLightSet();
		for (Zone zone : changed) {
			Rerender.zone(zone);
		}
	}

	private static void requestLoaded(LightZone zone) {
		if (!zone.settings.needsCapture() || world == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		int radius = client.options.getClampedViewDistance() + 1;
		ChunkPos center = client.player.getChunkPos();
		Zone box = zone.zone;
		int minX = Math.max(box.minChunkX(), center.x - radius);
		int maxX = Math.min(box.maxChunkX(), center.x + radius);
		int minZ = Math.max(box.minChunkZ(), center.z - radius);
		int maxZ = Math.min(box.maxChunkZ(), center.z + radius);
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				if (world.getChunkManager().getWorldChunk(cx, cz) != null) {
					requests.computeIfAbsent(zone.source, k -> new LongLinkedOpenHashSet()).add(ChunkPos.toLong(cx, cz));
				}
			}
		}
	}

	public static void onLightColumns(Payloads.LightColumns payload) {
		if (world == null || !world.getRegistryKey().equals(payload.dimension())) {
			return;
		}
		LightZone zone = lightZones.get(payload.source());
		if (zone == null || zone.lockId != payload.lockId()) {
			return;
		}
		Long2ObjectOpenHashMap<ColumnData> added = new Long2ObjectOpenHashMap<>();
		for (Payloads.ColumnEntry entry : payload.columns()) {
			entry.data().ifPresent(data -> added.put(entry.key(), data));
		}
		zone.putColumns(added);
		for (long key : added.keySet()) {
			Rerender.column(zone.zone, ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
		}
	}

	public static void onChunkLoad(WorldChunk chunk) {
		if (lightZones.isEmpty()) {
			return;
		}
		int cx = chunk.getPos().x;
		int cz = chunk.getPos().z;
		long key = chunk.getPos().toLong();
		for (LightZone zone : lightSet.index().inColumn(cx, cz)) {
			if (zone.settings.needsCapture() && !zone.hasColumn(key)) {
				requests.computeIfAbsent(zone.source, k -> new LongLinkedOpenHashSet()).add(key);
			}
		}
	}

	public static void onChunkUnload(WorldChunk chunk) {
		if (lightZones.isEmpty()) {
			return;
		}
		long key = chunk.getPos().toLong();
		for (LightZone zone : lightSet.index().inColumn(chunk.getPos().x, chunk.getPos().z)) {
			zone.removeColumn(key);
			LongLinkedOpenHashSet pending = requests.get(zone.source);
			if (pending != null) {
				pending.remove(key);
			}
		}
	}

	/** The hooks see the zones only while the player wants the suppressed light drawn. */
	private static void applyLightSet() {
		ZoneHolder holder = holder();
		if (holder != null) {
			holder.light = ClientConfig.displaySuppressedLight() ? lightSet : LightZoneSet.EMPTY;
		}
	}

	public static void onDisplayModeChanged() {
		applyLightSet();
		for (LightZone zone : lightZones.values()) {
			Rerender.zone(zone.zone);
		}
	}

	// ------------------------------------------------------------------ block entities

	public static void onBlockEntityLoad(SuppressionBlockEntity be, ClientWorld w) {
		ensureWorld(w);
		blockEntities.add(be);
	}

	public static void onBlockEntityUnload(SuppressionBlockEntity be) {
		blockEntities.remove(be);
	}
}
