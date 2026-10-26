package io.github.autyism.easysuppressions.net;

import io.github.autyism.easysuppressions.EasySuppressions;
import io.github.autyism.easysuppressions.ModContent;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.block.LightSuppressorBlockEntity;
import io.github.autyism.easysuppressions.block.PortalCutterBlockEntity;
import io.github.autyism.easysuppressions.block.SuppressionBlockEntity;
import io.github.autyism.easysuppressions.config.EsConfig;
import io.github.autyism.easysuppressions.item.Selection;
import io.github.autyism.easysuppressions.item.SelectorItem;
import io.github.autyism.easysuppressions.item.Tooltips;
import io.github.autyism.easysuppressions.light.ColumnData;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.light.LightZone;
import io.github.autyism.easysuppressions.vanilla.VanillaScreens;
import io.github.autyism.easysuppressions.world.EsWorldState;
import io.github.autyism.easysuppressions.world.ServerZones;
import io.github.autyism.easysuppressions.world.WorldZones;
import io.github.autyism.easysuppressions.zone.Zone;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server side of all play-phase packets. */
public final class ServerNet {
	private static final double SELECTOR_RANGE = 64.0;
	private static final int MAX_PAYLOAD_BYTES = 900_000;
	private static final int PENDING_NEAR_RADIUS = 48;
	private static final int PENDING_NEAR_LIMIT = 2048;
	private static final Map<UUID, ChunkPos> lastLightSendCenter = new HashMap<>();

	private ServerNet() {
	}

