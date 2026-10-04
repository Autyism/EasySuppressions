package io.github.autyism.easysuppressions.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.IdentityHashMap;
import java.util.Map;

/** Owns the {@link WorldZones} of every loaded server world. Server thread only. */
public final class ServerZones {
	private static final Map<ServerWorld, WorldZones> WORLDS = new IdentityHashMap<>();

	private ServerZones() {
	}

	public static void init() {
		ServerWorldEvents.LOAD.register((server, world) -> of(world));
		ServerWorldEvents.UNLOAD.register((server, world) -> {
			WorldZones zones = WORLDS.remove(world);
			if (zones != null) {
				zones.save();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORLDS.clear());
		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			if (world.getServer().isOnThread()) {
				of(world).onChunkLoad(chunk);
			} else {
				world.getServer().execute(() -> of(world).onChunkLoad(chunk));
			}
		});
		// A chunk that dropped to "inaccessible" without unloading and comes back fires no CHUNK_LOAD.
		ServerChunkEvents.CHUNK_LEVEL_TYPE_CHANGE.register((world, chunk, oldType, newType) -> {
			if (oldType == net.minecraft.server.world.ChunkLevelType.INACCESSIBLE && newType != net.minecraft.server.world.ChunkLevelType.INACCESSIBLE) {
				if (world.getServer().isOnThread()) {
					of(world).onChunkLoad(chunk);
				} else {
					world.getServer().execute(() -> of(world).onChunkLoad(chunk));
				}
			}
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
			WorldZones zones = WORLDS.get(world);
			if (zones != null) {
				if (world.getServer().isOnThread()) {
					zones.onChunkUnload(chunk);
				} else {
					world.getServer().execute(() -> zones.onChunkUnload(chunk));
				}
			}
		});
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			WorldZones zones = WORLDS.get(world);
			if (zones != null) {
				zones.tick();
			}
		});
	}

	public static WorldZones of(ServerWorld world) {
		WorldZones zones = WORLDS.get(world);
		if (zones == null) {
			zones = new WorldZones(world);
			WORLDS.put(world, zones);
		}
		return zones;
	}

	/** Called from the ServerWorld save mixin. */
	public static void onWorldSave(ServerWorld world) {
		WorldZones zones = WORLDS.get(world);
		if (zones != null) {
			zones.save();
		}
	}
}
