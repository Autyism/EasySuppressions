package io.github.autyism.easysuppressions.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.autyism.easysuppressions.zone.Zone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

/** A zone bound to a block item (anvil binding); the placed block takes it over. */
public record BoundZone(RegistryKey<World> dimension, Zone zone) {
	public static final Codec<BoundZone> CODEC = RecordCodecBuilder.create(i -> i.group(
			World.CODEC.fieldOf("dimension").forGetter(BoundZone::dimension),
			Zone.CODEC.fieldOf("zone").forGetter(BoundZone::zone)
	).apply(i, BoundZone::new));

	public static final PacketCodec<ByteBuf, BoundZone> PACKET_CODEC = PacketCodec.tuple(
			RegistryKey.createPacketCodec(RegistryKeys.WORLD), BoundZone::dimension,
			Zone.PACKET_CODEC, BoundZone::zone,
			BoundZone::new);
}
