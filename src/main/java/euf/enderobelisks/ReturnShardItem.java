package euf.enderobelisks;

import euf.enderobelisks.ObeliskService.TeleportResult;
import euf.enderobelisks.block.ObeliskBlock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A one-way ticket home. Sneak-right-click an obelisk to bind it; then hold right click anywhere for a few seconds to
 * return to that obelisk (with your pets). Used up on arrival; a cooldown follows.
 */
public class ReturnShardItem extends Item {
	private static final String KEY = "Obelisk";

	public ReturnShardItem(Settings settings) {
		super(settings);
	}

	@Nullable
	static UUID boundId(ItemStack stack) {
		NbtCompound nbt = stack.getSubNbt(KEY);
		return nbt != null && nbt.containsUuid("id") ? nbt.getUuid("id") : null;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		PlayerEntity player = context.getPlayer();
		BlockState state = context.getWorld().getBlockState(context.getBlockPos());
		if (player == null || !player.isSneaking() || !(state.getBlock() instanceof ObeliskBlock)) {
			return ActionResult.PASS;
		}
		if (context.getWorld() instanceof ServerWorld world) {
			Optional<ObeliskInfo> info = ObeliskService.at(world, ObeliskBlock.lowerPos(state, context.getBlockPos()));
			if (info.isEmpty() || !ObeliskService.canUse(info.get(), player)) {
				player.sendMessage(Text.translatable("ender_obelisks.teleport.no_access"), true);
				return ActionResult.FAIL;
			}
			bind(context.getStack(), info.get());
			player.sendMessage(Text.translatable("ender_obelisks.return.bound", info.get().name), true);
			world.playSound(null, context.getBlockPos(), ObeliskSounds.PLACE, SoundCategory.PLAYERS, 0.6f, 1.4f);
		}
		return ActionResult.success(context.getWorld().isClient());
	}

	public static void bind(ItemStack stack, ObeliskInfo info) {
		NbtCompound nbt = stack.getOrCreateSubNbt(KEY);
		nbt.putUuid("id", info.id);
		nbt.putString("name", info.name);
		nbt.putString("dim", info.dimension.getValue().toString());
		nbt.putLong("pos", info.pos.asLong());
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (boundId(stack) == null) {
			if (!world.isClient()) {
				user.sendMessage(Text.translatable("ender_obelisks.return.unbound"), true);
			}
			return TypedActionResult.fail(stack);
		}
		user.setCurrentHand(hand);
		if (!world.isClient()) {
			world.playSound(null, user.getX(), user.getY(), user.getZ(), ObeliskSounds.CHANNEL, SoundCategory.PLAYERS, 0.6f, 0.8f);
		}
		return TypedActionResult.consume(stack);
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return ObeliskConfig.get().returnShardSeconds * 20;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BOW;
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (world instanceof ServerWorld serverWorld && remainingUseTicks % 2 == 0) {
			double a = remainingUseTicks * 0.4;
			serverWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, user.getX() + Math.cos(a) * 0.8, user.getY() + 1.0,
					user.getZ() + Math.sin(a) * 0.8, 2, 0.05, 0.3, 0.05, 0);
		}
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!(user instanceof ServerPlayerEntity player)) {
			return stack;
		}
		TeleportResult result = goHome(player, stack);
		if (result != TeleportResult.OK) {
			player.sendMessage(Text.translatable("ender_obelisks.teleport." + result.name().toLowerCase(Locale.ROOT)), true);
			return stack;
		}
		player.getItemCooldownManager().set(this, ObeliskConfig.get().returnShardCooldownSeconds * 20);
		if (!player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
		return stack;
	}

	/** The checks and the jump, without the hold-to-use part (tests call this directly). */
	public static TeleportResult goHome(ServerPlayerEntity player, ItemStack stack) {
		UUID id = boundId(stack);
		ObeliskInfo target = id == null ? null : ObeliskRegistry.get(player.getServer()).byId(id).orElse(null);
		if (target == null) {
			return TeleportResult.NO_TARGET;
		}
		if (!ObeliskService.canUse(target, player)) {
			return TeleportResult.NO_ACCESS;
		}
		boolean sameDimension = target.dimension.equals(player.getWorld().getRegistryKey());
		if (!sameDimension && !ObeliskConfig.get().returnShardCrossDimension) {
			return TeleportResult.OTHER_DIMENSION;
		}
		double distance = sameDimension ? Math.sqrt(player.getBlockPos().getSquaredDistance(target.pos)) : 0;
		TeleportResult result = ObeliskService.arrive(player, target);
		if (result == TeleportResult.OK) {
			ObeliskCriteria.RETURNED.trigger(player, distance);
		}
		return result;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		NbtCompound nbt = stack.getSubNbt(KEY);
		if (nbt == null) {
			tooltip.add(Text.translatable("ender_obelisks.return.tooltip_unbound").formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.translatable("ender_obelisks.return.tooltip_bound", nbt.getString("name")).formatted(Formatting.AQUA));
			tooltip.add(Text.translatable("ender_obelisks.return.tooltip_use", ObeliskConfig.get().returnShardSeconds).formatted(Formatting.GRAY));
		}
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return boundId(stack) != null;
	}
}
