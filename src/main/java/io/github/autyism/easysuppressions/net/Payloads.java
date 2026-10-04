package io.github.autyism.easysuppressions.net;

import io.github.autyism.easysuppressions.EasySuppressions;
import io.github.autyism.easysuppressions.SuppressorKind;
import io.github.autyism.easysuppressions.config.EsConfig;
import io.github.autyism.easysuppressions.light.ColumnData;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.zone.Zone;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.text.TextCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Every custom packet of the mod. */
public final class Payloads {
	private Payloads() {
	}

	private static <T extends CustomPayload> CustomPayload.Id<T> id(String path) {
		return new CustomPayload.Id<>(EasySuppressions.id(path));
	}

	private static final PacketCodec<io.netty.buffer.ByteBuf, RegistryKey<World>> WORLD_KEY = RegistryKey.createPacketCodec(RegistryKeys.WORLD);

	private static void writeLongs(PacketByteBuf buf, LongList list) {
		buf.writeVarInt(list.size());
		for (int i = 0; i < list.size(); i++) {
			buf.writeLong(list.getLong(i));
		}
	}

	private static LongList readLongs(PacketByteBuf buf, int max) {
		int n = buf.readVarInt();
		if (n < 0 || n > max) {
			throw new IllegalArgumentException("list too long: " + n);
		}
		LongList list = new LongArrayList(n);
		for (int i = 0; i < n; i++) {
			list.add(buf.readLong());
		}
		return list;
	}

	// ------------------------------------------------------------------ configuration phase

