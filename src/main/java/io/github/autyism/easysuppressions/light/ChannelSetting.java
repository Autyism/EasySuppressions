package io.github.autyism.easysuppressions.light;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/** Mode of one channel plus the value used by {@link ChannelMode#FIXED} (0..15). */
public record ChannelSetting(ChannelMode mode, int value) {
	public static final ChannelSetting NONE = new ChannelSetting(ChannelMode.NONE, 0);
	public static final ChannelSetting LOCK = new ChannelSetting(ChannelMode.LOCK, 0);

	public static final Codec<ChannelSetting> CODEC = RecordCodecBuilder.create(i -> i.group(
			ChannelMode.CODEC.fieldOf("mode").forGetter(ChannelSetting::mode),
			Codec.intRange(0, 15).optionalFieldOf("value", 0).forGetter(ChannelSetting::value)
	).apply(i, ChannelSetting::new));

	public static final PacketCodec<ByteBuf, ChannelSetting> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.indexed(i -> ChannelMode.values()[Math.floorMod(i, ChannelMode.values().length)], ChannelMode::ordinal), ChannelSetting::mode,
			PacketCodecs.VAR_INT, ChannelSetting::value,
			ChannelSetting::new);

	public ChannelSetting {
		value = Math.max(0, Math.min(15, value));
	}

	public static ChannelSetting fixed(int value) {
		return new ChannelSetting(ChannelMode.FIXED, value);
	}
}
