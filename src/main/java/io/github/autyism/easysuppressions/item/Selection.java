package io.github.autyism.easysuppressions.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import io.github.autyism.easysuppressions.vanilla.EsClients;
import io.github.autyism.easysuppressions.zone.Zone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;

/** The two corners stored on a selector item. Both corners are always in {@link #dimension}. */
public record Selection(RegistryKey<World> dimension, Optional<BlockPos> pos1, Optional<BlockPos> pos2) implements PolymerComponent {
	/** Only clients with the mod know this component. */
	@Override
	public boolean canSyncRawToClient(xyz.nucleoid.packettweaker.PacketContext context) {
		return EsClients.has(context);
	}

	public static final Codec<Selection> CODEC = RecordCodecBuilder.create(i -> i.group(
			World.CODEC.fieldOf("dimension").forGetter(Selection::dimension),
			BlockPos.CODEC.optionalFieldOf("pos1").forGetter(Selection::pos1),
			BlockPos.CODEC.optionalFieldOf("pos2").forGetter(Selection::pos2)
	).apply(i, Selection::new));

	public static final PacketCodec<ByteBuf, Selection> PACKET_CODEC = PacketCodec.tuple(
			RegistryKey.createPacketCodec(RegistryKeys.WORLD), Selection::dimension,
			PacketCodecs.optional(BlockPos.PACKET_CODEC), Selection::pos1,
			PacketCodecs.optional(BlockPos.PACKET_CODEC), Selection::pos2,
			Selection::new);

	public boolean isComplete() {
		return pos1.isPresent() && pos2.isPresent();
	}

	public Optional<Zone> zone() {
		return isComplete() ? Optional.of(Zone.of(pos1.get(), pos2.get())) : Optional.empty();
	}

	public Selection with(int index, BlockPos pos) {
		return index == 1 ? new Selection(dimension, Optional.of(pos.toImmutable()), pos2)
				: new Selection(dimension, pos1, Optional.of(pos.toImmutable()));
	}
}
