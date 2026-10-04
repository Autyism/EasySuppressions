package io.github.autyism.easysuppressions.client.selftest;

import io.github.autyism.easysuppressions.block.LightSuppressorBlockEntity;
import io.github.autyism.easysuppressions.client.ClientConfig;
import io.github.autyism.easysuppressions.client.ClientZones;
import io.github.autyism.easysuppressions.client.TestHooks;
import io.github.autyism.easysuppressions.light.LightCapture;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.light.LightZone;
import io.github.autyism.easysuppressions.net.Payloads;
import io.github.autyism.easysuppressions.world.EsWorldState;
import io.github.autyism.easysuppressions.world.ServerZones;
import io.github.autyism.easysuppressions.world.WorldZones;
import io.github.autyism.easysuppressions.zone.Zone;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.Optional;

import static io.github.autyism.easysuppressions.client.selftest.Scenes.Y;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.be;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.placeLight;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.set;

/** Light suppressor acceptance tests. */
final class LightTests {
	private final Scenes s;
	private final SelfTest t;

	// Mob farm platform
	private static final int PX = 400;
	private static final int PZ = 0;
	private static final Zone FARM = new Zone(PX, Y - 2, PZ, PX + 14, Y + 3, PZ + 14);
	private static final BlockPos FARM_BLOCK = new BlockPos(PX + 7, Y - 2, PZ + 7);
	private static final BlockPos TORCH = new BlockPos(PX + 13, Y, PZ + 7);

	// Lock-current zone (also checked after the world is reopened)
	private static final Zone LOCK = new Zone(440, Y - 2, 0, 452, Y + 6, 12);
	private static final BlockPos LOCK_BLOCK = new BlockPos(446, Y - 2, 6);
	private static byte[][] lockSnapshot;

	LightTests(Scenes s) {
		this.s = s;
		this.t = s.t;
	}

	private void action(BlockPos pos, Payloads.Action action, Zone zone, LightSettings settings) {
		t.run("GUI action " + action, c -> {
			TestHooks.lastScreenMessage = null;
			ClientPlayNetworking.send(new Payloads.ScreenAction(pos, action, Optional.ofNullable(zone), settings));
		});
		t.waitTicks("action answered", 6);
	}