	public static void registerPayloads() {
		PayloadTypeRegistry.configurationS2C().register(Payloads.Hello.ID, Payloads.Hello.CODEC);
		PayloadTypeRegistry.configurationC2S().register(Payloads.HelloReply.ID, Payloads.HelloReply.CODEC);

		PayloadTypeRegistry.playS2C().register(Payloads.ConfigSync.ID, Payloads.ConfigSync.CODEC);
		PayloadTypeRegistry.playS2C().register(Payloads.CutterZones.ID, Payloads.CutterZones.CODEC);
		PayloadTypeRegistry.playS2C().register(Payloads.LightZones.ID, Payloads.LightZones.CODEC);
		PayloadTypeRegistry.playS2C().register(Payloads.LightColumns.ID, Payloads.LightColumns.CODEC);
		PayloadTypeRegistry.playS2C().register(Payloads.Screen.ID, Payloads.Screen.CODEC);
		PayloadTypeRegistry.playS2C().register(Payloads.ScreenMessage.ID, Payloads.ScreenMessage.CODEC);

		PayloadTypeRegistry.playC2S().register(Payloads.SelectorCorner.ID, Payloads.SelectorCorner.CODEC);
		PayloadTypeRegistry.playC2S().register(Payloads.RequestScreen.ID, Payloads.RequestScreen.CODEC);
		PayloadTypeRegistry.playC2S().register(Payloads.ScreenAction.ID, Payloads.ScreenAction.CODEC);
		PayloadTypeRegistry.playC2S().register(Payloads.RequestLightColumns.ID, Payloads.RequestLightColumns.CODEC);
		PayloadTypeRegistry.playC2S().register(Payloads.UpdateConfig.ID, Payloads.UpdateConfig.CODEC);
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SelectorCorner.ID, (p, ctx) -> onSelectorCorner(ctx.player(), p));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.RequestScreen.ID, (p, ctx) -> onRequestScreen(ctx.player(), p.pos()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.ScreenAction.ID, (p, ctx) -> onScreenAction(ctx.player(), p));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.RequestLightColumns.ID, (p, ctx) -> onRequestLightColumns(ctx.player(), p));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.UpdateConfig.ID, (p, ctx) -> onUpdateConfig(ctx.player(), p.values()));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendEverything(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lastLightSendCenter.remove(handler.player.getUuid()));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> sendWorld(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sendWorld(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(ServerNet::tick);
	}

	private static boolean canSend(ServerPlayerEntity player) {
		return ServerPlayNetworking.canSend(player, Payloads.CutterZones.ID);
	}

	// ------------------------------------------------------------------ sync

	private static void sendEverything(ServerPlayerEntity player) {
		if (!canSend(player)) {
			return;
		}
		ServerPlayNetworking.send(player, new Payloads.ConfigSync(EsConfig.local()));
		sendWorld(player);
	}

	private static void sendWorld(ServerPlayerEntity player) {
		if (!canSend(player)) {
			return;
		}
		WorldZones zones = ServerZones.of(player.getEntityWorld());
		ServerPlayNetworking.send(player, cutterPayload(zones));
		sendLightZones(player, zones);
	}

	public static void broadcastConfig(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (canSend(player)) {
				ServerPlayNetworking.send(player, new Payloads.ConfigSync(EsConfig.local()));
			}
		}
	}

	private static Payloads.CutterZones cutterPayload(WorldZones zones) {
		List<Payloads.CutterInfo> list = new ArrayList<>();
		for (EsWorldState.CutterEntry e : zones.cutters()) {
			list.add(new Payloads.CutterInfo(e.source(), e.zone()));
		}
		return new Payloads.CutterZones(zones.world().getRegistryKey(), list);
	}

	public static void broadcastCutters(WorldZones zones) {
		Payloads.CutterZones payload = cutterPayload(zones);
		for (ServerPlayerEntity player : zones.world().getPlayers()) {
			if (canSend(player)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void sendLightZones(ServerPlayerEntity player, WorldZones zones) {
		ChunkPos center = player.getChunkPos();
		lastLightSendCenter.put(player.getUuid(), center);
		List<Payloads.LightZoneInfo> list = new ArrayList<>();
		for (LightZone zone : zones.lightZones()) {
			WorldZones.Progress progress = WorldZones.progress(zone);
			LongList near = progress.pending() > 0
					? WorldZones.nearestPending(zone, center.x, center.z, PENDING_NEAR_LIMIT, PENDING_NEAR_RADIUS)
					: new LongArrayList();
			list.add(new Payloads.LightZoneInfo(zone.source, zone.lockId, zone.zone, zone.settings, progress.total(), progress.recorded(), near));
		}
		ServerPlayNetworking.send(player, new Payloads.LightZones(zones.world().getRegistryKey(), list));
	}

	public static void broadcastLightZones(WorldZones zones) {
		for (ServerPlayerEntity player : zones.world().getPlayers()) {
			if (canSend(player)) {
				sendLightZones(player, zones);
			}
		}
	}

	public static void broadcastLightZoneReset(WorldZones zones, BlockPos source) {
		broadcastLightZones(zones);
	}

	/** Newly recorded columns go to every player who has them loaded. */
	public static void sendLightColumns(WorldZones zones, LightZone zone, Long2ObjectMap<ColumnData> columns) {
		int radius = zones.world().getServer().getPlayerManager().getViewDistance() + 2;
		for (ServerPlayerEntity player : zones.world().getPlayers()) {
			if (!canSend(player)) {
				continue;
			}
			ChunkPos center = player.getChunkPos();
			List<Payloads.ColumnEntry> entries = new ArrayList<>();
			for (Long2ObjectMap.Entry<ColumnData> e : columns.long2ObjectEntrySet()) {
				long key = e.getLongKey();
				if (Math.abs(ChunkPos.getPackedX(key) - center.x) <= radius && Math.abs(ChunkPos.getPackedZ(key) - center.z) <= radius) {
					entries.add(new Payloads.ColumnEntry(key, Optional.of(e.getValue())));
				}
			}
			sendColumns(player, zones, zone, entries);
		}
	}

	private static void sendColumns(ServerPlayerEntity player, WorldZones zones, LightZone zone, List<Payloads.ColumnEntry> entries) {
		List<Payloads.ColumnEntry> batch = new ArrayList<>();
		long bytes = 0;
		for (Payloads.ColumnEntry entry : entries) {
			long size = entry.data().map(ColumnData::byteSize).orElse(16L) + 16;
			if (!batch.isEmpty() && bytes + size > MAX_PAYLOAD_BYTES) {
				ServerPlayNetworking.send(player, new Payloads.LightColumns(zones.world().getRegistryKey(), zone.source, zone.lockId, batch));
				batch = new ArrayList<>();
				bytes = 0;
			}
			batch.add(entry);
			bytes += size;
		}
		if (!batch.isEmpty()) {
			ServerPlayNetworking.send(player, new Payloads.LightColumns(zones.world().getRegistryKey(), zone.source, zone.lockId, batch));
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % 40 != 0) {
			return;
		}
		// Keep the "still to record" highlight around a moving player up to date.
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!canSend(player)) {
				continue;
			}
			WorldZones zones = ServerZones.of(player.getEntityWorld());
			boolean anyPending = false;
			for (LightZone zone : zones.lightZones()) {
				if (WorldZones.progress(zone).pending() > 0) {
					anyPending = true;
					break;
				}
			}
			if (!anyPending) {
				continue;
			}
			ChunkPos last = lastLightSendCenter.get(player.getUuid());
			ChunkPos now = player.getChunkPos();
			if (last == null || Math.max(Math.abs(last.x - now.x), Math.abs(last.z - now.z)) >= 4) {
				sendLightZones(player, zones);
			}
		}
	}

	private static void onRequestLightColumns(ServerPlayerEntity player, Payloads.RequestLightColumns request) {
		WorldZones zones = ServerZones.of(player.getEntityWorld());
		LightZone zone = zones.lightZone(request.source());
		if (zone == null || zone.lockId != request.lockId()) {
			return;
		}
		int radius = zones.world().getServer().getPlayerManager().getViewDistance() + 3;
		ChunkPos center = player.getChunkPos();
		List<Payloads.ColumnEntry> entries = new ArrayList<>();
		for (int i = 0; i < request.keys().size(); i++) {
			long key = request.keys().getLong(i);
			int cx = ChunkPos.getPackedX(key);
			int cz = ChunkPos.getPackedZ(key);
			if (Math.abs(cx - center.x) > radius || Math.abs(cz - center.z) > radius || !zone.zone.intersectsColumn(cx, cz)) {
				continue;
			}
			entries.add(new Payloads.ColumnEntry(key, Optional.ofNullable(zone.column(cx, cz))));
		}
		sendColumns(player, zones, zone, entries);
	}

	// ------------------------------------------------------------------ selector

	private static void onSelectorCorner(ServerPlayerEntity player, Payloads.SelectorCorner payload) {
		setCorner(player, payload.index(), payload.pos());
	}

	/** Sets a corner of the selector in the main hand (from the mod's packet, or the server's own reading of a vanilla client's click). */
	public static void setCorner(ServerPlayerEntity player, int index, BlockPos pos) {
		Payloads.SelectorCorner payload = new Payloads.SelectorCorner(index, pos);
		ItemStack stack = player.getMainHandStack();
		if (!(stack.getItem() instanceof SelectorItem) || (payload.index() != 1 && payload.index() != 2)) {
			return;
		}
		ServerWorld world = player.getEntityWorld();
		double max = SELECTOR_RANGE + 4.0;
		if (player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > max * max || !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
			return;
		}
		Selection selection = stack.get(ModContent.SELECTION);
		boolean cleared = false;
		if (selection == null || !selection.dimension().equals(world.getRegistryKey())) {
			cleared = selection != null && (selection.pos1().isPresent() || selection.pos2().isPresent());
			selection = new Selection(world.getRegistryKey(), Optional.empty(), Optional.empty());
		}
		selection = selection.with(payload.index(), pos);
		stack.set(ModContent.SELECTION, selection);
		Text message = Text.translatable("easysuppressions.message.corner_set", payload.index(), Tooltips.pos(pos));
		if (cleared) {
			message = Text.translatable("easysuppressions.message.corner_dimension_reset").append(" ").append(message);
		}
		if (selection.isComplete()) {
			Zone zone = selection.zone().orElseThrow();
			message = message.copy().append(Text.translatable("easysuppressions.message.selection_size", zone.sizeX(), zone.sizeY(), zone.sizeZ()));
		}
		player.sendMessage(message.copy().formatted(cleared ? Formatting.YELLOW : Formatting.AQUA), true);
	}

	// ------------------------------------------------------------------ block GUI

	public static Payloads.ScreenData screenData(SuppressionBlockEntity be) {
		ServerWorld world = (ServerWorld) be.getWorld();
		LightSettings settings = LightSettings.DEFAULT;
		long total = 0;
		long recorded = 0;
		LongList nearest = new LongArrayList();
		if (be instanceof LightSuppressorBlockEntity light) {
			settings = light.getSettings();
			LightZone zone = ServerZones.of(world).lightZone(be.getPos());
			if (zone != null) {
				WorldZones.Progress progress = WorldZones.progress(zone);
				total = progress.total();
				recorded = progress.recorded();
				if (progress.pending() > 0) {
					nearest = WorldZones.nearestPending(zone, be.getPos().getX() >> 4, be.getPos().getZ() >> 4, 6, 64);
				}
			}
		}
		return new Payloads.ScreenData(be.getPos(), be.kind(), Optional.ofNullable(be.getZone()), be.isActive(), settings,
				world.isReceivingRedstonePower(be.getPos()), total, recorded, nearest);
	}

	public static void openScreen(ServerPlayerEntity player, SuppressionBlockEntity be) {
		if (canSend(player)) {
			ServerPlayNetworking.send(player, new Payloads.Screen(screenData(be), true));
		} else {
			VanillaScreens.open(player, be);
		}
	}

	/** Shows the block's current state again after an action: the mod's screen, or the dialog. */
	private static void refresh(ServerPlayerEntity player, SuppressionBlockEntity be) {
		if (canSend(player)) {
			ServerPlayNetworking.send(player, new Payloads.Screen(screenData(be), false));
		} else {
			VanillaScreens.open(player, be);
		}
	}

	public static SuppressionBlockEntity reachable(ServerPlayerEntity player, BlockPos pos) {
		if (!player.canInteractWithBlockAt(pos, 4.0) || !player.getEntityWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
			return null;
		}
		return player.getEntityWorld().getBlockEntity(pos) instanceof SuppressionBlockEntity be ? be : null;
	}

	private static void onRequestScreen(ServerPlayerEntity player, BlockPos pos) {
		SuppressionBlockEntity be = reachable(player, pos);
		if (be != null) {
			ServerPlayNetworking.send(player, new Payloads.Screen(screenData(be), false));
		}
	}

	private static void message(ServerPlayerEntity player, BlockPos pos, Text text, boolean error) {
		if (canSend(player)) {
			ServerPlayNetworking.send(player, new Payloads.ScreenMessage(pos, text, error));
		} else {
			VanillaScreens.note(player, pos, text, error);
		}
	}

	private static void onScreenAction(ServerPlayerEntity player, Payloads.ScreenAction action) {
		handleAction(player, action);
	}

	/** One action of the block GUI, from the mod's screen or from the vanilla dialog. */
	public static void handleAction(ServerPlayerEntity player, Payloads.ScreenAction action) {
		BlockPos pos = action.pos();
		SuppressionBlockEntity be = reachable(player, pos);
		if (be == null) {
			return;
		}
		ServerWorld world = player.getEntityWorld();
		if (be instanceof LightSuppressorBlockEntity && !world.isReceivingRedstonePower(pos)) {
			message(player, pos, Text.translatable("easysuppressions.message.needs_redstone"), true);
			refresh(player, be);
			return;
		}
		switch (action.action()) {
			case SAVE_ZONE -> action.zone().ifPresent(zone -> saveZone(player, be, zone));
			case IMPORT_SELECTOR -> importSelector(player, be);
			case SAVE_SETTINGS -> {
				if (be instanceof LightSuppressorBlockEntity light) {
					if (light.isLocked()) {
						message(player, pos, Text.translatable("easysuppressions.message.locked_readonly"), true);
					} else {
						light.setSettings(action.settings());
						message(player, pos, Text.translatable("easysuppressions.message.settings_saved"), false);
					}
				}
			}
			case LOCK -> {
				if (be instanceof LightSuppressorBlockEntity light && !light.isLocked()) {
					lock(player, light, action.zone().orElse(null), action.settings());
				}
			}
			case UNLOCK -> {
				if (be instanceof LightSuppressorBlockEntity light && light.isLocked()) {
					light.unlock(world);
					message(player, pos, Text.translatable("easysuppressions.message.unlocked"), false);
				}
			}
		}
		refresh(player, be);
	}

	private static boolean checkZone(ServerPlayerEntity player, SuppressionBlockEntity be, Zone zone) {
		if (!zone.contains(be.getPos())) {
			message(player, be.getPos(), Text.translatable("easysuppressions.message.zone_must_contain_block"), true);
			return false;
		}
		return true;
	}

	private static void saveZone(ServerPlayerEntity player, SuppressionBlockEntity be, Zone zone) {
		if (!checkZone(player, be, zone)) {
			return;
		}
		if (be instanceof PortalCutterBlockEntity cutter) {
			cutter.setZone(player.getEntityWorld(), zone);
		} else if (be instanceof LightSuppressorBlockEntity light) {
			if (light.isLocked()) {
				message(player, be.getPos(), Text.translatable("easysuppressions.message.locked_readonly"), true);
				return;
			}
			light.setZone(zone);
		}
		message(player, be.getPos(), Text.translatable("easysuppressions.message.zone_saved", zone.describe()), false);
	}

	private static void importSelector(ServerPlayerEntity player, SuppressionBlockEntity be) {
		SuppressorKind kind = be.kind();
		Selection found = null;
		boolean wrongKind = false;
		for (Hand hand : Hand.values()) {
			ItemStack stack = player.getStackInHand(hand);
			if (stack.getItem() instanceof SelectorItem selector) {
				if (selector.kind() != kind) {
					wrongKind = true;
					continue;
				}
				Selection selection = stack.get(ModContent.SELECTION);
				if (selection != null && selection.isComplete()) {
					found = selection;
					break;
				}
			}
		}
		if (found == null) {
			message(player, be.getPos(), Text.translatable(wrongKind ? "easysuppressions.message.import_wrong_kind"
					: "easysuppressions.message.import_no_selector"), true);
			return;
		}
		if (!found.dimension().equals(player.getEntityWorld().getRegistryKey())) {
			message(player, be.getPos(), Text.translatable("easysuppressions.message.import_wrong_dimension",
					Tooltips.dimensionName(found.dimension())), true);
			return;
		}
		saveZone(player, be, found.zone().orElseThrow());
	}

	private static void lock(ServerPlayerEntity player, LightSuppressorBlockEntity light, Zone zone, LightSettings settings) {
		BlockPos pos = light.getPos();
		if (zone == null) {
			message(player, pos, Text.translatable("easysuppressions.message.no_zone"), true);
			return;
		}
		if (!checkZone(player, light, zone)) {
			return;
		}
		if (settings.doesNothing()) {
			message(player, pos, Text.translatable("easysuppressions.message.nothing_to_lock"), true);
			return;
		}
		light.setZone(zone);
		light.setSettings(settings);
		WorldZones.LockResult result = light.lock(player.getEntityWorld(), player.getUuid());
		if (result instanceof WorldZones.LockResult.Overlap overlap) {
			message(player, pos, Text.translatable("easysuppressions.message.overlap", Tooltips.pos(overlap.other())), true);
		} else {
			message(player, pos, Text.translatable("easysuppressions.message.locked"), false);
		}
	}

	// ------------------------------------------------------------------ settings

	public static boolean mayEditConfig(ServerPlayerEntity player) {
		MinecraftServer server = player.getEntityWorld().getServer();
		return server.isHost(player.getPlayerConfigEntry()) || player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS);
	}

	private static void onUpdateConfig(ServerPlayerEntity player, EsConfig.Values values) {
		if (!mayEditConfig(player)) {
			player.sendMessage(Text.translatable("easysuppressions.message.config_no_permission").formatted(Formatting.RED), false);
			return;
		}
		EsConfig.set(values);
		EasySuppressions.LOGGER.info("{} changed the settings: break {} s, portal pickaxe {} s", player.getName().getString(),
				values.blockBreakSeconds(), values.pickaxeSeconds());
		broadcastConfig(player.getEntityWorld().getServer());
	}
}
