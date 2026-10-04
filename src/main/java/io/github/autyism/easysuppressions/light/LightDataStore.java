package io.github.autyism.easysuppressions.light;

import io.github.autyism.easysuppressions.EasySuppressions;
import io.github.autyism.easysuppressions.zone.Zone;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Recorded light of locked zones, one file per zone in {@code <dimension>/data/easysuppressions/}.
 * The file is append-only: a header (zone + settings, to detect a stale file) followed by blocks
 * of deflate-compressed columns, one block per save. Recording a few new columns therefore never
 * rewrites the whole file. A block cut off by a crash is ignored when reading.
 */
public final class LightDataStore {
	private static final int MAGIC = 0x45534C44; // "ESLD"
	private static final int VERSION = 1;
	private static final int BLOCK_MAGIC = 0x434F4C53; // "COLS"

	private final Path dir;

	public LightDataStore(Path dir) {
		this.dir = dir;
	}

	private Path file(BlockPos source) {
		return dir.resolve("light_" + source.getX() + "_" + source.getY() + "_" + source.getZ() + ".dat");
	}

	/** Starts a new file for a freshly locked zone (replacing any old one). */
	public void create(BlockPos source, Zone zone, LightSettings settings) {
		try {
			Files.createDirectories(dir);
			Path tmp = file(source).resolveSibling(file(source).getFileName() + ".tmp");
			try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmp))) {
				writeHeader(out, zone, settings);
			}
			Files.move(tmp, file(source), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			EasySuppressions.LOGGER.error("Could not create light data file for {}", source, e);
		}
	}

	public void append(BlockPos source, Long2ObjectMap<ColumnData> columns) {
		if (columns.isEmpty()) {
			return;
		}
		try {
			ByteArrayOutputStream rawBytes = new ByteArrayOutputStream();
			try (DataOutputStream raw = new DataOutputStream(rawBytes)) {
				raw.writeInt(columns.size());
				for (Long2ObjectMap.Entry<ColumnData> e : columns.long2ObjectEntrySet()) {
					raw.writeLong(e.getLongKey());
					e.getValue().write(raw);
				}
			}
			byte[] input = rawBytes.toByteArray();
			Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
			deflater.setInput(input);
			deflater.finish();
			ByteArrayOutputStream compressed = new ByteArrayOutputStream(Math.max(64, input.length / 4));
			byte[] buf = new byte[65536];
			while (!deflater.finished()) {
				int n = deflater.deflate(buf);
				compressed.write(buf, 0, n);
			}
			deflater.end();
			byte[] packed = compressed.toByteArray();
			try (OutputStream os = Files.newOutputStream(file(source), StandardOpenOption.APPEND, StandardOpenOption.WRITE);
				 DataOutputStream out = new DataOutputStream(os)) {
				out.writeInt(BLOCK_MAGIC);
				out.writeInt(packed.length);
				out.writeInt(input.length);
				out.write(packed);
			}
		} catch (IOException e) {
			EasySuppressions.LOGGER.error("Could not save recorded light of {}", source, e);
		}
	}

	/**
	 * Reads every recorded column of a zone. Returns null when the file is missing or belongs to a
	 * different zone/settings (it was then not usable).
	 */
	public Long2ObjectOpenHashMap<ColumnData> read(BlockPos source, Zone zone, LightSettings settings) {
		Path path = file(source);
		if (!Files.exists(path)) {
			return null;
		}
		Long2ObjectOpenHashMap<ColumnData> result = new Long2ObjectOpenHashMap<>();
		try (InputStream is = Files.newInputStream(path); DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(is))) {
			if (in.readInt() != MAGIC || in.readInt() != VERSION) {
				return null;
			}
			Zone storedZone = new Zone(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
			LightSettings storedSettings = new LightSettings(
					new ChannelSetting(ChannelMode.values()[in.readByte()], in.readByte()),
					new ChannelSetting(ChannelMode.values()[in.readByte()], in.readByte()));
			if (!storedZone.equals(zone) || !storedSettings.equals(settings)) {
				EasySuppressions.LOGGER.warn("Light data file of {} belongs to another zone; ignoring it", source);
				return null;
			}
			while (true) {
				int magic;
				try {
					magic = in.readInt();
				} catch (EOFException end) {
					break;
				}
				if (magic != BLOCK_MAGIC) {
					EasySuppressions.LOGGER.warn("Light data file of {} has a damaged block; using what came before it", source);
					break;
				}
				int packedLength = in.readInt();
				int rawLength = in.readInt();
				byte[] packed = new byte[packedLength];
				try {
					in.readFully(packed);
				} catch (EOFException cut) {
					EasySuppressions.LOGGER.warn("Light data file of {} ends in the middle of a block (crash?); using what came before it", source);
					break;
				}
				Inflater inflater = new Inflater();
				inflater.setInput(packed);
				byte[] raw = new byte[rawLength];
				int done = 0;
				while (done < rawLength && !inflater.finished()) {
					done += inflater.inflate(raw, done, rawLength - done);
				}
				inflater.end();
				DataInputStream block = new DataInputStream(new ByteArrayInputStream(raw));
				int count = block.readInt();
				for (int i = 0; i < count; i++) {
					long key = block.readLong();
					result.put(key, ColumnData.read(block));
				}
			}
		} catch (IOException | DataFormatException | RuntimeException e) {
			EasySuppressions.LOGGER.error("Could not read recorded light of {}", source, e);
		}
		return result;
	}

	public void delete(BlockPos source) {
		try {
			Files.deleteIfExists(file(source));
		} catch (IOException e) {
			EasySuppressions.LOGGER.warn("Could not delete light data file of {}", source, e);
		}
	}

	private static void writeHeader(DataOutputStream out, Zone zone, LightSettings settings) throws IOException {
		out.writeInt(MAGIC);
		out.writeInt(VERSION);
		out.writeInt(zone.minX());
		out.writeInt(zone.minY());
		out.writeInt(zone.minZ());
		out.writeInt(zone.maxX());
		out.writeInt(zone.maxY());
		out.writeInt(zone.maxZ());
		out.writeByte(settings.sky().mode().ordinal());
		out.writeByte(settings.sky().value());
		out.writeByte(settings.block().mode().ordinal());
		out.writeByte(settings.block().value());
	}
}
