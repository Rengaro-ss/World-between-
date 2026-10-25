package com.worldbetween.world;

import com.mojang.serialization.Codec;
import com.worldbetween.WorldBetween;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Data stored on chunks.
 */
public final class WorldBetweenAttachments {
	/**
	 * How far each mine has been carved into this chunk, keyed by mine. Lets a chunk catch up on
	 * everything dug while it was unloaded, and never re-dig what a player has since changed.
	 */
	public static final AttachmentType<Map<String, Integer>> MINE_PROGRESS = AttachmentRegistry.createPersistent(
			Identifier.of(WorldBetween.MOD_ID, "mine_progress"), Codec.unboundedMap(Codec.STRING, Codec.INT));

	private WorldBetweenAttachments() {
	}

	public static void register() {
		// Loading the class registers the attachment types.
	}
}
