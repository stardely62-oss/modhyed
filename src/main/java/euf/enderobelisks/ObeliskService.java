package euf.enderobelisks;

import euf.enderobelisks.block.ObeliskBlock;
import euf.enderobelisks.block.ObeliskBlockEntity;
import euf.enderobelisks.net.ObeliskPackets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Server-side rules: placing, access, teleporting, upgrading, renaming. Every packet handler goes through here. */
public final class ObeliskService {
	public static final int COOLDOWN_TICKS = 30 * 20;
	public static final int MAX_NAME_LENGTH = 32;
	/** How close a player must stand to an obelisk to work its menu. */
	public static final double USE_DISTANCE = 6.0;

	public enum TeleportResult { OK, NO_SOURCE, NO_TARGET, SAME, NO_ACCESS, TOO_FAR, COOLDOWN, NO_SPACE }

	private static final Map<UUID, Long> lastTeleport = new HashMap<>();

	private ObeliskService() {
	}

	/** Registers a freshly placed obelisk; {@code owner} may be null for obelisks set by commands or tests. */
	public static ObeliskInfo register(ServerWorld world, BlockPos lower, @Nullable PlayerEntity owner, @Nullable String customName) {
		ObeliskRegistry registry = ObeliskRegistry.get(world.getServer());
		Optional<ObeliskInfo> existing = registry.at(world.getRegistryKey(), lower);
		if (existing.isPresent()) {
			return existing.get();
		}
		UUID ownerId = owner != null ? owner.getUuid() : new UUID(0, 0);
		String ownerName = owner != null ? owner.getGameProfile().getName() : "?";
		String name = customName != null && !customName.isBlank() ? clampName(customName)
				: Text.translatableWithFallback("ender_obelisks.default_name", "Obelisk #%s", registry.all().size() + 1).getString();
		ObeliskInfo info = new ObeliskInfo(UUID.randomUUID(), world.getRegistryKey(), lower, ownerId, ownerName, name, true, 0);
		registry.put(info);
		syncBlockEntity(world, info);
		return info;
	}

	public static void unregister(ServerWorld world, BlockPos lower) {
		ObeliskRegistry registry = ObeliskRegistry.get(world.getServer());
		registry.at(world.getRegistryKey(), lower).ifPresent(info -> registry.remove(info.id));
	}

	public static Optional<ObeliskInfo> at(ServerWorld world, BlockPos lower) {
		return ObeliskRegistry.get(world.getServer()).at(world.getRegistryKey(), lower);
	}

	public static boolean isOwner(ObeliskInfo info, PlayerEntity player) {
		return info.owner.equals(player.getUuid()) || player.hasPermissionLevel(2);
	}

	/** Public obelisks are open to everyone; private ones to their owner (and operators). */
	public static boolean canUse(ObeliskInfo info, PlayerEntity player) {
		return info.isPublic || isOwner(info, player);
	}

	public static int cooldownLeft(ServerPlayerEntity player) {
		Long last = lastTeleport.get(player.getUuid());
		if (last == null) {
			return 0;
		}
		return (int) Math.max(0, last + COOLDOWN_TICKS - player.getServer().getTicks());
	}

	/** For tests: lets the next teleport go through at once. */
	public static void clearCooldown(PlayerEntity player) {
		lastTeleport.remove(player.getUuid());
	}

	public static boolean isNear(ServerPlayerEntity player, BlockPos lower) {
		return player.getPos().squaredDistanceTo(Vec3d.ofCenter(lower.up())) <= USE_DISTANCE * USE_DISTANCE;
	}

	public static TeleportResult check(ServerPlayerEntity player, ObeliskInfo source, @Nullable ObeliskInfo target) {
		if (target == null || !target.dimension.equals(source.dimension)) {
			return TeleportResult.NO_TARGET;
		}
		if (target.id.equals(source.id)) {
			return TeleportResult.SAME;
		}
		if (!canUse(source, player) || !canUse(target, player)) {
			return TeleportResult.NO_ACCESS;
		}
		if (source.distanceTo(target) > source.range()) {
			return TeleportResult.TOO_FAR;
		}
		if (cooldownLeft(player) > 0) {
			return TeleportResult.COOLDOWN;
		}
		return TeleportResult.OK;
	}

	public static TeleportResult teleport(ServerPlayerEntity player, BlockPos sourcePos, UUID targetId) {
		ServerWorld world = player.getServerWorld();
		Optional<ObeliskInfo> source = at(world, sourcePos);
		if (source.isEmpty() || !isNear(player, sourcePos)) {
			return TeleportResult.NO_SOURCE;
		}
		ObeliskRegistry registry = ObeliskRegistry.get(world.getServer());
		ObeliskInfo target = registry.byId(targetId).orElse(null);
		TeleportResult result = check(player, source.get(), target);
		if (result != TeleportResult.OK) {
			return result;
		}
		// Loads the target chunk; an obelisk destroyed while its chunk was unloaded is dropped from the registry here
		if (!(world.getBlockState(target.pos).getBlock() instanceof ObeliskBlock)) {
			registry.remove(target.id);
			return TeleportResult.NO_TARGET;
		}
		Optional<Vec3d> landing = findLanding(world, target.pos);
		if (landing.isEmpty()) {
			return TeleportResult.NO_SPACE;
		}

		effects(world, player.getPos());
		Vec3d spot = landing.get();
		// Face away from the obelisk you arrive at
		Vec3d away = spot.subtract(Vec3d.ofBottomCenter(target.pos));
		float yaw = (float) (Math.atan2(-away.x, away.z) * 180.0 / Math.PI);
		player.teleport(world, spot.x, spot.y, spot.z, yaw, 0.0f);
		player.fallDistance = 0.0f;
		effects(world, spot);
		lastTeleport.put(player.getUuid(), (long) world.getServer().getTicks());
		return TeleportResult.OK;
	}

