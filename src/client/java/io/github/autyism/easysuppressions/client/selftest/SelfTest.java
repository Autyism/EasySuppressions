package io.github.autyism.easysuppressions.client.selftest;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.tutorial.TutorialStep;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.rule.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Automated in-game test, enabled with {@code -Des.selftest=true} (scenario list with
 * {@code -Des.scenarios=a,b,...}, default all). Creates a fresh flat world, runs the scenario
 * scripts (see {@link Scenes}), takes screenshots ({@code run/screenshots/estest_NN_<stage>.png})
 * and stops the client. Every step logs with the prefix {@code [SelfTest]}; the run ends with
 * {@code [SelfTest] RESULT PASS|FAIL} and {@code [SelfTest] DONE}.
 */
public final class SelfTest {
	static final Logger LOGGER = LoggerFactory.getLogger("easysuppressions/selftest");
	static final String WORLD_NAME = "es_selftest";
	private static final String SCREENSHOT_PREFIX = "estest_";
	private static final long MAX_RUN_MILLIS = 25 * 60 * 1000L;

	private final Deque<Step> steps = new ArrayDeque<>();
	private Step current;
	private int ticksInStep;
	private int screenshotIndex;
	private int failures;
	private int checks;
	private long startMillis;
	private boolean finished;

	private SelfTest() {
	}

	public static void install() {
		SelfTest test = new SelfTest();
		new Scenes(test).build();
		ClientTickEvents.END_CLIENT_TICK.register(test::tick);
		LOGGER.info("[SelfTest] enabled, waiting for the main menu");
	}

	// ---------------------------------------------------------------- world

	void prepareClient(MinecraftClient c) {
		c.options.pauseOnLostFocus = false;
		c.getTutorialManager().setStep(TutorialStep.NONE);
		File[] old = new File(c.runDirectory, ScreenshotRecorder.SCREENSHOTS_DIRECTORY)
				.listFiles((dir, name) -> name.startsWith(SCREENSHOT_PREFIX) && name.endsWith(".png"));
		if (old != null) {
			for (File file : old) {
				file.delete();
			}
		}
	}

	void createFreshWorld(MinecraftClient client) {
		Path dir = client.runDirectory.toPath().resolve("saves").resolve(WORLD_NAME);
		if (Files.exists(dir)) {
			try (Stream<Path> walk = Files.walk(dir)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			} catch (IOException e) {
				fail("could not delete the old test world: " + e);
			}
		}
		LOGGER.info("[SelfTest] creating flat world '{}'", WORLD_NAME);
		LevelInfo levelInfo = new LevelInfo(WORLD_NAME, GameMode.CREATIVE, false, Difficulty.NORMAL, true,
				new GameRules(DataConfiguration.SAFE_MODE.enabledFeatures()), DataConfiguration.SAFE_MODE);
		Screen parent = client.currentScreen;
		client.createIntegratedServerLoader().createAndStart(WORLD_NAME, levelInfo, GeneratorOptions.createTestWorld(),
				WorldPresets::createTestOptions, parent);
	}

	void openExistingWorld(MinecraftClient client) {
		client.createIntegratedServerLoader().start(WORLD_NAME, () -> fail("world load was cancelled"));
	}

	// ---------------------------------------------------------------- engine

	private void tick(MinecraftClient client) {
		if (finished) {
			return;
		}
		if (startMillis == 0L) {
			startMillis = System.currentTimeMillis();
		} else if (System.currentTimeMillis() - startMillis > MAX_RUN_MILLIS) {
			fail("overall timeout" + (current != null ? " during step '" + current.name + "'" : ""));
			finish(client);
			return;
		}
		if (flushCommands(client)) {
			return;
		}
		if (current == null) {
			current = steps.poll();
			ticksInStep = 0;
			if (current == null) {
				finish(client);
				return;
			}
		}
		Step step = current;
		try {
			if (step.body.test(client)) {
				LOGGER.info("[SelfTest] OK   {}", step.name);
				current = null;
			} else if (++ticksInStep > step.timeoutTicks) {
				fail("step '" + step.name + "' timed out after " + step.timeoutTicks + " ticks (screen="
						+ (client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName()) + ")");
				if (step.fatal) {
					finish(client);
				} else {
					current = null;
				}
			}
		} catch (Throwable t) {
			LOGGER.error("[SelfTest] exception in step '{}'", step.name, t);
			fail("step '" + step.name + "' threw " + t);
			finish(client);
		}
	}

