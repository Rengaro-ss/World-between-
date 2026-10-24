package com.worldbetween.world;

import com.worldbetween.faction.FactionType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of the world chronicle. Stored as a translation key so it shows in the reader's language.
 */
public record ChronicleEntry(long day, FactionType faction, String key, List<String> args) {
	public MutableText toText() {
		MutableText text = Text.translatable("chronicle.worldbetween.day", day).formatted(Formatting.GRAY);
		if (faction != null) {
			text.append(" ").append(faction.displayName()).append(Text.literal(":").formatted(Formatting.GRAY));
		}
		return text.append(" ").append(Text.translatable(key, args.toArray()).formatted(Formatting.WHITE));
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putLong("Day", day);
		if (faction != null) {
			nbt.putString("Faction", faction.id());
		}
		nbt.putString("Key", key);
		NbtList argsNbt = new NbtList();
		args.forEach(arg -> argsNbt.add(NbtString.of(arg)));
		nbt.put("Args", argsNbt);
		return nbt;
	}

	public static ChronicleEntry fromNbt(NbtCompound nbt) {
		List<String> args = new ArrayList<>();
		NbtList argsNbt = nbt.getList("Args", NbtElement.STRING_TYPE);
		for (int i = 0; i < argsNbt.size(); i++) {
			args.add(argsNbt.getString(i));
		}
		FactionType faction = nbt.contains("Faction") ? FactionType.byId(nbt.getString("Faction")) : null;
		return new ChronicleEntry(nbt.getLong("Day"), faction, nbt.getString("Key"), List.copyOf(args));
	}
}
