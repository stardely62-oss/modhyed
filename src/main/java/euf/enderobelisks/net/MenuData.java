package euf.enderobelisks.net;

import euf.enderobelisks.ObeliskInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

/** What the obelisk menu shows: the obelisk you stand at, your cooldown, and every obelisk you may use here. */
public record MenuData(BlockPos sourcePos, UUID sourceId, String name, boolean isOwner, boolean isPublic, int tier,
		int cooldownTicks, List<Entry> entries) {

	public record Entry(UUID id, BlockPos pos, String name, String ownerName, boolean isOwn, boolean isPublic, int tier) {
	}

	public static void write(PacketByteBuf buf, ObeliskInfo source, boolean isOwner, int cooldownTicks, List<ObeliskInfo> visible,
			UUID viewer) {
		buf.writeBlockPos(source.pos);
		buf.writeUuid(source.id);
		buf.writeString(source.name);
		buf.writeBoolean(isOwner);
		buf.writeBoolean(source.isPublic);
		buf.writeVarInt(source.tier);
		buf.writeVarInt(cooldownTicks);
		buf.writeVarInt(visible.size());
		for (ObeliskInfo o : visible) {
			buf.writeUuid(o.id);
			buf.writeBlockPos(o.pos);
			buf.writeString(o.name);
			buf.writeString(o.ownerName);
			buf.writeBoolean(o.owner.equals(viewer));
			buf.writeBoolean(o.isPublic);
			buf.writeVarInt(o.tier);
		}
	}

	public static MenuData read(PacketByteBuf buf) {
		BlockPos pos = buf.readBlockPos();
		UUID id = buf.readUuid();
		String name = buf.readString();
		boolean isOwner = buf.readBoolean();
		boolean isPublic = buf.readBoolean();
		int tier = buf.readVarInt();
		int cooldown = buf.readVarInt();
		int count = buf.readVarInt();
		List<Entry> entries = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			entries.add(new Entry(buf.readUuid(), buf.readBlockPos(), buf.readString(), buf.readString(),
					buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
		}
		return new MenuData(pos, id, name, isOwner, isPublic, tier, cooldown, entries);
	}
}
