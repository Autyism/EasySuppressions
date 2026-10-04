package io.github.autyism.easysuppressions.light;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

/** What a light suppressor does to one channel. */
public enum ChannelMode implements StringIdentifiable {
	/** Vanilla light. */
	NONE("none"),
	/** The real value of every block at the moment of locking, frozen. */
	LOCK("lock"),
	/** One fixed value for the whole zone. */
	FIXED("fixed");

	public static final Codec<ChannelMode> CODEC = StringIdentifiable.createCodec(ChannelMode::values);

	private final String id;

	ChannelMode(String id) {
		this.id = id;
	}

	@Override
	public String asString() {
		return id;
	}

	public String translationKey() {
		return "easysuppressions.mode." + id;
	}
}
