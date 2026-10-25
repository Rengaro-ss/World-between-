package com.worldbetween.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.worldbetween.faction.kament.Focus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;

/**
 * A charm of stone the Ancients cut into an amulet. One for each thing the council can set the people to;
 * an amulet works while it is anywhere in the inventory.
 */
public record StoneCharm(Kind kind, int level) {
	public enum Kind {
		/** Of stone: the body takes blows as rock does. */
		FIRM("firm", Focus.STONE),
		/** Of the chisel: hands that work faster. */
		CHISEL("chisel", Focus.CARVING),
		/** Of the deep: eyes for the dark, and at the second cut, skin for the fire below. */
		DEEP("deep", Focus.DEPTH),
		/** Of memory: a body that remembers being whole. */
		MEMORY("memory", Focus.MEMORY),
		/** Of the ward: a shell of stone that comes back after a while. */
		WARD("ward", Focus.WARD);

		private final String id;
		private final Focus focus;

		Kind(String id, Focus focus) {
			this.id = id;
			this.focus = focus;
		}

		public String id() {
			return id;
		}

		public Focus focus() {
			return focus;
		}

		public Text displayName() {
			return Text.translatable("charm.worldbetween." + id);
		}

		public static Kind of(Focus focus) {
			for (Kind kind : values()) {
				if (kind.focus == focus) {
					return kind;
				}
			}
			return FIRM;
		}

		public static Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id.equals(id)) {
					return kind;
				}
			}
			return FIRM;
		}
	}

	public static final Codec<StoneCharm> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.xmap(Kind::byId, Kind::id).fieldOf("kind").forGetter(StoneCharm::kind),
			Codec.INT.fieldOf("level").forGetter(StoneCharm::level)
	).apply(instance, StoneCharm::new));

	public static final PacketCodec<ByteBuf, StoneCharm> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.STRING.xmap(Kind::byId, Kind::id), StoneCharm::kind,
			PacketCodecs.VAR_INT, StoneCharm::level,
			StoneCharm::new);
}
