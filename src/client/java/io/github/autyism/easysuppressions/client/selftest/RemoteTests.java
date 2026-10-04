package io.github.autyism.easysuppressions.client.selftest;

import io.github.autyism.easysuppressions.client.ClientZones;
import io.github.autyism.easysuppressions.client.TestHooks;
import io.github.autyism.easysuppressions.config.EsConfig;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.net.Payloads;
import io.github.autyism.easysuppressions.zone.Zone;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.lang.reflect.Field;
import java.util.Optional;

import static io.github.autyism.easysuppressions.client.selftest.Scenes.Y;
import static io.github.autyism.easysuppressions.client.selftest.Scenes.count;

/**
 * Runs against a real dedicated server (-Des.connect=host:port): everything goes through the
 * network, nothing touches server objects directly. "remote": cutter and light suppressor on the
 * server. "kicked": expects the login to be refused and records the message.
 */
final class RemoteTests {
	private final Scenes s;
	private final SelfTest t;

	RemoteTests(Scenes s) {
		this.s = s;
		this.t = s.t;
	}

	void connect(String address) {
		t.run("connect to " + address, c -> ConnectScreen.connect(c.currentScreen, c, ServerAddress.parse(address),
				new ServerInfo("es-test", address, ServerInfo.ServerType.OTHER), false, null));
	}

	static String disconnectReason(MinecraftClient c) {
		if (!(c.currentScreen instanceof DisconnectedScreen screen)) {
			return null;
		}
		for (Field f : DisconnectedScreen.class.getDeclaredFields()) {
			if (f.getType() == DisconnectionInfo.class) {
				try {
					f.setAccessible(true);
					return ((DisconnectionInfo) f.get(screen)).reason().getString();
				} catch (ReflectiveOperationException e) {
					return "?";
				}
			}
		}
		return "?";
	}

	void kicked(String expected) {
		t.waitUntilCheck("the server refuses the login", c -> c.currentScreen instanceof DisconnectedScreen, 20 * 60);
		t.waitTicks("screen", 20);
		t.run("refusal text", c -> {
			String reason = disconnectReason(c);
			t.info("disconnect reason: {}", reason);
			t.check("refusal says: " + expected, reason != null && reason.contains(expected));
		});
		t.screenshot("login_refused");
	}

	private void action(BlockPos pos, Payloads.Action action, Zone zone, LightSettings settings) {
		t.run("GUI action " + action, c -> {
			TestHooks.lastScreenMessage = null;
			ClientPlayNetworking.send(new Payloads.ScreenAction(pos, action, Optional.ofNullable(zone), settings));
		});
		t.waitTicks("action answered", 10);
	}

