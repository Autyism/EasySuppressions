package io.github.autyism.easysuppressions.client.selftest;

import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.block.LightSuppressorBlockEntity;
import io.github.autyism.easysuppressions.block.PortalCutterBlockEntity;
import io.github.autyism.easysuppressions.block.SuppressionBlockEntity;
import io.github.autyism.easysuppressions.client.TestHooks;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The test scripts. Each scenario adds steps to the queue; they run in order. */
final class Scenes {
	static final int Y = -60; // first air block above the superflat ground
	final SelfTest t;
	private final Set<String> selected;

	Scenes(SelfTest t) {
		this.t = t;
		String list = System.getProperty("es.scenarios", "all");
		this.selected = new HashSet<>(Arrays.asList(list.split(",")));
	}

	private boolean wants(String name) {
		return selected.contains("all") || selected.contains(name);
	}

	void build() {
		t.waitUntil("main menu", c -> c.getOverlay() == null && c.currentScreen != null && c.world == null, 20 * 180);
		t.run("prepare client", t::prepareClient);
		String connect = System.getProperty("es.connect");
		if (connect != null && !connect.isEmpty()) {
			RemoteTests remote = new RemoteTests(this);
			remote.connect(connect);
			if (wants("remote")) {
				remote.remote();
			}
			if (selected.stream().anyMatch(n -> n.startsWith("kicked"))) {
				remote.kicked(System.getProperty("es.expect", ""));
			}
			return;
		}
		t.run("create world", t::createFreshWorld);
		waitInWorld();
		t.run("world rules", c -> {
			SelfTest.command(c, "gamerule advance_time false");
			SelfTest.command(c, "gamerule advance_weather false");
			SelfTest.command(c, "gamerule spawn_mobs false");
			SelfTest.command(c, "time set 6000");
			SelfTest.command(c, "weather clear");
			SelfTest.command(c, "difficulty peaceful");
			SelfTest.command(c, "kill @e[type=!player]");
			SelfTest.command(c, "easysuppressions config blockBreakSeconds 10");
			SelfTest.command(c, "easysuppressions config portalPickaxeSeconds 1");
		});
		t.waitTicks("rules applied", 10);

		if (wants("smoke")) {
			smoke();
		}
		if (wants("shader")) {
			shader();
		}
		BlockTests blocks = new BlockTests(this);
		if (wants("blocks")) {
			blocks.redstoneAndObservers();
			blocks.pistons();
			blocks.explosions();
			blocks.wither();
		}
		if (wants("mining")) {
			blocks.mining();
		}
		if (wants("registry")) {
			blocks.registryCleanup();
		}
		ItemTests items = new ItemTests(this);
		if (wants("items")) {
			items.recipes();
			items.anvil();
			items.selector();
			items.placement();
			items.restrictions();
		}
		if (wants("cutter")) {
			new CutterTests(this).run();
		}
		LightTests light = new LightTests(this);
		if (wants("light") || wants("mobfarm")) {
			light.mobFarm();
		}
		if (wants("light") || wants("lockcurrent")) {
			light.lockCurrent();
			light.overlap();
		}
		if (wants("light") || wants("unloaded")) {
			light.unloaded();
		}
		if (wants("light") || wants("display")) {
			light.display();
		}
		if (wants("gui")) {
			items.screens();
		}
		if (wants("persist")) {
			light.persistence();
		}
	}

	void waitInWorld() {
		t.waitUntil("in world", c -> c.world != null && c.player != null && c.getOverlay() == null && c.currentScreen == null, 20 * 180);
		t.waitTicks("settle", 60);
	}

	// ------------------------------------------------------------------ helpers

	static SuppressionBlockEntity be(ServerWorld world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof SuppressionBlockEntity be ? be : null;
	}

	static ServerPlayerEntity sp(MinecraftServer server) {
		return SelfTest.serverPlayer(server);
	}

	void server(String name, Consumer<ServerWorld> action) {
		t.onServer(name, s -> action.accept(s.getOverworld()));
	}