	private void finish(MinecraftClient client) {
		finished = true;
		LOGGER.info("[SelfTest] RESULT {} ({} check(s), {} failure(s), {} screenshot(s))", failures == 0 ? "PASS" : "FAIL",
				checks, failures, screenshotIndex);
		LOGGER.info("[SelfTest] DONE");
		client.scheduleStop();
	}

	void fail(String reason) {
		failures++;
		LOGGER.error("[SelfTest] FAIL {}", reason);
	}

	void check(String what, boolean ok) {
		checks++;
		if (ok) {
			LOGGER.info("[SelfTest] CHECK ok: {}", what);
		} else {
			fail("check failed: " + what);
		}
	}

	void info(String text, Object... args) {
		LOGGER.info("[SelfTest] " + text, args);
	}

	// ---------------------------------------------------------------- step helpers

	void run(String name, Consumer<MinecraftClient> action) {
		steps.add(new Step(name, c -> {
			action.accept(c);
			return true;
		}, 0, true));
	}

	/** Runs on the integrated server thread; the script waits until it finished. */
	void onServer(String name, Consumer<MinecraftServer> action) {
		CompletableFuture<?>[] future = new CompletableFuture<?>[1];
		steps.add(new Step("server: " + name, c -> {
			if (future[0] == null) {
				MinecraftServer server = c.getServer();
				future[0] = server.submit(() -> {
					try {
						action.accept(server);
					} catch (Throwable t) {
						LOGGER.error("[SelfTest] exception on the server in '{}'", name, t);
						fail("server step '" + name + "' threw " + t);
					}
				});
			}
			return future[0].isDone();
		}, 200, true));
	}

	void waitTicks(String name, int ticks) {
		int[] remaining = {ticks};
		steps.add(new Step("wait " + ticks + " ticks (" + name + ")", c -> --remaining[0] <= 0, ticks + 5, true));
	}

	void waitUntil(String name, Predicate<MinecraftClient> condition, int timeoutTicks) {
		steps.add(new Step("wait for " + name, condition, timeoutTicks, true));
	}

	/** Like waitUntil, but a timeout only fails this check and the script goes on. */
	void waitUntilCheck(String name, Predicate<MinecraftClient> condition, int timeoutTicks) {
		steps.add(new Step("wait for " + name, c -> {
			boolean ok = condition.test(c);
			if (ok) {
				checks++;
			}
			return ok;
		}, timeoutTicks, false));
	}

	void screenshot(String stage) {
		run("clear chat before screenshot " + stage, c -> c.inGameHud.getChatHud().clear(false));
		waitTicks("frame without chat", 2);
		screenshotKeepChat(stage);
	}

	void screenshotKeepChat(String stage) {
		run("screenshot " + stage, c -> {
			String fileName = String.format("%s%02d_%s.png", SCREENSHOT_PREFIX, ++screenshotIndex, stage);
			ScreenshotRecorder.saveScreenshot(c.runDirectory, fileName, c.getFramebuffer(), 1,
					message -> LOGGER.info("[SelfTest] screenshot saved: {}", fileName));
		});
	}

	/** Commands go out one every few ticks: a burst gets a player kicked for spamming on servers that check it. */
	private static final Deque<String> COMMANDS = new ArrayDeque<>();
	private static int commandCooldown;

	static void command(MinecraftClient client, String command) {
		COMMANDS.add(command);
	}

	/** Sends the next queued command; true while commands are still waiting. */
	private static boolean flushCommands(MinecraftClient client) {
		if (COMMANDS.isEmpty()) {
			return false;
		}
		if (client.player == null || client.getNetworkHandler() == null) {
			COMMANDS.clear();
			return false;
		}
		if (--commandCooldown <= 0) {
			String command = COMMANDS.poll();
			LOGGER.info("[SelfTest] command: /{}", command);
			client.player.networkHandler.sendChatCommand(command);
			commandCooldown = 3;
		}
		return true;
	}

	static ServerWorld overworld(MinecraftClient client) {
		return client.getServer().getOverworld();
	}

	static ServerPlayerEntity serverPlayer(MinecraftServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	private record Step(String name, Predicate<MinecraftClient> body, int timeoutTicks, boolean fatal) {
	}
}
