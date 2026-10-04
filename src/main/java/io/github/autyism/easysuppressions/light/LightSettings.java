package io.github.autyism.easysuppressions.light;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;

/** Settings of both channels of a light suppressor. */
public record LightSettings(ChannelSetting sky, ChannelSetting block) {
	/** "Simple mode": lock both channels as they are. */
	public static final LightSettings SIMPLE = new LightSettings(ChannelSetting.LOCK, ChannelSetting.LOCK);
	/** Preset "mob farm platform": block light 0, sky light 0. */
	public static final LightSettings MOB_FARM = new LightSettings(ChannelSetting.fixed(0), ChannelSetting.fixed(0));
	/** Preset "no spawning": block light 15, sky light untouched. */
	public static final LightSettings NO_SPAWN = new LightSettings(ChannelSetting.NONE, ChannelSetting.fixed(15));
	/** Preset "sky light only": block light untouched, sky light 0. */
	public static final LightSettings SKY_ONLY = new LightSettings(ChannelSetting.fixed(0), ChannelSetting.NONE);

	public static final LightSettings DEFAULT = SIMPLE;

	public static final Codec<LightSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			ChannelSetting.CODEC.fieldOf("sky").forGetter(LightSettings::sky),
			ChannelSetting.CODEC.fieldOf("block").forGetter(LightSettings::block)
	).apply(i, LightSettings::new));

	public static final PacketCodec<ByteBuf, LightSettings> PACKET_CODEC = PacketCodec.tuple(
			ChannelSetting.PACKET_CODEC, LightSettings::sky,
			ChannelSetting.PACKET_CODEC, LightSettings::block,
			LightSettings::new);

	public ChannelSetting get(LightChannel channel) {
		return channel == LightChannel.SKY ? sky : block;
	}

	public boolean needsCapture() {
		return sky.mode() == ChannelMode.LOCK || block.mode() == ChannelMode.LOCK;
	}

	public boolean doesNothing() {
		return sky.mode() == ChannelMode.NONE && block.mode() == ChannelMode.NONE;
	}
}
