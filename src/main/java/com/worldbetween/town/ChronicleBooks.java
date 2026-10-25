package com.worldbetween.town;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.world.ChronicleEntry;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Every library keeps the chronicle of its own settlement on a lectern, and keeps it up to date.
 */
public final class ChronicleBooks {
	private static final int MAX_PAGES = 100;
	private static final double SEARCH_RADIUS = 120;

	private ChronicleBooks() {
	}

	/** Puts (or refreshes) the chronicle of the nearest settlement on the lectern at {@code pos}. */
	public static void place(ServerWorld world, BlockPos pos) {
		WorldBetweenState state = WorldBetweenState.get(world.getServer());
		Faction owner = null;
		Settlement nearest = null;
		double best = SEARCH_RADIUS * SEARCH_RADIUS;
		for (Faction faction : state.factions()) {
			for (Settlement settlement : faction.settlements()) {
				double distance = settlement.pos().getSquaredDistance(pos);
				if (distance < best) {
					best = distance;
					nearest = settlement;
					owner = faction;
				}
			}
		}
		if (nearest != null) {
			update(world, pos, owner, nearest, state.chronicle());
		}
	}

	public static void update(ServerWorld world, BlockPos pos, Faction faction, Settlement settlement, List<ChronicleEntry> chronicle) {
		BlockState lectern = world.getBlockState(pos);
		if (!lectern.isOf(Blocks.LECTERN) || !(world.getBlockEntity(pos) instanceof LecternBlockEntity entity)) {
			return;
		}
		List<RawFilteredPair<Text>> pages = new ArrayList<>();
		pages.add(RawFilteredPair.of(Text.translatable("book.worldbetween.chronicle.title", settlement.name())
				.append("\n\n")
				.append(Text.translatable("book.worldbetween.chronicle.intro", faction.type().displayName(), settlement.foundedDay()))));
		for (ChronicleEntry entry : chronicle) {
			if (pages.size() >= MAX_PAGES) {
				break;
			}
			if (entry.faction() == faction.type() && entry.args().contains(settlement.name())) {
				pages.add(RawFilteredPair.of(Text.translatable("chronicle.worldbetween.day", entry.day())
						.append("\n\n").append(Text.translatable(entry.key(), entry.args().toArray()))));
			}
		}
		ItemStack current = entity.getBook();
		WrittenBookContentComponent existing = current.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
		if (existing != null && existing.pages().size() == pages.size()) {
			return;
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
				RawFilteredPair.of(settlement.name()), settlement.name(), 0, pages, true));
		if (!lectern.get(LecternBlock.HAS_BOOK)) {
			world.setBlockState(pos, lectern.with(LecternBlock.HAS_BOOK, true), net.minecraft.block.Block.NOTIFY_LISTENERS);
		}
		entity.setBook(book);
		entity.markDirty();
	}
}
