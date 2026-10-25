package euf.enderobelisks;

import com.mojang.authlib.GameProfile;
import euf.enderobelisks.block.ObeliskBlock;
import euf.enderobelisks.block.ObeliskBlockEntity;
import euf.enderobelisks.net.ObeliskPackets;
import euf.enderobelisks.net.ObeliskSync;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Server-side rules: placing, access, teleporting, upgrading, renaming. Every packet handler goes through here. */
public final class ObeliskService {
	public static final int MAX_NAME_LENGTH = 32;
	/** How close a player must stand to an obelisk to work its menu. */
	public static final double USE_DISTANCE = 6.0;
	/** Moving further than this from where the warmup started cancels it. */
	private static final double WARMUP_MOVE_LIMIT = 0.6;

	public enum TeleportResult { OK, NO_SOURCE, NO_TARGET, SAME, NO_ACCESS, TOO_FAR, COOLDOWN, NO_SPACE, OTHER_DIMENSION, BUSY }

	/** A jump that is still channelling. */
	private record Pending(ServerPlayerEntity player, BlockPos source, UUID target, long startTick, Vec3d startPos) {}

	private static final Map<UUID, Long> lastTeleport = new HashMap<>();
	private static final Map<UUID, Pending> pending = new HashMap<>();

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
		ObeliskSync.broadcast(world.getServer());
		return info;
	}

	public static void unregister(ServerWorld world, BlockPos lower) {
		ObeliskRegistry registry = ObeliskRegistry.get(world.getServer());
		registry.at(world.getRegistryKey(), lower).ifPresent(info -> {
			registry.remove(info.id);
			ObeliskSync.broadcast(world.getServer());
		});
	}

	public static Optional<ObeliskInfo> at(ServerWorld world, BlockPos lower) {
		return ObeliskRegistry.get(world.getServer()).at(world.getRegistryKey(), lower);
	}

	public static boolean isOwner(ObeliskInfo info, PlayerEntity player) {
		return info.owner.equals(player.getUuid()) || player.hasPermissionLevel(2);
	}

	/** Public obelisks are open to everyone; private ones to their owner, operators and the owner's friends list. */
	public static boolean canUse(ObeliskInfo info, PlayerEntity player) {
		return info.isPublic || isOwner(info, player) || info.friends.containsKey(player.getUuid());
	}

	public static int cooldownLeft(ServerPlayerEntity player) {
		Long last = lastTeleport.get(player.getUuid());
		if (last == null) {
			return 0;
		}
		return (int) Math.max(0, last + ObeliskConfig.get().cooldownSeconds * 20L - player.getServer().getTicks());
	}

	/** Forgets cooldowns and channelling jumps when a server (or a singleplayer world) stops. */
	public static void reset() {
		lastTeleport.clear();
		pending.clear();
	}

	/** For tests: lets the next teleport go through at once. */
	public static void clearCooldown(PlayerEntity player) {
		lastTeleport.remove(player.getUuid());
	}

	public static boolean isNear(ServerPlayerEntity player, BlockPos lower) {
		return player.getPos().squaredDistanceTo(Vec3d.ofCenter(lower.up())) <= USE_DISTANCE * USE_DISTANCE;
	}

	public static TeleportResult check(ServerPlayerEntity player, ObeliskInfo source, @Nullable ObeliskInfo target) {
		if (target == null) {
			return TeleportResult.NO_TARGET;
		}
		if (target.id.equals(source.id)) {
			return TeleportResult.SAME;
		}
		if (!canUse(source, player) || !canUse(target, player)) {
			return TeleportResult.NO_ACCESS;
		}
		if (!target.dimension.equals(source.dimension)) {
			ObeliskConfig config = ObeliskConfig.get();
			if (!config.crossDimension || source.tier < config.crossDimensionMinTier) {
				return TeleportResult.OTHER_DIMENSION;
			}
		} else if (source.distanceTo(target) > source.range()) {
			return TeleportResult.TOO_FAR;
		}
		if (cooldownLeft(player) > 0) {
			return TeleportResult.COOLDOWN;
		}
		return TeleportResult.OK;
	}

	/**
	 * What the menu's Teleport button does: checks the jump, then channels it for the configured warmup (moving or
	 * taking damage cancels it) and only then teleports. With no warmup it jumps at once.
	 */
	public static TeleportResult startTeleport(ServerPlayerEntity player, BlockPos sourcePos, UUID targetId) {
		if (pending.containsKey(player.getUuid())) {
			return TeleportResult.BUSY;
		}
		Optional<ObeliskInfo> source = at(player.getServerWorld(), sourcePos);
		if (source.isEmpty() || !isNear(player, sourcePos)) {
			return TeleportResult.NO_SOURCE;
		}
		TeleportResult result = check(player, source.get(), ObeliskRegistry.get(player.getServer()).byId(targetId).orElse(null));
		if (result != TeleportResult.OK) {
			return result;
		}
		if (ObeliskConfig.get().warmupSeconds <= 0) {
			return teleport(player, sourcePos, targetId);
		}
		pending.put(player.getUuid(), new Pending(player, sourcePos, targetId, player.getServer().getTicks(), player.getPos()));
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ObeliskSounds.CHANNEL,
				SoundCategory.PLAYERS, 0.6f, 1.0f);
		return TeleportResult.OK;
	}

	public static boolean isChanneling(PlayerEntity player) {
		return pending.containsKey(player.getUuid());
	}

	/** Called when a player takes damage: a channelling jump is broken off. */
	public static void onDamaged(ServerPlayerEntity player) {
		if (pending.remove(player.getUuid()) != null) {
			cancelled(player, "ender_obelisks.teleport.interrupted_damage");
		}
	}

	/** Every server tick: draws the channelling effect, cancels jumps of players who moved, finishes the rest. */
	public static void tick(MinecraftServer server) {
		int warmupTicks = ObeliskConfig.get().warmupSeconds * 20;
		for (Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending jump = entry.getValue();
			ServerPlayerEntity player = jump.player();
			if (player.isRemoved() || !player.isAlive()) {
				it.remove();
				continue;
			}
			if (player.getPos().squaredDistanceTo(jump.startPos()) > WARMUP_MOVE_LIMIT * WARMUP_MOVE_LIMIT) {
				it.remove();
				cancelled(player, "ender_obelisks.teleport.interrupted_move");
				continue;
			}
			long elapsed = server.getTicks() - jump.startTick();
			if (elapsed >= warmupTicks) {
				it.remove();
				TeleportResult result = teleport(player, jump.source(), jump.target());
				if (result != TeleportResult.OK) {
					player.sendMessage(Text.translatable("ender_obelisks.teleport." + result.name().toLowerCase(Locale.ROOT)), true);
				}
				continue;
			}
			// A spiral of portal particles closing in around the player, thicker as the jump nears
			ServerWorld world = player.getServerWorld();
			float progress = (float) elapsed / warmupTicks;
			for (int i = 0; i < 2 + (int) (progress * 6); i++) {
				double a = (elapsed * 0.35) + i * Math.PI * 2 / 3;
				double r = 1.2 - progress * 0.8;
				world.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX() + Math.cos(a) * r, player.getY() + 0.1 + progress * 1.8,
						player.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
			if (elapsed % 20 == 0) {
				int secondsLeft = MathHelper.ceil((warmupTicks - elapsed) / 20.0f);
				player.sendMessage(Text.translatable("ender_obelisks.teleport.channeling", secondsLeft), true);
			}
		}
	}

	private static void cancelled(ServerPlayerEntity player, String message) {
		player.sendMessage(Text.translatable(message), true);
		player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ObeliskSounds.CANCEL,
				SoundCategory.PLAYERS, 0.6f, 1.0f);
	}

	/** The jump itself, with no warmup: re-checks everything, moves the player and their pets, starts the cooldown. */
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
		ServerWorld targetWorld = world.getServer().getWorld(target.dimension);
		// Loads the target chunk; an obelisk destroyed while its chunk was unloaded is dropped from the registry here
		if (targetWorld == null || !(targetWorld.getBlockState(target.pos).getBlock() instanceof ObeliskBlock)) {
			registry.remove(target.id);
			ObeliskSync.broadcast(world.getServer());
			return TeleportResult.NO_TARGET;
		}
		Optional<Vec3d> landing = findLanding(targetWorld, target.pos);
		if (landing.isEmpty()) {
			return TeleportResult.NO_SPACE;
		}

		List<Entity> companions = ObeliskConfig.get().takePets ? companions(player) : List.of();
		effects(world, player.getPos());
		Vec3d spot = landing.get();
		// Face away from the obelisk you arrive at
		Vec3d away = spot.subtract(Vec3d.ofBottomCenter(target.pos));
		float yaw = (float) (Math.atan2(-away.x, away.z) * 180.0 / Math.PI);
		player.teleport(targetWorld, spot.x, spot.y, spot.z, yaw, 0.0f);
		player.fallDistance = 0.0f;
		for (int i = 0; i < companions.size(); i++) {
			// Spread them a little around the landing spot so they don't stack inside each other
			double a = i * 2.4;
			companions.get(i).teleport(targetWorld, spot.x + Math.cos(a) * 0.6, spot.y, spot.z + Math.sin(a) * 0.6,
					EnumSet.noneOf(PositionFlag.class), companions.get(i).getYaw(), companions.get(i).getPitch());
		}
		effects(targetWorld, spot);
		lastTeleport.put(player.getUuid(), (long) world.getServer().getTicks());
		ObeliskCriteria.TELEPORTED.trigger(player, target.dimension.equals(source.get().dimension) ? source.get().distanceTo(target) : 0);
		return TeleportResult.OK;
	}

	/** Tamed pets of this player that are not told to sit, and any mob on this player's lead. */
	static List<Entity> companions(ServerPlayerEntity player) {
		double r = ObeliskConfig.get().petRadius;
		return player.getServerWorld().getOtherEntities(player, new Box(player.getBlockPos()).expand(r), e ->
				(e instanceof TameableEntity pet && pet.isTamed() && player.getUuid().equals(pet.getOwnerUuid()) && !pet.isSitting())
						|| (e instanceof MobEntity mob && mob.getHoldingEntity() == player));
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
		world.playSound(null, at.x, at.y, at.z, ObeliskSounds.TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
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
		if (info.tier >= ObeliskTiers.MAX_TIER) {
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
		changed(world.getServer(), world, info);
		world.playSound(null, sourcePos, ObeliskSounds.UPGRADE, SoundCategory.BLOCKS, 1.0f, 1.0f);
		ObeliskCriteria.UPGRADED.trigger(player, info.tier);
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
		changed(player.getServer(), player.getServerWorld(), found.get());
		return true;
	}

	public static boolean togglePublic(ServerPlayerEntity player, BlockPos sourcePos) {
		Optional<ObeliskInfo> found = at(player.getServerWorld(), sourcePos);
		if (found.isEmpty() || !isNear(player, sourcePos) || !isOwner(found.get(), player)) {
			return false;
		}
		found.get().isPublic = !found.get().isPublic;
		changed(player.getServer(), player.getServerWorld(), found.get());
		return true;
	}

	public static boolean setColor(ServerPlayerEntity player, BlockPos sourcePos, int color) {
		Optional<ObeliskInfo> found = at(player.getServerWorld(), sourcePos);
		if (found.isEmpty() || !isNear(player, sourcePos) || !isOwner(found.get(), player)) {
			return false;
		}
		found.get().color = Math.floorMod(color, ObeliskColors.RGB.length);
		changed(player.getServer(), player.getServerWorld(), found.get());
		return true;
	}

	/** Lets a player into a private obelisk, by name: someone online, or anyone who has played on this server. */
	public static boolean addFriend(ServerPlayerEntity player, BlockPos sourcePos, String name) {
		Optional<ObeliskInfo> found = at(player.getServerWorld(), sourcePos);
		if (found.isEmpty() || !isNear(player, sourcePos) || !isOwner(found.get(), player) || name.isBlank()) {
			return false;
		}
		MinecraftServer server = player.getServer();
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(name.strip());
		Optional<GameProfile> profile = online != null ? Optional.of(online.getGameProfile())
				: server.getUserCache() == null ? Optional.empty() : server.getUserCache().findByName(name.strip());
		if (profile.isEmpty() || profile.get().getId().equals(found.get().owner)) {
			return false;
		}
		found.get().friends.put(profile.get().getId(), profile.get().getName());
		changed(server, player.getServerWorld(), found.get());
		return true;
	}

	public static boolean removeFriend(ServerPlayerEntity player, BlockPos sourcePos, UUID friend) {
		Optional<ObeliskInfo> found = at(player.getServerWorld(), sourcePos);
		if (found.isEmpty() || !isNear(player, sourcePos) || !isOwner(found.get(), player)) {
			return false;
		}
		found.get().friends.remove(friend);
		changed(player.getServer(), player.getServerWorld(), found.get());
		return true;
	}

	/** After an edit: save, update the block entity, tell every client. */
	private static void changed(MinecraftServer server, ServerWorld world, ObeliskInfo info) {
		ObeliskRegistry.get(server).markDirty();
		syncBlockEntity(world, info);
		ObeliskSync.broadcast(server);
	}

	/** Opens (or refreshes) the obelisk menu for a player standing at it. */
	public static void openMenu(ServerPlayerEntity player, BlockPos sourcePos) {
		at(player.getServerWorld(), sourcePos).ifPresent(source -> {
			boolean crossDimension = ObeliskConfig.get().crossDimension;
			List<ObeliskInfo> visible = ObeliskRegistry.get(player.getServer()).all().stream()
					.filter(o -> (crossDimension || o.dimension.equals(source.dimension)) && canUse(o, player))
					.toList();
			ObeliskPackets.sendMenu(player, source, isOwner(source, player), cooldownLeft(player), visible);
		});
	}

	public static String clampName(String name) {
		String trimmed = name.strip().replaceAll("\\p{Cntrl}", "");
		return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
	}

	/** Copies what the renderer needs (name, tier) into the loaded block entity and sends it to clients. */
	static void syncBlockEntity(ServerWorld world, ObeliskInfo info) {
		if (world.isChunkLoaded(info.pos) && world.getBlockEntity(info.pos) instanceof ObeliskBlockEntity entity) {
			entity.update(info.id, info.name, info.tier, info.color);
		}
	}
}