	/** Server to client at login: the server's mod version. The client answers with {@link HelloReply}. */
	public record Hello(String version) implements CustomPayload {
		public static final Id<Hello> ID = id("hello");
		public static final PacketCodec<PacketByteBuf, Hello> CODEC = CustomPayload.codecOf(
				(p, buf) -> buf.writeString(p.version, 64), buf -> new Hello(buf.readString(64)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record HelloReply(String version) implements CustomPayload {
		public static final Id<HelloReply> ID = id("hello_reply");
		public static final PacketCodec<PacketByteBuf, HelloReply> CODEC = CustomPayload.codecOf(
				(p, buf) -> buf.writeString(p.version, 64), buf -> new HelloReply(buf.readString(64)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------ server to client

	public record ConfigSync(EsConfig.Values values) implements CustomPayload {
		public static final Id<ConfigSync> ID = id("config");
		public static final PacketCodec<RegistryByteBuf, ConfigSync> CODEC = CustomPayload.codecOf(
				(p, buf) -> EsConfig.Values.PACKET_CODEC.encode(buf, p.values), buf -> new ConfigSync(EsConfig.Values.PACKET_CODEC.decode(buf)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record CutterInfo(BlockPos source, Zone zone) {
	}

	/** All active portal cutter zones of one dimension (replaces what the client had). */
	public record CutterZones(RegistryKey<World> dimension, List<CutterInfo> zones) implements CustomPayload {
		public static final Id<CutterZones> ID = id("cutter_zones");
		public static final PacketCodec<RegistryByteBuf, CutterZones> CODEC = CustomPayload.codecOf((p, buf) -> {
			WORLD_KEY.encode(buf, p.dimension);
			buf.writeVarInt(p.zones.size());
			for (CutterInfo info : p.zones) {
				buf.writeBlockPos(info.source);
				Zone.PACKET_CODEC.encode(buf, info.zone);
			}
		}, buf -> {
			RegistryKey<World> dim = WORLD_KEY.decode(buf);
			int n = buf.readVarInt();
			List<CutterInfo> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				list.add(new CutterInfo(buf.readBlockPos(), Zone.PACKET_CODEC.decode(buf)));
			}
			return new CutterZones(dim, list);
		});

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/**
	 * One locked light zone. {@code lockId} changes whenever the zone is locked again, so the client
	 * knows its recorded columns are stale. {@code pendingNear} lists columns still to be recorded
	 * near the receiving player (for the highlight).
	 */
	public record LightZoneInfo(BlockPos source, long lockId, Zone zone, LightSettings settings, long total, long recorded, LongList pendingNear) {
	}

	/** All locked light zones of one dimension (replaces what the client had). */
	public record LightZones(RegistryKey<World> dimension, List<LightZoneInfo> zones) implements CustomPayload {
		public static final Id<LightZones> ID = id("light_zones");
		public static final PacketCodec<RegistryByteBuf, LightZones> CODEC = CustomPayload.codecOf((p, buf) -> {
			WORLD_KEY.encode(buf, p.dimension);
			buf.writeVarInt(p.zones.size());
			for (LightZoneInfo info : p.zones) {
				buf.writeBlockPos(info.source);
				buf.writeLong(info.lockId);
				Zone.PACKET_CODEC.encode(buf, info.zone);
				LightSettings.PACKET_CODEC.encode(buf, info.settings);
				buf.writeVarLong(info.total);
				buf.writeVarLong(info.recorded);
				writeLongs(buf, info.pendingNear);
			}
		}, buf -> {
			RegistryKey<World> dim = WORLD_KEY.decode(buf);
			int n = buf.readVarInt();
			List<LightZoneInfo> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				list.add(new LightZoneInfo(buf.readBlockPos(), buf.readLong(), Zone.PACKET_CODEC.decode(buf),
						LightSettings.PACKET_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong(), readLongs(buf, 8192)));
			}
			return new LightZones(dim, list);
		});

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record ColumnEntry(long key, Optional<ColumnData> data) {
	}

	/** Recorded light of some columns of one zone; an empty entry means "not recorded yet". */
	public record LightColumns(RegistryKey<World> dimension, BlockPos source, long lockId, List<ColumnEntry> columns) implements CustomPayload {
		public static final Id<LightColumns> ID = id("light_columns");
		public static final PacketCodec<RegistryByteBuf, LightColumns> CODEC = CustomPayload.codecOf((p, buf) -> {
			WORLD_KEY.encode(buf, p.dimension);
			buf.writeBlockPos(p.source);
			buf.writeLong(p.lockId);
			buf.writeVarInt(p.columns.size());
			for (ColumnEntry e : p.columns) {
				buf.writeLong(e.key);
				buf.writeBoolean(e.data.isPresent());
				e.data.ifPresent(d -> d.write(buf));
			}
		}, buf -> {
			RegistryKey<World> dim = WORLD_KEY.decode(buf);
			BlockPos source = buf.readBlockPos();
			long lockId = buf.readLong();
			int n = buf.readVarInt();
			if (n < 0 || n > 4096) {
				throw new IllegalArgumentException("too many columns");
			}
			List<ColumnEntry> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				long key = buf.readLong();
				list.add(new ColumnEntry(key, buf.readBoolean() ? Optional.of(ColumnData.read(buf)) : Optional.empty()));
			}
			return new LightColumns(dim, source, lockId, list);
		});

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** What a block's GUI shows. */
	public record ScreenData(BlockPos pos, SuppressorKind kind, Optional<Zone> zone, boolean active, LightSettings settings,
							 boolean powered, long total, long recorded, LongList nearestPending) {
		static void write(RegistryByteBuf buf, ScreenData d) {
			buf.writeBlockPos(d.pos);
			buf.writeEnumConstant(d.kind);
			Zone.OPTIONAL_PACKET_CODEC.encode(buf, d.zone);
			buf.writeBoolean(d.active);
			LightSettings.PACKET_CODEC.encode(buf, d.settings);
			buf.writeBoolean(d.powered);
			buf.writeVarLong(d.total);
			buf.writeVarLong(d.recorded);
			writeLongs(buf, d.nearestPending);
		}

		static ScreenData read(RegistryByteBuf buf) {
			return new ScreenData(buf.readBlockPos(), buf.readEnumConstant(SuppressorKind.class), Zone.OPTIONAL_PACKET_CODEC.decode(buf),
					buf.readBoolean(), LightSettings.PACKET_CODEC.decode(buf), buf.readBoolean(), buf.readVarLong(), buf.readVarLong(),
					readLongs(buf, 64));
		}
	}

	/** Opens (open = true) or refreshes the GUI of a block. */
	public record Screen(ScreenData data, boolean open) implements CustomPayload {
		public static final Id<Screen> ID = id("screen");
		public static final PacketCodec<RegistryByteBuf, Screen> CODEC = CustomPayload.codecOf((p, buf) -> {
			ScreenData.write(buf, p.data);
			buf.writeBoolean(p.open);
		}, buf -> new Screen(ScreenData.read(buf), buf.readBoolean()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** A result line shown inside the open GUI (e.g. why locking failed). */
	public record ScreenMessage(BlockPos pos, Text message, boolean error) implements CustomPayload {
		public static final Id<ScreenMessage> ID = id("screen_message");
		public static final PacketCodec<RegistryByteBuf, ScreenMessage> CODEC = CustomPayload.codecOf((p, buf) -> {
			buf.writeBlockPos(p.pos);
			TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, p.message);
			buf.writeBoolean(p.error);
		}, buf -> new ScreenMessage(buf.readBlockPos(), TextCodecs.REGISTRY_PACKET_CODEC.decode(buf), buf.readBoolean()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	// ------------------------------------------------------------------ client to server

	/** Selector click: set corner 1 (left click) or 2 (right click) to the block the client hit. */
	public record SelectorCorner(int index, BlockPos pos) implements CustomPayload {
		public static final Id<SelectorCorner> ID = id("selector_corner");
		public static final PacketCodec<RegistryByteBuf, SelectorCorner> CODEC = CustomPayload.codecOf((p, buf) -> {
			buf.writeByte(p.index);
			buf.writeBlockPos(p.pos);
		}, buf -> new SelectorCorner(buf.readByte(), buf.readBlockPos()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record RequestScreen(BlockPos pos) implements CustomPayload {
		public static final Id<RequestScreen> ID = id("request_screen");
		public static final PacketCodec<RegistryByteBuf, RequestScreen> CODEC = CustomPayload.codecOf(
				(p, buf) -> buf.writeBlockPos(p.pos), buf -> new RequestScreen(buf.readBlockPos()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public enum Action {
		SAVE_ZONE,
		IMPORT_SELECTOR,
		SAVE_SETTINGS,
		LOCK,
		UNLOCK
	}

	/** A button in a block GUI. Zone and settings are what the GUI shows (used by SAVE_* and LOCK). */
	public record ScreenAction(BlockPos pos, Action action, Optional<Zone> zone, LightSettings settings) implements CustomPayload {
		public static final Id<ScreenAction> ID = id("screen_action");
		public static final PacketCodec<RegistryByteBuf, ScreenAction> CODEC = CustomPayload.codecOf((p, buf) -> {
			buf.writeBlockPos(p.pos);
			buf.writeEnumConstant(p.action);
			Zone.OPTIONAL_PACKET_CODEC.encode(buf, p.zone);
			LightSettings.PACKET_CODEC.encode(buf, p.settings);
		}, buf -> new ScreenAction(buf.readBlockPos(), buf.readEnumConstant(Action.class), Zone.OPTIONAL_PACKET_CODEC.decode(buf),
				LightSettings.PACKET_CODEC.decode(buf)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** The client loaded chunks inside a locked zone and wants their recorded light (for drawing). */
	public record RequestLightColumns(BlockPos source, long lockId, LongList keys) implements CustomPayload {
		public static final Id<RequestLightColumns> ID = id("request_light_columns");
		public static final PacketCodec<RegistryByteBuf, RequestLightColumns> CODEC = CustomPayload.codecOf((p, buf) -> {
			buf.writeBlockPos(p.source);
			buf.writeLong(p.lockId);
			writeLongs(buf, p.keys);
		}, buf -> new RequestLightColumns(buf.readBlockPos(), buf.readLong(), readLongs(buf, 4096)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Settings screen: new server values (op or singleplayer host only). */
	public record UpdateConfig(EsConfig.Values values) implements CustomPayload {
		public static final Id<UpdateConfig> ID = id("update_config");
		public static final PacketCodec<RegistryByteBuf, UpdateConfig> CODEC = CustomPayload.codecOf(
				(p, buf) -> EsConfig.Values.PACKET_CODEC.encode(buf, p.values), buf -> new UpdateConfig(EsConfig.Values.PACKET_CODEC.decode(buf)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