	/** Two free blocks of height with solid ground, on one of the four sides, a little up or down. */
	static Optional<Vec3d> findLanding(ServerWorld world, BlockPos lower) {
		BlockState state = world.getBlockState(lower);
		Direction front = state.contains(ObeliskBlock.FACING) ? state.get(ObeliskBlock.FACING) : Direction.NORTH;
		List<Direction> sides = List.of(front, front.rotateYClockwise(), front.rotateYCounterclockwise(), front.getOpposite());
		for (int dy : new int[] {0, 1, -1, 2}) {
			for (Direction side : sides) {
				BlockPos feet = lower.offset(side).up(dy);
				if (world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
						&& world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
						&& world.getBlockState(feet.down()).isSideSolidFullSquare(world, feet.down(), Direction.UP)) {
					return Optional.of(Vec3d.ofBottomCenter(feet));
				}
			}
		}
		return Optional.empty();
	}

	private static void effects(ServerWorld world, Vec3d at) {
		world.spawnParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 60, 0.4, 0.8, 0.4, 0.6);
		world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
	}

	/** Consumes the next tier's cost (free in creative) and raises the tier. Owner only. */
	public static boolean upgrade(ServerPlayerEntity player, BlockPos sourcePos) {
		ServerWorld world = player.getServerWorld();
		Optional<ObeliskInfo> found = at(world, sourcePos);
		if (found.isEmpty() || !isNear(player, sourcePos) || !isOwner(found.get(), player)) {
			return false;
		}
		ObeliskInfo info = found.get();
		List<ItemStack> cost = ObeliskTiers.upgradeCost(info.tier);
		if (cost.isEmpty()) {
			return false;
		}
		if (!player.getAbilities().creativeMode) {
			PlayerInventory inventory = player.getInventory();
			if (!cost.stream().allMatch(stack -> inventory.count(stack.getItem()) >= stack.getCount())) {
				return false;
			}
			cost.forEach(stack -> take(inventory, stack.getItem(), stack.getCount()));
		}
		info.tier++;
		ObeliskRegistry.get(world.getServer()).markDirty();
		syncBlockEntity(world, info);
		world.playSound(null, sourcePos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.0f, 1.2f);
		return true;
	}

	private static void take(PlayerInventory inventory, Item item, int count) {
		for (int i = 0; i < inventory.size() && count > 0; i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.isOf(item)) {
				int n = Math.min(count, stack.getCount());
				stack.decrement(n);
				count -= n;
			}
		}
		inventory.markDirty();
	}

	public static boolean rename(ServerPlayerEntity player, BlockPos sourcePos, String name) {
		Optional<ObeliskInfo> found = at(player.getServerWorld(), sourcePos);
		String clean = clampName(name);
		if (found.isEmpty() || !isNear(player, sourcePos) || !isOwner(found.get(), player) || clean.isBlank()) {
			return false;
		}
		found.get().name = clean;
		ObeliskRegistry.get(player.getServer()).markDirty();
		syncBlockEntity(player.getServerWorld(), found.get());
		return true;
	}

	public static boolean togglePublic(ServerPlayerEntity player, BlockPos sourcePos) {
		Optional<ObeliskInfo> found = at(player.getServerWorld(), sourcePos);
		if (found.isEmpty() || !isNear(player, sourcePos) || !isOwner(found.get(), player)) {
			return false;
		}
		found.get().isPublic = !found.get().isPublic;
		ObeliskRegistry.get(player.getServer()).markDirty();
		return true;
	}

	/** Opens (or refreshes) the obelisk menu for a player standing at it. */
	public static void openMenu(ServerPlayerEntity player, BlockPos sourcePos) {
		at(player.getServerWorld(), sourcePos).ifPresent(source -> {
			List<ObeliskInfo> visible = ObeliskRegistry.get(player.getServer()).all().stream()
					.filter(o -> o.dimension.equals(source.dimension) && canUse(o, player))
					.toList();
			ObeliskPackets.sendMenu(player, source, isOwner(source, player), cooldownLeft(player), visible);
		});
	}

	public static String clampName(String name) {
		String trimmed = name.strip().replaceAll("\\p{Cntrl}", "");
		return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
	}

	/** Copies what the renderer needs (name, tier) into the loaded block entity and sends it to clients. */
	private static void syncBlockEntity(ServerWorld world, ObeliskInfo info) {
		if (world.isChunkLoaded(info.pos) && world.getBlockEntity(info.pos) instanceof ObeliskBlockEntity entity) {
			entity.update(info.id, info.name, info.tier);
		}
	}
}