	void mobFarm() {
		s.server("stone platform, light suppressor underneath with redstone", w -> {
			Scenes.fill(w, new BlockPos(PX, Y - 1, PZ), new BlockPos(PX + 14, Y - 1, PZ + 14), Blocks.STONE.getDefaultState());
			placeLight(w, FARM_BLOCK, null);
			set(w, FARM_BLOCK.down(), Blocks.REDSTONE_BLOCK);
		});
		s.tp(PX + 7.5, Y, PZ + 4.5, 0F, 20F);
		s.serverCheck("before: real sky light 15 at noon on the platform", w -> w.getLightLevel(LightType.SKY, FARM_BLOCK.up(2)) == 15);
		action(FARM_BLOCK, Payloads.Action.LOCK, FARM, LightSettings.MOB_FARM);
		s.serverCheck("locked with the mob farm preset", w -> be(w, FARM_BLOCK).isActive());
		s.serverCheck("in the zone: sky 0, block 0, sky not visible, dark enough for monsters at noon", w -> {
			BlockPos p = new BlockPos(PX + 3, Y, PZ + 3);
			Random r = Random.create(1);
			boolean dark = true;
			for (int i = 0; i < 20; i++) {
				dark &= HostileEntity.isSpawnDark(w, p, r);
			}
			return w.getLightLevel(LightType.SKY, p) == 0 && w.getLightLevel(LightType.BLOCK, p) == 0 && !w.isSkyVisible(p) && dark
					&& w.getLightLevel(p) == 0;
		});
		s.serverCheck("just outside the zone: still bright", w -> {
			BlockPos p = new BlockPos(PX - 2, Y, PZ + 3);
			return w.getLightLevel(LightType.SKY, p) == 15 && !HostileEntity.isSpawnDark(w, p, Random.create(1));
		});
		s.server("a torch inside the zone, near its edge", w -> set(w, TORCH, Blocks.TORCH));
		t.waitTicks("light engine", 10);
		s.serverCheck("torch: block light in the zone stays 0", w -> w.getLightLevel(LightType.BLOCK, TORCH) == 0
				&& LightCapture.rawLight(w, LightType.BLOCK, TORCH) == 14);
		s.serverCheck("torch still lights up blocks outside the zone", w -> w.getLightLevel(LightType.BLOCK, TORCH.east(3)) == 11);
		s.server("cut the redstone", w -> set(w, FARM_BLOCK.down(), Blocks.DIRT));
		t.waitTicks("unpowered", 5);
		s.serverCheck("without redstone the effect stays", w -> w.getLightLevel(LightType.SKY, new BlockPos(PX + 3, Y, PZ + 3)) == 0);
		t.run("client draws the set light", c -> t.check("client light in the zone is 0 (display: set light)",
				c.world.getLightLevel(LightType.SKY, new BlockPos(PX + 3, Y, PZ + 3)) == 0));

		// Real spawning in daylight on the platform; the player must be 24+ blocks away.
		s.cmd("gamerule spawn_mobs true", "difficulty normal");
		s.tp(PX + 7.5, Y + 6, PZ - 30.5, 0F, 15F);
		int[] waited = {0};
		t.waitUntilCheck("monsters spawn on the platform at noon", c -> {
			if (++waited[0] % 20 != 0) {
				return false;
			}
			boolean[] found = {false};
			c.getServer().submit(() -> found[0] = !c.getServer().getOverworld().getEntitiesByClass(HostileEntity.class,
					new Box(PX, Y - 1, PZ, PX + 15, Y + 4, PZ + 15), e -> true).isEmpty()).join();
			return found[0];
		}, 20 * 120);
		t.waitTicks("a few more", 100);
		s.cmd("gamerule spawn_mobs false");
		s.tp(PX + 7.5, Y + 5, PZ - 9.5, 0F, 28F);
		s.gamemode("creative");
		t.waitTicks("render", 20);
		t.screenshot("mob_farm_noon_spawning");
		s.serverCheck("monsters standing in the zone at noon", w -> !w.getEntitiesByClass(HostileEntity.class,
				new Box(PX, Y - 1, PZ, PX + 15, Y + 4, PZ + 15), e -> true).isEmpty());
		s.cmd("kill @e[type=!player]", "difficulty peaceful");
		s.server("power again to open the GUI", w -> set(w, FARM_BLOCK.down(), Blocks.REDSTONE_BLOCK));
		s.tp(PX + 7.5, Y, PZ + 4.5, 0F, 20F);
		action(FARM_BLOCK, Payloads.Action.UNLOCK, null, LightSettings.MOB_FARM);
		s.serverCheck("unlocked: real light at once", w -> !be(w, FARM_BLOCK).isActive()
				&& w.getLightLevel(LightType.SKY, new BlockPos(PX + 3, Y, PZ + 3)) == 15
				&& w.getLightLevel(LightType.BLOCK, TORCH) == 14);
	}

	/** Records the real value of every block of the zone, sky then block channel. */
	private static byte[][] snapshot(ServerWorld w, Zone zone) {
		int n = (int) (zone.sizeX() * zone.sizeY() * zone.sizeZ());
		byte[][] out = new byte[2][n];
		int i = 0;
		for (BlockPos p : BlockPos.iterate(zone.min(), zone.max())) {
			out[0][i] = (byte) LightCapture.rawLight(w, LightType.SKY, p);
			out[1][i] = (byte) LightCapture.rawLight(w, LightType.BLOCK, p);
			i++;
		}
		return out;
	}

	/** Number of blocks where the (hooked) light query differs from the snapshot. */
	private static int differences(World w, Zone zone, byte[][] snap) {
		int i = 0;
		int diff = 0;
		for (BlockPos p : BlockPos.iterate(zone.min(), zone.max())) {
			if (w.getLightLevel(LightType.SKY, p) != snap[0][i] || w.getLightLevel(LightType.BLOCK, p) != snap[1][i]) {
				diff++;
			}
			i++;
		}
		return diff;
	}

