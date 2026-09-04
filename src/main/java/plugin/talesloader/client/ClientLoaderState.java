package plugin.talesloader.client;

import net.minecraft.core.BlockPos;
import plugin.talesloader.block.ChunkLoaderBlockEntity;

import java.util.Collections;
import java.util.List;

/** Client-side mirror of the extra loader details that arrive via {@link plugin.talesloader.net.LoaderInfoS2C}. */
public final class ClientLoaderState {
    private static BlockPos pos = BlockPos.ZERO;
    private static String ownerName = "";
    private static List<String> blockedOwners = Collections.nCopies(ChunkLoaderBlockEntity.CHUNK_COUNT, "");
    private static List<String> trusted = List.of();

    private ClientLoaderState() {
    }

    public static void update(BlockPos loaderPos, String owner, List<String> blocked, List<String> trustedNames) {
        pos = loaderPos;
        ownerName = owner;
        blockedOwners = blocked.size() == ChunkLoaderBlockEntity.CHUNK_COUNT
                ? List.copyOf(blocked)
                : Collections.nCopies(ChunkLoaderBlockEntity.CHUNK_COUNT, "");
        trusted = List.copyOf(trustedNames);
    }

    public static boolean matches(BlockPos loaderPos) {
        return pos.equals(loaderPos);
    }

    public static String ownerName() {
        return ownerName;
    }

    /** Empty string when the chunk is free or belongs to this loader. */
    public static String blockedBy(int index) {
        return index >= 0 && index < blockedOwners.size() ? blockedOwners.get(index) : "";
    }

    public static List<String> trusted() {
        return trusted;
    }
}
