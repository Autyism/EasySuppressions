package io.github.autyism.easysuppressions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.autyism.easysuppressions.config.EsConfig;
import io.github.autyism.easysuppressions.item.Tooltips;
import io.github.autyism.easysuppressions.light.ChannelMode;
import io.github.autyism.easysuppressions.light.ChannelSetting;
import io.github.autyism.easysuppressions.light.LightZone;
import io.github.autyism.easysuppressions.net.ServerNet;
import io.github.autyism.easysuppressions.world.EsWorldState;
import io.github.autyism.easysuppressions.world.ServerZones;
import io.github.autyism.easysuppressions.world.WorldZones;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /easysuppressions zones list            every registered zone (for troubleshooting)
 * /easysuppressions config ...            show / change the server settings
 * Both need operator rights.
 */
public final class EsCommand {
	private EsCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("easysuppressions")
				.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(CommandManager.literal("zones")
						.then(CommandManager.literal("list").executes(EsCommand::listZones)))
				.then(CommandManager.literal("config")
						.executes(EsCommand::showConfig)
						.then(CommandManager.literal("blockBreakSeconds")
								.then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(0, EsConfig.MAX_SECONDS))
										.executes(ctx -> setConfig(ctx, DoubleArgumentType.getDouble(ctx, "seconds"), EsConfig.local().pickaxeSeconds()))))
						.then(CommandManager.literal("portalPickaxeSeconds")
								.then(CommandManager.argument("seconds", DoubleArgumentType.doubleArg(0, EsConfig.MAX_SECONDS))
										.executes(ctx -> setConfig(ctx, EsConfig.local().blockBreakSeconds(), DoubleArgumentType.getDouble(ctx, "seconds")))))));
	}

	private static int listZones(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		int count = 0;
		for (ServerWorld world : source.getServer().getWorlds()) {
			WorldZones zones = ServerZones.of(world);
			zones.validate();
			Text dim = Tooltips.dimensionName(world.getRegistryKey());
			for (EsWorldState.CutterEntry e : zones.cutters()) {
				count++;
				MutableText line = Text.translatable("easysuppressions.command.zone_cutter", dim, e.zone().describe(), Tooltips.pos(e.source()));
				source.sendFeedback(() -> line.formatted(Formatting.LIGHT_PURPLE), false);
			}
			for (EsWorldState.SuppressorEntry e : zones.lightEntries()) {
				count++;
				LightZone zone = zones.lightZone(e.source());
				WorldZones.Progress progress = zone != null ? WorldZones.progress(zone) : new WorldZones.Progress(0, 0);
				MutableText line = Text.translatable("easysuppressions.command.zone_light", dim, e.zone().describe(), Tooltips.pos(e.source()),
						describe(e.settings().sky()), describe(e.settings().block()), progress.recorded(), progress.total());
				source.sendFeedback(() -> line.formatted(Formatting.GOLD), false);
			}
		}
		int total = count;
		source.sendFeedback(() -> Text.translatable("easysuppressions.command.zone_count", total), false);
		return count;
	}

	private static Text describe(ChannelSetting setting) {
		MutableText text = Text.translatable(setting.mode().translationKey());
		if (setting.mode() == ChannelMode.FIXED) {
			text.append(" " + setting.value());
		}
		return text;
	}

	private static int showConfig(CommandContext<ServerCommandSource> ctx) {
		EsConfig.Values v = EsConfig.local();
		ctx.getSource().sendFeedback(() -> Text.translatable("easysuppressions.command.config", v.blockBreakSeconds(), v.pickaxeSeconds()), false);
		return 1;
	}

	private static int setConfig(CommandContext<ServerCommandSource> ctx, double breakSeconds, double pickaxeSeconds) {
		EsConfig.set(new EsConfig.Values(breakSeconds, pickaxeSeconds));
		ServerNet.broadcastConfig(ctx.getSource().getServer());
		return showConfig(ctx);
	}
}