	void lockCurrent() {
		s.server("a room with torches, a roof and a hole", w -> {
			set(w, LOCK_BLOCK, Blocks.AIR);
			Scenes.fill(w, new BlockPos(442, Y, 2), new BlockPos(450, Y + 3, 10), Blocks.GLASS.getDefaultState());
			Scenes.fill(w, new BlockPos(443, Y, 3), new BlockPos(449, Y + 2, 9), Blocks.AIR.getDefaultState());
			Scenes.fill(w, new BlockPos(442, Y + 4, 2), new BlockPos(450, Y + 4, 10), Blocks.STONE.getDefaultState());
			set(w, new BlockPos(446, Y + 4, 6), Blocks.AIR);
			set(w, new BlockPos(444, Y, 4), Blocks.TORCH);
			set(w, new BlockPos(448, Y + 1, 8), Blocks.GLOWSTONE);
			placeLight(w, LOCK_BLOCK, null);
			set(w, LOCK_BLOCK.down(), Blocks.REDSTONE_BLOCK);
		});
		t.waitTicks("light settles", 20);
		t.onServer("snapshot the real light", srv -> lockSnapshot = snapshot(srv.getOverworld(), LOCK));
		s.tp(446.5, Y, -2.5, 0F, 10F);
		action(LOCK_BLOCK, Payloads.Action.LOCK, LOCK, LightSettings.SIMPLE);
		s.serverCheck("locked current", w -> be(w, LOCK_BLOCK).isActive());
		s.serverCheck("every block reads exactly the recorded value", w -> {
			int diff = differences(w, LOCK, lockSnapshot);
			t.info("differences right after locking: {}", diff);
			return diff == 0;
		});
		s.server("change the room: torch out, roof off, new light source", w -> {
			set(w, new BlockPos(444, Y, 4), Blocks.AIR);
			Scenes.fill(w, new BlockPos(442, Y + 4, 2), new BlockPos(450, Y + 4, 10), Blocks.AIR.getDefaultState());
			set(w, new BlockPos(447, Y, 5), Blocks.SEA_LANTERN);
		});
		t.waitTicks("light engine", 20);
		s.serverCheck("real light changed (under the old roof)", w -> LightCapture.rawLight(w, LightType.SKY, new BlockPos(444, Y + 1, 4)) == 15
				&& lockSnapshot[0][index(new BlockPos(444, Y + 1, 4))] != 15);
		s.serverCheck("queries still return the recorded values (exact for every block)", w -> differences(w, LOCK, lockSnapshot) == 0);
		t.waitTicks("client data", 10);
		t.run("client draws the recorded values", c -> {
			int diff = differences(c.world, LOCK, lockSnapshot);
			t.info("client differences: {}", diff);
			t.check("client light equals the recorded values", diff == 0);
		});
		s.server("cut the redstone (effect must stay; reopened later)", w -> set(w, LOCK_BLOCK.down(), Blocks.DIRT));
	}

	private static int index(BlockPos p) {
		int i = 0;
		for (BlockPos q : BlockPos.iterate(LOCK.min(), LOCK.max())) {
			if (q.equals(p)) {
				return i;
			}
			i++;
		}
		return -1;
	}

	void overlap() {
		BlockPos second = new BlockPos(455, Y - 2, 6);
		Zone overlapping = new Zone(450, Y - 2, 0, 460, Y + 3, 12);
		s.server("second light suppressor overlapping the locked zone", w -> {
			placeLight(w, second, null);
			set(w, second.down(), Blocks.REDSTONE_BLOCK);
		});
		s.tp(455.5, Y, 2.5, 0F, 20F);
		action(second, Payloads.Action.LOCK, overlapping, LightSettings.MOB_FARM);
		s.serverCheck("the overlapping zone was not locked", w -> !be(w, second).isActive() && ServerZones.of(w).lightEntry(second) == null);
		t.run("the GUI message names the other block", c -> {
			String msg = TestHooks.lastScreenMessage == null ? "" : TestHooks.lastScreenMessage.getString();
			t.info("overlap message: {}", msg);
			t.check("overlap message names 446, -62, 6", msg.contains("446, -62, 6"));
		});
		s.server("remove the second block", w -> {
			set(w, second, Blocks.AIR);
			set(w, second.down(), Blocks.DIRT);
		});
	}

