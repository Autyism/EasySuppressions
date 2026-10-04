package io.github.autyism.easysuppressions.light;

import net.minecraft.network.PacketByteBuf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * The recorded ("locked") light of one chunk column, for the sections a zone covers. Immutable.
 *
 * <p>Each section is stored as a byte array: one byte = every block in the section has that value
 * (most sections, e.g. open sky or solid ground), 2048 bytes = one nibble per block in the vanilla
 * {@code ChunkNibbleArray} layout (index {@code y << 8 | z << 4 | x}, low nibble first). This is
 * lossless: {@link #get} returns exactly what was recorded.
 */
public final class ColumnData {
	public static final int NIBBLE_BYTES = 2048;

	private final int minSectionY;
	private final byte[][] sky;
	private final byte[][] block;

	public ColumnData(int minSectionY, byte[][] sky, byte[][] block) {
		this.minSectionY = minSectionY;
		this.sky = sky;
		this.block = block;
	}

	public int minSectionY() {
		return minSectionY;
	}

	public int sectionCount() {
		return sky != null ? sky.length : block != null ? block.length : 0;
	}

	public boolean has(LightChannel channel) {
		return (channel == LightChannel.SKY ? sky : block) != null;
	}

	/** Recorded value, or -1 when this channel or height was not recorded. */
	public int get(LightChannel channel, int x, int y, int z) {
		byte[][] sections = channel == LightChannel.SKY ? sky : block;
		if (sections == null) {
			return -1;
		}
		int index = (y >> 4) - minSectionY;
		if (index < 0 || index >= sections.length) {
			return -1;
		}
		return read(sections[index], x & 15, y & 15, z & 15);
	}

	public byte[] section(LightChannel channel, int sectionY) {
		byte[][] sections = channel == LightChannel.SKY ? sky : block;
		if (sections == null) {
			return null;
		}
		int index = sectionY - minSectionY;
		return index < 0 || index >= sections.length ? null : sections[index];
	}

	public static int read(byte[] section, int lx, int ly, int lz) {
		if (section.length == 1) {
			return section[0];
		}
		int i = ly << 8 | lz << 4 | lx;
		return section[i >> 1] >> ((i & 1) << 2) & 15;
	}

	/** Packs 4096 values (index {@code y << 8 | z << 4 | x}) into the compact form. */
	public static byte[] pack(byte[] values) {
		byte first = values[0];
		boolean uniform = true;
		for (int i = 1; i < 4096; i++) {
			if (values[i] != first) {
				uniform = false;
				break;
			}
		}
		if (uniform) {
			return new byte[]{first};
		}
		byte[] out = new byte[NIBBLE_BYTES];
		for (int i = 0; i < 4096; i++) {
			out[i >> 1] |= (byte) ((values[i] & 15) << ((i & 1) << 2));
		}
		return out;
	}

	/** Approximate memory/disk size, for the zones list and logs. */
	public long byteSize() {
		long n = 16;
		for (byte[][] sections : new byte[][][]{sky, block}) {
			if (sections != null) {
				for (byte[] s : sections) {
					n += s.length;
				}
			}
		}
		return n;
	}

	// --- serialization (files use DataOutput, the network uses PacketByteBuf) ---

	public void write(DataOutput out) throws IOException {
		out.writeInt(minSectionY);
		writeChannel(out, sky);
		writeChannel(out, block);
	}

	private static void writeChannel(DataOutput out, byte[][] sections) throws IOException {
		if (sections == null) {
			out.writeShort(-1);
			return;
		}
		out.writeShort(sections.length);
		for (byte[] s : sections) {
			out.writeShort(s.length);
			out.write(s);
		}
	}

	public static ColumnData read(DataInput in) throws IOException {
		int minSectionY = in.readInt();
		byte[][] sky = readChannel(in);
		byte[][] block = readChannel(in);
		return new ColumnData(minSectionY, sky, block);
	}

	private static byte[][] readChannel(DataInput in) throws IOException {
		int count = in.readShort();
		if (count < 0) {
			return null;
		}
		byte[][] sections = new byte[count][];
		for (int i = 0; i < count; i++) {
			int len = in.readShort();
			if (len != 1 && len != NIBBLE_BYTES) {
				throw new IOException("bad section length " + len);
			}
			byte[] s = new byte[len];
			in.readFully(s);
			sections[i] = s;
		}
		return sections;
	}

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(minSectionY);
		writeChannel(buf, sky);
		writeChannel(buf, block);
	}

	private static void writeChannel(PacketByteBuf buf, byte[][] sections) {
		if (sections == null) {
			buf.writeVarInt(-1);
			return;
		}
		buf.writeVarInt(sections.length);
		for (byte[] s : sections) {
			buf.writeByteArray(s);
		}
	}

	public static ColumnData read(PacketByteBuf buf) {
		int minSectionY = buf.readVarInt();
		byte[][] sky = readChannel(buf);
		byte[][] block = readChannel(buf);
		return new ColumnData(minSectionY, sky, block);
	}

	private static byte[][] readChannel(PacketByteBuf buf) {
		int count = buf.readVarInt();
		if (count < 0) {
			return null;
		}
		if (count > 4096) {
			throw new IllegalArgumentException("too many sections");
		}
		byte[][] sections = new byte[count][];
		for (int i = 0; i < count; i++) {
			byte[] s = buf.readByteArray(NIBBLE_BYTES);
			if (s.length != 1 && s.length != NIBBLE_BYTES) {
				throw new IllegalArgumentException("bad section length " + s.length);
			}
			sections[i] = s;
		}
		return sections;
	}
}
