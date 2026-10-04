package io.github.autyism.easysuppressions.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.autyism.easysuppressions.light.LightSettings;
import io.github.autyism.easysuppressions.zone.Zone;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The registers saved with each world (dimension): active portal cutter zones and locked light
 * suppressor zones. The recorded light itself lives in separate files (LightDataStore).
 */
public final class EsWorldState extends PersistentState {
	public record CutterEntry(BlockPos source, Zone zone) {
		static final Codec<CutterEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("source").forGetter(CutterEntry::source),
				Zone.CODEC.fieldOf("zone").forGetter(CutterEntry::zone)
		).apply(i, CutterEntry::new));
	}

	public record SuppressorEntry(BlockPos source, Zone zone, LightSettings settings, Optional<UUID> owner, boolean completeNotified) {
		static final Codec<SuppressorEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("source").forGetter(SuppressorEntry::source),
				Zone.CODEC.fieldOf("zone").forGetter(SuppressorEntry::zone),
				LightSettings.CODEC.fieldOf("settings").forGetter(SuppressorEntry::settings),
				Uuids.CODEC.optionalFieldOf("owner").forGetter(SuppressorEntry::owner),
				Codec.BOOL.optionalFieldOf("complete_notified", false).forGetter(SuppressorEntry::completeNotified)
		).apply(i, SuppressorEntry::new));

		public SuppressorEntry withCompleteNotified() {
			return new SuppressorEntry(source, zone, settings, owner, true);
		}
	}

	private static final Codec<EsWorldState> CODEC = RecordCodecBuilder.create(i -> i.group(
			CutterEntry.CODEC.listOf().optionalFieldOf("portal_cutters", List.of()).forGetter(s -> new ArrayList<>(s.cutters.values())),
			SuppressorEntry.CODEC.listOf().optionalFieldOf("light_suppressors", List.of()).forGetter(s -> new ArrayList<>(s.suppressors.values()))
	).apply(i, EsWorldState::new));

	public static final PersistentStateType<EsWorldState> TYPE = new PersistentStateType<>(
			"easysuppressions_zones", EsWorldState::new, CODEC, null);

	final Map<BlockPos, CutterEntry> cutters = new LinkedHashMap<>();
	final Map<BlockPos, SuppressorEntry> suppressors = new LinkedHashMap<>();

	public EsWorldState() {
	}

	private EsWorldState(List<CutterEntry> cutters, List<SuppressorEntry> suppressors) {
		for (CutterEntry e : cutters) {
			this.cutters.put(e.source(), e);
		}
		for (SuppressorEntry e : suppressors) {
			this.suppressors.put(e.source(), e);
		}
	}
}
