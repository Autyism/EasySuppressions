package io.github.autyism.easysuppressions.client.model;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ResolvableModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Block state model with two looks, picked per block from the block entity's render data
 * (Boolean "active"). The active state is not part of the block state on purpose (observers must
 * not notice it), so the switch happens here, at chunk meshing time. Used in the blockstate file as
 * {@code "fabric:type": "easysuppressions:active_switch"} with an "inactive" and an "active" variant.
 */
public final class ActiveSwitchModel implements BlockStateModel {
	private final BlockStateModel inactive;
	private final BlockStateModel active;

	private ActiveSwitchModel(BlockStateModel inactive, BlockStateModel active) {
		this.inactive = inactive;
		this.active = active;
	}

	private BlockStateModel pick(BlockRenderView view, BlockPos pos) {
		return view.getBlockEntityRenderData(pos) instanceof Boolean on && on ? active : inactive;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockRenderView blockView, BlockPos pos, BlockState state, Random random, Predicate<@Nullable Direction> cullTest) {
		pick(blockView, pos).emitQuads(emitter, blockView, pos, state, random, cullTest);
	}

	@Override
	@Nullable
	public Object createGeometryKey(BlockRenderView blockView, BlockPos pos, BlockState state, Random random) {
		BlockStateModel model = pick(blockView, pos);
		Object key = model.createGeometryKey(blockView, pos, state, random);
		return key == null ? null : new Key(model == active, key);
	}

	@Override
	public Sprite particleSprite(BlockRenderView blockView, BlockPos pos, BlockState state) {
		return pick(blockView, pos).particleSprite(blockView, pos, state);
	}

	/** Without world context (item frames, falling blocks...): the inactive look. */
	@Override
	public void addParts(Random random, List<BlockModelPart> parts) {
		inactive.addParts(random, parts);
	}

	@Override
	public Sprite particleSprite() {
		return inactive.particleSprite();
	}

	private record Key(boolean active, Object inner) {
	}

	public record Unbaked(BlockStateModel.Unbaked inactive, BlockStateModel.Unbaked active) implements CustomUnbakedBlockStateModel {
		public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				BlockStateModel.Unbaked.CODEC.fieldOf("inactive").forGetter(Unbaked::inactive),
				BlockStateModel.Unbaked.CODEC.fieldOf("active").forGetter(Unbaked::active)
		).apply(i, Unbaked::new));

		@Override
		public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
			return CODEC;
		}

		@Override
		public void resolve(ResolvableModel.Resolver resolver) {
			inactive.resolve(resolver);
			active.resolve(resolver);
		}

		@Override
		public BlockStateModel bake(Baker baker) {
			return new ActiveSwitchModel(inactive.bake(baker), active.bake(baker));
		}
	}
}
