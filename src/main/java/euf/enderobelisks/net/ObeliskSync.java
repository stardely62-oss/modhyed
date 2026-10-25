package euf.enderobelisks.net;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.ObeliskInfo;
import euf.enderobelisks.ObeliskRegistry;
import euf.enderobelisks.ObeliskService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Keeps every client's list of the obelisks it may see (all dimensions) up to date: sent on join and after any
 * change. Map mods (JourneyMap) draw their markers from it.
 */
public final class ObeliskSync {
	public static final Identifier SYNC = EnderObelisks.id("sync");

	public record Entry(UUID id, Identifier dimension, BlockPos pos, String name, String ownerName, boolean isOwn,
			boolean isPublic, int color) {
	}

	private ObeliskSync() {
	}

	public static void sendTo(ServerPlayerEntity player) {
		List<ObeliskInfo> visible = ObeliskRegistry.get(player.getServer()).all().stream()
				.filter(o -> ObeliskService.canUse(o, player)).toList();
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(visible.size());
		for (ObeliskInfo o : visible) {
			buf.writeUuid(o.id);
			buf.writeIdentifier(o.dimension.getValue());
			buf.writeBlockPos(o.pos);
			buf.writeString(o.name);
			buf.writeString(o.ownerName);
			buf.writeBoolean(o.owner.equals(player.getUuid()));
			buf.writeBoolean(o.isPublic);
			buf.writeVarInt(o.color);
		}
		ServerPlayNetworking.send(player, SYNC, buf);
	}

	public static void broadcast(MinecraftServer server) {
		server.getPlayerManager().getPlayerList().forEach(ObeliskSync::sendTo);
	}

	public static List<Entry> read(PacketByteBuf buf) {
		int count = buf.readVarInt();
		List<Entry> entries = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			entries.add(new Entry(buf.readUuid(), buf.readIdentifier(), buf.readBlockPos(), buf.readString(), buf.readString(),
					buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
		}
		return entries;
	}
}
