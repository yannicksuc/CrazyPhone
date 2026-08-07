package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.nbt.CompoundTag;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.data.ConversationSavedData;

import java.util.List;

/**
 * Returns the most recent page of a conversation's messages (oldest-first), instead of the old
 * behaviour of returning the entire message history as a single NBT ListTag. Full-history NBT
 * blobs are exactly what made the old CrazythingsModVariables#crazyPhoneMessages sync grow forever
 * and crash the server on player connect - callers that need older messages should page further
 * back via ConversationSavedData#getPage / the ConversationRequestPacket flow instead of calling
 * this repeatedly with no bound.
 */
public class CrazyPhoneGetMessagesProcedure {
	public static List<CompoundTag> execute(LevelAccessor world, String id) {
		if (id == null)
			return List.of();
		return ConversationSavedData.get(world).getPage(id, 0, Config.maxMessagesSentPerRequest);
	}
}