	void remote() {
		s.waitInWorld();
		t.run("mod channel is up", c -> t.check("server speaks the mod's protocol", ClientPlayNetworking.canSend(Payloads.ScreenAction.ID)
				&& EsConfig.synced() != null));
		s.cmd("gamemode creative", "time set 6000", "gamerule advance_time false", "gamerule spawn_mobs false",
				"easysuppressions config blockBreakSeconds 10", "easysuppressions config portalPickaxeSeconds 1");
		// Portal: 6x6 frame, 4x4 portal.
		int x0 = 20;
		int z = 10;
		BlockPos cutter = new BlockPos(x0 + 2, Y, z + 2);
		Zone zone = new Zone(x0, Y, z - 1, x0 + 5, Y + 5, z + 2);
		s.cmd(String.format("fill %d %d %d %d %d %d obsidian", x0, Y, z, x0 + 5, Y + 5, z),
				String.format("fill %d %d %d %d %d %d air", x0 + 1, Y + 1, z, x0 + 4, Y + 4, z),
				String.format("setblock %d %d %d fire", x0 + 1, Y + 1, z),
				String.format("setblock %d %d %d easysuppressions:portal_cutter_block", cutter.getX(), cutter.getY(), cutter.getZ()),
				String.format("setblock %d %d %d redstone_block", cutter.getX(), cutter.getY(), cutter.getZ() + 1));
		BlockPos min = new BlockPos(x0 + 1, Y + 1, z);
		BlockPos max = new BlockPos(x0 + 4, Y + 4, z);
		s.tpLook(x0 + 2.5, Y, z + 4.5, cutter);
		t.waitTicks("world", 20);
		t.run("portal lit", c -> t.check("16 portal blocks", count(c.world, min, max, Blocks.NETHER_PORTAL) == 16));
		action(cutter, Payloads.Action.SAVE_ZONE, zone, LightSettings.DEFAULT);
		t.waitTicks("sync", 10);
		t.run("cutter zone synced", c -> t.check("client got the active cutter zone",
				ClientZones.cutters().stream().anyMatch(i -> i.zone().equals(zone))));
		s.gamemode("survival");
		s.cmd("give @s easysuppressions:portal_pickaxe");
		BlockPos hole = new BlockPos(x0 + 2, Y + 2, z);
		s.tpLook(x0 + 2.5, Y, z - 2.0, hole);
		s.mine("portal pickaxe on the dedicated server", hole, 20, 80);
		t.waitTicks("updates", 10);
		t.run("portal kept", c -> t.check("15 portal blocks left on the server (as seen by the client)", count(c.world, min, max, Blocks.NETHER_PORTAL) == 15));
		s.gamemode("creative");
		s.tp(x0 + 3.0, Y + 2, z - 9.5, 0F, 5F);
		t.waitTicks("render", 20);
		t.screenshot("server_portal_cut");

		// Light suppressor on the server: mob farm preset.
		BlockPos light = new BlockPos(40, Y - 1, 10);
		Zone farm = new Zone(36, Y - 1, 6, 44, Y + 3, 14);
		s.cmd(String.format("setblock %d %d %d easysuppressions:light_suppressor_block", light.getX(), light.getY(), light.getZ()),
				String.format("setblock %d %d %d redstone_block", light.getX(), light.getY() - 1, light.getZ()));
		s.tp(40.5, Y, 7.5, 0F, 20F);
		action(light, Payloads.Action.LOCK, farm, LightSettings.MOB_FARM);
		t.waitTicks("sync", 20);
		t.run("light zone synced", c -> {
			t.check("client got the locked light zone", ClientZones.lightInfo().containsKey(light));
			t.check("client draws sky light 0 in the zone", c.world.getLightLevel(LightType.SKY, new BlockPos(38, Y, 8)) == 0);
		});
		t.run("clear chat", c -> c.inGameHud.getChatHud().clear(false));
		s.tp(40.5, Y + 6, -2.5, 0F, 30F);
		s.cmd("easysuppressions zones list");
		t.waitTicks("render", 30);
		t.screenshotKeepChat("server_light_zone_and_list");
		s.tp(40.5, Y, 7.5, 0F, 20F);
		action(light, Payloads.Action.UNLOCK, null, LightSettings.MOB_FARM);
		t.waitTicks("sync", 20);
		t.run("unlocked", c -> t.check("client back to real light", c.world.getLightLevel(LightType.SKY, new BlockPos(38, Y, 8)) == 15));
		s.cmd(String.format("setblock %d %d %d air", cutter.getX(), cutter.getY(), cutter.getZ()),
				String.format("setblock %d %d %d air", light.getX(), light.getY(), light.getZ()));
		t.waitTicks("removed", 10);
		t.run("registers empty", c -> t.check("removing the blocks emptied the registers",
				ClientZones.cutters().isEmpty() && ClientZones.lightInfo().isEmpty()));
		t.run("leave", c -> c.send(() -> {
			if (c.world != null) {
				c.world.disconnect(net.minecraft.client.world.ClientWorld.QUITTING_MULTIPLAYER_TEXT);
			}
			c.disconnectWithSavingScreen();
		}));
		t.waitUntil("title screen", c -> c.world == null, 20 * 30);
	}
}