	void unloaded() {
		int x0 = 600;
		int length = 16 * 44;
		Zone big = new Zone(x0, Y - 2, 0, x0 + length - 1, Y + 3, 15);
		BlockPos block = new BlockPos(x0 + 5, Y - 2, 5);
		int[] oldDistance = {0};
		t.run("short view distance, so unloaded columns are near", c -> {
			oldDistance[0] = c.options.getViewDistance().getValue();
			c.options.getViewDistance().setValue(6);
		});
		s.tp(x0 + 5.5, Y, 2.5, -90F, 10F);
		t.waitTicks("chunks load", 40);
		s.server("light suppressor with a long zone", w -> {
			placeLight(w, block, null);
			set(w, block.down(), Blocks.REDSTONE_BLOCK);
		});
		action(block, Payloads.Action.LOCK, big, LightSettings.SIMPLE);
		long[] pendingAtLock = {0};
		s.serverCheck("part of the zone is not loaded yet: some columns wait", w -> {
			LightZone zone = ServerZones.of(w).lightZone(block);
			WorldZones.Progress p = WorldZones.progress(zone);
			pendingAtLock[0] = p.pending();
			t.info("lock of a long zone: total {} recorded {} pending {}", p.total(), p.recorded(), p.pending());
			return zone != null && p.total() == ((x0 + length - 1) >> 4) - (x0 >> 4) + 1 && p.pending() > 5 && p.recorded() > 0;
		});
		s.serverCheck("no column is read without data: unrecorded columns pass the real light through", w -> {
			LightZone zone = ServerZones.of(w).lightZone(block);
			int far = x0 + length - 8;
			return zone.column(far >> 4, 0) == null && w.getLightLevel(LightType.SKY, new BlockPos(far, Y + 1, 5)) == LightCapture.rawLight(w, LightType.SKY, new BlockPos(far, Y + 1, 5));
		});
		t.run("open the GUI to see the progress", c -> ClientPlayNetworking.send(new Payloads.RequestScreen(block)));
		t.waitTicks("data", 5);
		t.run("open the light suppressor GUI", c -> {
			if (!(c.currentScreen instanceof io.github.autyism.easysuppressions.client.screen.SuppressorScreen)) {
				c.getServer().execute(() -> io.github.autyism.easysuppressions.net.ServerNet.openScreen(Scenes.sp(c.getServer()),
						Scenes.be(c.getServer().getOverworld(), block)));
			}
		});
		t.waitTicks("gui", 15);
		t.screenshot("gui_pending_columns");
		t.run("close", c -> c.setScreen(null));
		t.waitTicks("highlight list", 50);
		s.tp(x0 + 40.5, Y + 14, -30.5, -78F, 8F);
		t.waitTicks("render", 30);
		t.screenshot("pending_columns_highlight");
		t.run("client has the pending list", c -> {
			Payloads.LightZoneInfo info = ClientZones.lightInfo().get(block);
			t.check("client got pending columns to highlight", info != null && !info.pendingNear().isEmpty());
		});
		// Walk (teleport) along the zone so every column loads once.
		for (int step = 1; step <= 11; step++) {
			s.tp(x0 + 5.5 + step * 64, Y + 2, 7.5, -90F, 10F);
			t.waitTicks("chunks load", 40);
		}
		t.waitUntilCheck("every column recorded", c -> {
			boolean[] done = {false};
			c.getServer().submit(() -> done[0] = WorldZones.progress(ServerZones.of(c.getServer().getOverworld()).lightZone(block)).pending() == 0).join();
			return done[0];
		}, 400);
		t.onServer("pending diagnostics", srv -> {
			ServerWorld w = srv.getOverworld();
			LightZone zone = ServerZones.of(w).lightZone(block);
			var pending = WorldZones.nearestPending(zone, x0 >> 4, 0, 20, 100);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < pending.size(); i++) {
				long key = pending.getLong(i);
				var chunk = w.getChunkManager().getWorldChunk(net.minecraft.util.math.ChunkPos.getPackedX(key), net.minecraft.util.math.ChunkPos.getPackedZ(key));
				sb.append(new net.minecraft.util.math.ChunkPos(key)).append(chunk == null ? " unloaded" : " loaded light=" + chunk.isLightOn()).append("; ");
			}
			t.info("pending after the walk: {} -> {}", WorldZones.progress(zone), sb);
		});
		s.serverCheck("completion was announced once", w -> {
			EsWorldState.SuppressorEntry e = ServerZones.of(w).lightEntry(block);
			return e != null && e.completeNotified();
		});
		s.serverCheck("late columns read exactly what was recorded", w -> {
			LightZone zone = ServerZones.of(w).lightZone(block);
			int x = x0 + length - 6;
			for (int y = Y - 2; y <= Y + 3; y++) {
				for (int z = 0; z < 16; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (zone.get(io.github.autyism.easysuppressions.light.LightChannel.SKY, x, y, z) != LightCapture.rawLight(w, LightType.SKY, p)) {
						return false;
					}
				}
			}
			return true;
		});
		s.tp(x0 + 40.5, Y + 14, -30.5, -78F, 8F);
		t.waitTicks("render", 60);
		t.screenshot("pending_columns_gone");
		t.run("view distance back", c -> c.options.getViewDistance().setValue(oldDistance[0]));
		t.run("highlight list is empty", c -> {
			Payloads.LightZoneInfo info = ClientZones.lightInfo().get(block);
			t.check("no more pending columns on the client", info != null && info.pendingNear().isEmpty() && info.recorded() == info.total());
		});
		s.tp(x0 + 5.5, Y, 2.5, -90F, 10F);
		action(block, Payloads.Action.UNLOCK, null, LightSettings.SIMPLE);
		s.serverCheck("long zone unlocked", w -> ServerZones.of(w).lightEntry(block) == null);
	}

	void display() {
		// Night, torches on the platform, mob farm preset: dark with "set light", bright with "real light".
		s.server("night platform with torches", w -> {
			Scenes.fill(w, new BlockPos(PX, Y - 1, PZ), new BlockPos(PX + 14, Y - 1, PZ + 14), Blocks.STONE.getDefaultState());
			if (!(w.getBlockEntity(FARM_BLOCK) instanceof LightSuppressorBlockEntity)) {
				placeLight(w, FARM_BLOCK, null);
			}
			set(w, TORCH, Blocks.TORCH);
			set(w, new BlockPos(PX + 3, Y, PZ + 10), Blocks.TORCH);
			set(w, new BlockPos(PX + 10, Y, PZ + 3), Blocks.TORCH);
			set(w, FARM_BLOCK.down(), Blocks.REDSTONE_BLOCK);
		});
		s.cmd("time set 18000");
		s.tp(PX + 7.5, Y, PZ + 4.5, 0F, 20F);
		action(FARM_BLOCK, Payloads.Action.LOCK, FARM, LightSettings.MOB_FARM);
		s.tp(PX + 7.5, Y + 6, PZ - 8.5, 0F, 30F);
		t.waitTicks("render", 40);
		s.serverCheck("display scene locked", w -> be(w, FARM_BLOCK).isActive());
		t.screenshot("display_set_light_night");
		t.run("switch to real light", c -> ClientConfig.setDisplaySuppressedLight(false));
		t.waitTicks("render", 40);
		t.run("client shows real light", c -> t.check("client reads the real block light with 'real light'",
				c.world.getLightLevel(LightType.BLOCK, TORCH) == 14));
		t.screenshot("display_real_light_night");
		t.run("back to set light", c -> ClientConfig.setDisplaySuppressedLight(true));
		t.waitTicks("render", 20);
		s.cmd("time set 6000");
		s.tp(PX + 7.5, Y, PZ + 4.5, 0F, 20F);
		action(FARM_BLOCK, Payloads.Action.UNLOCK, null, LightSettings.MOB_FARM);
	}

	/** Leaves the world and opens it again: locked zones, recorded light and cutter zones must survive. */
	void persistence() {
		s.cmd("save-all flush");
		t.waitTicks("saved", 20);
		t.run("leave the world", c -> c.send(() -> {
			if (c.world != null) {
				c.world.disconnect(net.minecraft.client.world.ClientWorld.QUITTING_MULTIPLAYER_TEXT);
			}
			c.disconnectWithSavingScreen();
		}));
		t.waitUntil("title screen", c -> c.world == null && c.currentScreen != null, 20 * 60);
		t.waitTicks("settle", 20);
		t.run("reopen the world", t::openExistingWorld);
		s.waitInWorld();
		s.tp(446.5, Y, -2.5, 0F, 10F);
		t.waitTicks("chunks", 40);
		s.serverCheck("after reopening: the zone is still locked", w -> ServerZones.of(w).lightEntry(LOCK_BLOCK) != null
				&& be(w, LOCK_BLOCK) instanceof LightSuppressorBlockEntity ls && ls.isLocked());
		s.serverCheck("after reopening: every block still reads the recorded value", w -> {
			if (lockSnapshot == null) {
				return false;
			}
			int diff = differences(w, LOCK, lockSnapshot);
			t.info("differences after reopening: {}", diff);
			return diff == 0;
		});
		t.waitTicks("client data", 20);
		t.run("client after reopening", c -> t.check("client light equals the recorded values after reopening",
				lockSnapshot != null && differences(c.world, LOCK, lockSnapshot) == 0));
		s.tp(446.5, Y + 3, -8.5, 0F, 20F);
		t.waitTicks("render", 20);
		t.screenshot("lock_current_after_reopen");
	}
}
