package io.github.autyism.easysuppressions.vanilla;

import io.github.autyism.easysuppressions.item.SelectorItem;
import io.github.autyism.easysuppressions.net.ServerNet;
import io.github.autyism.easysuppressions.world.EsWorldState;
import io.github.autyism.easysuppressions.world.ServerZones;
import io.github.autyism.easysuppressions.zone.Zone;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a player without the mod needs besides the stand-ins and dialogs:
 * <ul>
 * <li>selectors: the server reads the clicks itself (right click = use, left click = arm swing) and
 * aims like the mod does, up to 64 blocks;</li>
 * <li>portal cutter: such a client predicts portal blocks breaking when it places or breaks a block
 * next to them; the server keeps them, so it sends them back to that player.</li>
 * </ul>
 */
public final class VanillaSupport {
	private static final double RANGE = 64.0;
	/** Swings this close together are one press (holding the button swings every tick). */
	private static final int SWING_GAP = 5;
	private static final int MAX_PORTAL_RESEND = 4096;

	private static final Map<UUID, Long> LAST_SWING = new HashMap<>();
	private static final Map<UUID, Long> LAST_USE = new HashMap<>();

	private VanillaSupport() {
	}

	public static void init() {
		UseItemCallback.EVENT.register((player, world, hand) -> rightClick(player, hand));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> rightClick(player, hand));
		ServerTickEvents.END_SERVER_TICK.register(VanillaZones::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUuid();
			LAST_SWING.remove(id);
			LAST_USE.remove(id);
			VanillaScreens.forget(handler.player);
			VanillaZones.forget(handler.player);
		});
	}

	private static ActionResult rightClick(PlayerEntity p, Hand hand) {
		if (!(p instanceof ServerPlayerEntity player) || EsClients.has(player) || !(player.getStackInHand(hand).getItem() instanceof SelectorItem)) {
			return ActionResult.PASS;
		}
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.FAIL;
		}
		long now = player.getEntityWorld().getTime();
		Long last = LAST_USE.put(player.getUuid(), now);
		// the client sends "use on block" and then "use item" for the same click
		if (last == null || last != now) {
			aimAndSet(player, 2);
		}
		return ActionResult.SUCCESS;
	}

	/** Left click (the arm swing packet), see mixin/ServerPlayNetworkHandlerMixin. */
	public static void onSwing(ServerPlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND || EsClients.has(player) || !(player.getMainHandStack().getItem() instanceof SelectorItem)) {
			return;
		}
		long now = player.getEntityWorld().getTime();
		Long lastUse = LAST_USE.get(player.getUuid());
		if (lastUse != null && now - lastUse <= 1) {
			return; // the swing of a right click
		}
		Long last = LAST_SWING.put(player.getUuid(), now);
		if (last != null && now - last <= SWING_GAP) {
			return;
		}
		aimAndSet(player, 1);
	}

	private static void aimAndSet(ServerPlayerEntity player, int corner) {
		HitResult hit = player.raycast(RANGE, 1.0F, false);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			ServerNet.setCorner(player, corner, block.getBlockPos());
		}
	}

	/** After a vanilla player's block action next to a portal cutter zone, see mixin/ServerPlayNetworkHandlerMixin. */
	public static void afterBlockAction(ServerPlayerEntity player, BlockPos pos) {
		if (EsClients.has(player)) {
			return;
		}
		ServerWorld world = player.getEntityWorld();
		for (EsWorldState.CutterEntry e : ServerZones.of(world).cutters()) {
			Zone z = e.zone();
			if (pos.getX() < z.minX() - 1 || pos.getX() > z.maxX() + 1 || pos.getY() < z.minY() - 1 || pos.getY() > z.maxY() + 1
					|| pos.getZ() < z.minZ() - 1 || pos.getZ() > z.maxZ() + 1) {
				continue;
			}
			// the client's breaking spreads through the portal blocks connected to the changed block
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			Set<BlockPos> seen = new HashSet<>();
			for (Direction d : Direction.values()) {
				queue.add(pos.offset(d));
			}
			while (!queue.isEmpty() && seen.size() < MAX_PORTAL_RESEND) {
				BlockPos p = queue.poll();
				if (!seen.add(p) || !z.contains(p) || !world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)
						|| !world.getBlockState(p).isOf(Blocks.NETHER_PORTAL)) {
					continue;
				}
				player.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, p));
				for (Direction d : Direction.values()) {
					queue.add(p.offset(d));
				}
			}
		}
	}
}
