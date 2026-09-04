package plugin.talesloader.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-level record of which chunk is reserved by which chunk loader.
 * <p>
 * Two purposes: a chunk can only ever be reserved by a single loader (first one wins), and the
 * chunk map GUI reads its data from here. This is <em>not</em> a claim system - it only tracks
 * force-loading, and an entry disappears as soon as the loader deselects the chunk or is broken.
 */
public class LoadedChunkIndex extends SavedData {
    private static final String NAME = "talesloader_chunk_index";

    private final Map<Long, Entry> entries = new HashMap<>();

    public static LoadedChunkIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LoadedChunkIndex::new, LoadedChunkIndex::load, DataFixTypes.LEVEL), NAME);
    }

    private static LoadedChunkIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        LoadedChunkIndex index = new LoadedChunkIndex();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("owner")) {
                continue;
            }
            index.entries.put(entry.getLong("chunk"), new Entry(
                    entry.getUUID("owner"),
                    entry.getString("ownerName"),
                    BlockPos.of(entry.getLong("loader")),
                    entry.getBoolean("active")));
        }
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        entries.forEach((chunk, entry) -> {
            CompoundTag out = new CompoundTag();
            out.putLong("chunk", chunk);
            out.putUUID("owner", entry.ownerId());
            out.putString("ownerName", entry.ownerName());
            out.putLong("loader", entry.loaderPos().asLong());
            out.putBoolean("active", entry.active());
            list.add(out);
        });
        tag.put("entries", list);
        return tag;
    }

    /**
     * Reserves a chunk for the given loader.
     *
     * @return {@code true} if the chunk is now reserved by this loader (also when it already was).
     */
    public boolean claim(ChunkPos chunk, UUID ownerId, String ownerName, BlockPos loaderPos, boolean active) {
        Entry existing = entries.get(chunk.toLong());
        if (existing != null && !existing.loaderPos().equals(loaderPos)) {
            return false;
        }
        entries.put(chunk.toLong(), new Entry(ownerId, ownerName, loaderPos, active));
        setDirty();
        return true;
    }

    public void release(ChunkPos chunk, BlockPos loaderPos) {
        Entry existing = entries.get(chunk.toLong());
        if (existing != null && existing.loaderPos().equals(loaderPos)) {
            entries.remove(chunk.toLong());
            setDirty();
        }
    }

    public void releaseAll(BlockPos loaderPos) {
        if (entries.values().removeIf(entry -> entry.loaderPos().equals(loaderPos))) {
            setDirty();
        }
    }

    /** Flags every chunk of a loader as currently force-loading (or idle, when it ran out of fuel). */
    public void setActive(BlockPos loaderPos, boolean active) {
        boolean changed = false;
        for (Map.Entry<Long, Entry> entry : entries.entrySet()) {
            Entry value = entry.getValue();
            if (value.loaderPos().equals(loaderPos) && value.active() != active) {
                entry.setValue(new Entry(value.ownerId(), value.ownerName(), value.loaderPos(), active));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public Entry get(ChunkPos chunk) {
        return entries.get(chunk.toLong());
    }

    /** All reservations within {@code radius} chunks of {@code center}, for the map GUI. */
    public List<Positioned> around(ChunkPos center, int radius) {
        List<Positioned> result = new ArrayList<>();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                Entry entry = entries.get(ChunkPos.asLong(x, z));
                if (entry != null) {
                    result.add(new Positioned(x, z, entry));
                }
            }
        }
        return result;
    }

    /** Every reservation in this level, used by the admin listing command. */
    public Map<Long, Entry> all() {
        return Map.copyOf(entries);
    }

    public record Entry(UUID ownerId, String ownerName, BlockPos loaderPos, boolean active) {
    }

    public record Positioned(int chunkX, int chunkZ, Entry entry) {
    }
}
