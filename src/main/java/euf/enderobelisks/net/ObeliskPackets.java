package euf.enderobelisks.net;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.ObeliskInfo;
import euf.enderobelisks.ObeliskService;
import euf.enderobelisks.ObeliskService.TeleportResult;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Channel ids and the server side of every packet. The server re-checks everything a client asks for. */
public final class ObeliskPackets {
	public static final Identifier MENU = EnderObelisks.id("menu");
	public static final Identifier TELEPORT = EnderObelisks.id("teleport");
	public static final Identifier RENAME = EnderObelisks.id("rename");
	public static final Identifier TOGGLE_PUBLIC = EnderObelisks.id("toggle_public");
	public static final Identifier UPGRADE = EnderObelisks.id("upgrade");
	public static final Identifier SET_COLOR = EnderObelisks.id("set_color");
	public static final Identifier FRIEND_ADD = EnderObelisks.id("friend_add");
	public static final Identifier FRIEND_REMOVE = EnderObelisks.id("friend_remove");

	private ObeliskPackets() {
	}

	public static void registerServerReceivers() {
		registerSimple();
		ServerPlayNetworking.registerGlobalReceiver(TELEPORT, (server, player, handler, buf, sender) -> {
			BlockPos source = buf.readBlockPos();
			UUID target = buf.readUuid();
			server.execute(() -> {
				// Starts the warmup (or jumps at once); refusals reopen the menu with the reason in the action bar
				TeleportResult result = ObeliskService.startTeleport(player, source, target);
				if (result != TeleportResult.OK) {
					player.sendMessage(Text.translatable("ender_obelisks.teleport." + result.name().toLowerCase(Locale.ROOT)), true);
					ObeliskService.openMenu(player, source);
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(RENAME, (server, player, handler, buf, sender) -> {
			BlockPos source = buf.readBlockPos();
			String name = buf.readString(ObeliskService.MAX_NAME_LENGTH * 4);
			server.execute(() -> {
				ObeliskService.rename(player, source, name);
				ObeliskService.openMenu(player, source);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(TOGGLE_PUBLIC, (server, player, handler, buf, sender) -> {
			BlockPos source = buf.readBlockPos();
			server.execute(() -> {
				ObeliskService.togglePublic(player, source);
				ObeliskService.openMenu(player, source);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(UPGRADE, (server, player, handler, buf, sender) -> {
			BlockPos source = buf.readBlockPos();
			server.execute(() -> {
				if (!ObeliskService.upgrade(player, source)) {
					player.sendMessage(Text.translatable("ender_obelisks.message.upgrade_failed"), true);
				}
				ObeliskService.openMenu(player, source);
			});
		});
	}

	private static void registerSimple() {
		ServerPlayNetworking.registerGlobalReceiver(SET_COLOR, (server, player, handler, buf, sender) -> {
			BlockPos source = buf.readBlockPos();
			int color = buf.readVarInt();
			server.execute(() -> {
				ObeliskService.setColor(player, source, color);
				ObeliskService.openMenu(player, source);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(FRIEND_ADD, (server, player, handler, buf, sender) -> {
			BlockPos source = buf.readBlockPos();
			String name = buf.readString(64);
			server.execute(() -> {
				if (!ObeliskService.addFriend(player, source, name)) {
					player.sendMessage(Text.translatable("ender_obelisks.message.friend_unknown", name), true);
				}
				ObeliskService.openMenu(player, source);
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(FRIEND_REMOVE, (server, player, handler, buf, sender) -> {
			BlockPos source = buf.readBlockPos();
			UUID friend = buf.readUuid();
			server.execute(() -> {
				ObeliskService.removeFriend(player, source, friend);
				ObeliskService.openMenu(player, source);
			});
		});
		// Every player gets their list of visible obelisks on join (map integrations need it before any menu opens)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ObeliskSync.sendTo(handler.player));
	}

	public static void sendMenu(ServerPlayerEntity player, ObeliskInfo source, boolean isOwner, int cooldownTicks, List<ObeliskInfo> visible) {
		PacketByteBuf buf = PacketByteBufs.create();
		MenuData.write(buf, source, isOwner, cooldownTicks, visible, player.getUuid());
		ServerPlayNetworking.send(player, MENU, buf);
	}
}