	void serverCheck(String what, Predicate<ServerWorld> test) {
		t.onServer("check " + what, s -> t.check(what, test.test(s.getOverworld())));
	}

	void cmd(String... commands) {
		t.run("commands " + commands[0], c -> {
			for (String command : commands) {
				SelfTest.command(c, command);
			}
		});
		t.waitTicks("commands applied", 4);
	}

	void tp(double x, double y, double z, float yaw, float pitch) {
		t.run("teleport", c -> SelfTest.command(c, String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch)));
		t.waitTicks("teleported", 10);
	}

	/** Teleports to (x, y, z) and turns the camera to the centre of {@code target}. */
	void tpLook(double x, double y, double z, BlockPos target) {
		tpLookAt(x, y, z, Vec3d.ofCenter(target));
	}

	/** Teleports to (x, y, z) and turns the camera to a point. */
	void tpLookAt(double x, double y, double z, Vec3d target) {
		Vec3d eye = new Vec3d(x, y + 1.62, z);
		Vec3d d = target.subtract(eye);
		float yaw = (float) (MathHelper.atan2(d.z, d.x) * 57.29577951308232) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 57.29577951308232);
		tp(x, y, z, yaw, pitch);
	}

	void gamemode(String mode) {
		cmd("gamemode " + mode + " @s");
	}

	void hold(ItemStack stack) {
		ItemStack copy = stack.copy();
		t.onServer("hold " + copy.getItem(), s -> {
			sp(s).getInventory().setSelectedSlot(0);
			sp(s).setStackInHand(Hand.MAIN_HAND, copy.copy());
		});
		t.waitTicks("held item synced", 4);
	}

	static ItemStack enchanted(ServerWorld w, ItemStack stack, RegistryKey<Enchantment> key, int level) {
		stack.addEnchantment(w.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(key), level);
		return stack;
	}

	/**
	 * Holds the attack key (real client path: crosshair, break progress, packets) until the block
	 * is gone on the client. {@code progressTicks} is the expected number of ticks from the moment
	 * breaking starts (vanilla's 5 tick cooldown after the previous break is not counted); 0 means
	 * instant.
	 */
	void mine(String what, BlockPos pos, int progressTicks, int timeout) {
		int[] total = {0};
		int[] since = {-1};
		t.run("start mining " + what, c -> {
			total[0] = 0;
			since[0] = -1;
			TestHooks.forceAttackHeld = true;
		});
		t.waitUntil("block gone: " + what, c -> {
			total[0]++;
			if (since[0] >= 0) {
				since[0]++;
			} else if (c.interactionManager.isBreakingBlock()) {
				since[0] = 0;
			}
			if (total[0] % 40 == 0) {
				t.info("mining {}: tick {} crosshair={} breaking={} player={}", what, total[0], c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bh ? bh.getBlockPos() + " " + bh.getType() : c.crosshairTarget,
						c.interactionManager.isBreakingBlock(), c.player.getBlockPos());
			}
			return c.world.getBlockState(pos).isAir();
		}, timeout);
		t.run("stop mining " + what, c -> {
			TestHooks.forceAttackHeld = false;
			t.info("{}: {} ticks after breaking started, {} in total (expected {})", what, since[0], total[0], progressTicks);
			boolean ok = progressTicks == 0 ? total[0] <= 2 && since[0] < 0 : Math.abs(since[0] - progressTicks) <= 1;
			t.check(what + " takes " + progressTicks + " ticks (measured " + (progressTicks == 0 ? total[0] : since[0]) + ")", ok);
		});
		t.waitTicks("break cooldown", 8);
	}

	/** Holds the attack key for a while and checks the block is still there (and no progress shown). */
	void mineNoEffect(String what, BlockPos pos, int ticks) {
		boolean[] progress = {false};
		t.run("start hitting " + what, c -> TestHooks.forceAttackHeld = true);
		int[] n = {0};
		t.waitUntil("hit " + what + " for " + ticks + " ticks", c -> {
			if (c.interactionManager.isBreakingBlock()) {
				progress[0] = true;
			}
			return ++n[0] >= ticks;
		}, ticks + 20);
		t.run("stop hitting " + what, c -> {
			TestHooks.forceAttackHeld = false;
			t.check(what + ": block still on the client", !c.world.getBlockState(pos).isAir());
			t.check(what + ": no break progress", !progress[0]);
		});
		t.onServer("check " + what + " on the server", s -> t.check(what + ": block still on the server", !s.getOverworld().getBlockState(pos).isAir()));
	}

	/** One left click through the real key binding. */
	void leftClick() {
		t.run("left click", c -> {
			t.info("attack key bound to {}, screen {}, main hand {}", c.options.attackKey.getBoundKeyTranslationKey(), c.currentScreen, c.player.getMainHandStack());
			KeyBinding.onKeyPressed(InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_LEFT));
		});
		t.waitTicks("left click handled", 5);
	}

	void rightClick() {
		InputUtil.Key key = InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		t.run("right click", c -> {
			KeyBinding.setKeyPressed(key, true);
			KeyBinding.onKeyPressed(key);
		});
		t.waitTicks("right click held", 1);
		t.run("release right click", c -> KeyBinding.setKeyPressed(key, false));
		t.waitTicks("right click handled", 5);
	}

	static void set(World w, BlockPos pos, Block block) {
		w.setBlockState(pos, block.getDefaultState());
	}

	static void fill(World w, BlockPos a, BlockPos b, BlockState state) {
		for (BlockPos p : BlockPos.iterate(a, b)) {
			w.setBlockState(p, state);
		}
	}

	static int count(World w, BlockPos a, BlockPos b, Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.iterate(a, b)) {
			if (w.getBlockState(p).isOf(block)) {
				n++;
			}
		}
		return n;
	}

	static PortalCutterBlockEntity placeCutter(ServerWorld w, BlockPos pos, Zone zone) {
		set(w, pos, ModContent.PORTAL_CUTTER_BLOCK);
		PortalCutterBlockEntity be = (PortalCutterBlockEntity) be(w, pos);
		if (zone != null) {
			be.setZone(w, zone);
		}
		return be;
	}

	static LightSuppressorBlockEntity placeLight(ServerWorld w, BlockPos pos, Zone zone) {
		set(w, pos, ModContent.LIGHT_SUPPRESSOR_BLOCK);
		LightSuppressorBlockEntity be = (LightSuppressorBlockEntity) be(w, pos);
		if (zone != null) {
			be.setZone(zone);
		}
		return be;
	}

	// ------------------------------------------------------------------ smoke

	private void smoke() {
		BlockPos cutter = new BlockPos(0, Y, 0);
		BlockPos light = new BlockPos(3, Y, 0);
		server("place both blocks", w -> {
			placeCutter(w, cutter, new Zone(-2, Y - 1, -2, 2, Y + 3, 2));
			placeLight(w, light, new Zone(3, Y - 1, -1, 6, Y + 2, 2));
		});
		tp(1.5, Y + 3, -11.5, 0F, 22F);
		t.waitTicks("render", 30);
		t.screenshot("blocks_inactive");
		server("power the cutter and lock the light suppressor", w -> {
			set(w, cutter.south(), Blocks.REDSTONE_BLOCK);
			LightSuppressorBlockEntity ls = (LightSuppressorBlockEntity) be(w, light);
			ls.setSettings(LightSettings.MOB_FARM);
			ls.lock(w, null);
		});
		t.waitTicks("render", 30);
		serverCheck("cutter is active on the server", w -> be(w, cutter).isActive());
		serverCheck("light suppressor is locked on the server", w -> be(w, light).isActive());
		t.run("check active flags on the client", c -> {
			t.check("client sees the cutter active", c.world.getBlockEntity(cutter) instanceof SuppressionBlockEntity sbe && sbe.isActive());
			t.check("client sees the light suppressor locked", c.world.getBlockEntity(light) instanceof SuppressionBlockEntity sbe && sbe.isActive());
		});
		t.screenshot("blocks_active");
		tp(1.5, Y + 0.5, -2.5, 0F, 10F);
		t.waitTicks("render", 20);
		t.screenshot("blocks_active_close");
		server("clean up smoke", w -> {
			set(w, cutter.south(), Blocks.AIR);
			set(w, cutter, Blocks.AIR);
			set(w, light, Blocks.AIR);
		});
		t.run("give items", c -> {
			SelfTest.command(c, "clear @s");
			SelfTest.command(c, "give @s easysuppressions:portal_cutter_block 64");
			SelfTest.command(c, "give @s easysuppressions:light_suppressor_block 64");
			SelfTest.command(c, "give @s easysuppressions:portal_cutter_selector");
			SelfTest.command(c, "give @s easysuppressions:light_suppressor_selector");
			SelfTest.command(c, "give @s easysuppressions:portal_pickaxe");
		});
		t.waitTicks("items", 10);
		t.run("open inventory", c -> c.setScreen(new InventoryScreen(c.player)));
		t.waitTicks("inventory", 10);
		t.screenshot("inventory");
		t.run("close inventory", c -> c.setScreen(null));
		cmd("clear @s");
	}

	/** Close-ups of both blocks, inactive and active, by day and by night (for shader packs). */
	private void shader() {
		BlockPos c1 = new BlockPos(-40, Y, 0);
		BlockPos c2 = new BlockPos(-38, Y, 0);
		BlockPos l1 = new BlockPos(-36, Y, 0);
		BlockPos l2 = new BlockPos(-34, Y, 0);
		server("blocks for close-ups", w -> {
			placeCutter(w, c1, new Zone(-41, Y, -1, -39, Y + 1, 1));
			placeCutter(w, c2, new Zone(-38, Y, 0, -38, Y, 0));
			set(w, c2.down(), Blocks.REDSTONE_BLOCK);
			placeLight(w, l1, new Zone(-36, Y, 0, -36, Y, 0));
			LightSuppressorBlockEntity locked = placeLight(w, l2, new Zone(-34, Y, 0, -34, Y, 0));
			locked.setSettings(new LightSettings(io.github.autyism.easysuppressions.light.ChannelSetting.NONE,
					io.github.autyism.easysuppressions.light.ChannelSetting.fixed(15)));
			locked.lock(w, null);
		});
		t.run("hide the zone boxes for the close-ups", c -> {
			for (BlockPos p : new BlockPos[]{c1, c2, l1, l2}) {
				io.github.autyism.easysuppressions.client.ClientConfig.setZoneShown(p, false);
			}
		});
		tp(-36.5, Y + 0.5, -3.2, 0F, 18F);
		t.waitTicks("render", 60);
		t.screenshot("shader_day_inactive_active");
		cmd("time set 18000");
		t.waitTicks("render", 60);
		t.screenshot("shader_night_inactive_active");
		tp(-38.5, Y + 0.3, -1.6, 0F, 22F);
		t.waitTicks("render", 40);
		t.screenshot("shader_night_cutter_active_close");
		cmd("time set 6000");
		t.run("show the zone boxes again", c -> {
			for (BlockPos p : new BlockPos[]{c1, c2, l1, l2}) {
				io.github.autyism.easysuppressions.client.ClientConfig.setZoneShown(p, true);
			}
		});
	}

	static ItemStack stack(net.minecraft.item.Item item) {
		return new ItemStack(item);
	}

	static ItemStack pickaxe(ServerWorld w) {
		return enchanted(w, new ItemStack(Items.DIAMOND_PICKAXE), Enchantments.EFFICIENCY, 5);
	}

	static void haste(ServerPlayerEntity p, int amplifier) {
		p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 20 * 600, amplifier));
	}
}
