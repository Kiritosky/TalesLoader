package plugin.talesloader.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import plugin.talesloader.Config;
import plugin.talesloader.Talesloader;
import plugin.talesloader.chunk.LoaderTickets;
import plugin.talesloader.data.LoadedChunkIndex;
import plugin.talesloader.menu.ChunkLoaderMenu;
import plugin.talesloader.registry.ModBlockEntities;
import plugin.talesloader.registry.ModDataComponents;
import plugin.talesloader.util.TimeFormat;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * State of a placed chunk loader: owner, trusted players, fuel and the 3x3 chunk selection.
 * <p>
 * Deliberately <b>not</b> a {@link net.minecraft.world.Container} and it exposes no item handler
 * capability, so hoppers, droppers and dispensers cannot interact with it. Fuel can only be inserted
 * by a player through the GUI.
 */
public class ChunkLoaderBlockEntity extends BlockEntity implements MenuProvider, IHaveGoggleInformation {
    /** 3x3 area, index = (dz + 1) * 3 + (dx + 1). Index 4 is the loader's own chunk. */
    public static final int GRID_SIZE = 3;
    public static final int CHUNK_COUNT = GRID_SIZE * GRID_SIZE;
    public static final int CENTER_INDEX = 4;
    public static final int CENTER_BIT = 1 << CENTER_INDEX;

    /**
     * Container data slots travel as <em>signed shorts</em> on the wire
     * ({@code ClientboundContainerSetDataPacket} writes them with {@code writeShort}), so anything
     * wider than 15 bits has to be split. Fuel therefore goes over two slots.
     */
    public static final int DATA_FUEL_LOW = 0;
    public static final int DATA_FUEL_HIGH = 1;
    public static final int DATA_MASK = 2;
    public static final int DATA_COUNT = 3;
    public static final int DATA_CHUNK_BITS = 15;
    public static final int DATA_CHUNK_MASK = (1 << DATA_CHUNK_BITS) - 1;

    private static final int SAVE_INTERVAL = 100;
    /** How often the running state is pushed to nearby clients for the goggle overlay. */
    private static final int CLIENT_SYNC_INTERVAL = 40;

    @Nullable
    private UUID ownerId;
    private String ownerName = "";
    private final Map<UUID, String> trusted = new LinkedHashMap<>();

    private long fuel;
    private int selectionMask = CENTER_BIT;
    /** Ticket state actually applied to the level; -1 means "unknown", forcing a full resync. */
    private int appliedMask = -1;
    private int saveTimer;
    private int clientSyncTimer;
    private boolean indexInitialised;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FUEL_LOW -> (int) (fuel & DATA_CHUNK_MASK);
                case DATA_FUEL_HIGH -> (int) ((fuel >>> DATA_CHUNK_BITS) & DATA_CHUNK_MASK);
                case DATA_MASK -> selectionMask;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server authoritative: the client never writes back.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHUNK_LOADER.get(), pos, state);
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChunkLoaderBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel, pos, state);
        }
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!indexInitialised) {
            reserveSelection(level);
            indexInitialised = true;
        }

        boolean wasActive = fuel > 0L;
        if (wasActive) {
            fuel = Math.max(0L, fuel - consumptionRate());
            if (fuel == 0L) {
                notifyOwner(level, Component.translatable("message.talesloader.out_of_fuel",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
        }

        boolean active = fuel > 0L;
        syncTickets(level, active);

        if (state.getValue(ChunkLoaderBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ChunkLoaderBlock.ACTIVE, active), Block.UPDATE_ALL);
            LoadedChunkIndex.get(level).setActive(pos, active);
        }

        if (wasActive && ++saveTimer >= SAVE_INTERVAL) {
            saveTimer = 0;
            setChanged();
        }

        // The goggle overlay reads the client copy of this block entity, so it needs refreshing.
        // Only while fuel is actually draining - an idle loader has nothing new to report.
        if (wasActive && ++clientSyncTimer >= CLIENT_SYNC_INTERVAL) {
            clientSyncTimer = 0;
            syncToClients();
        }
    }

    /** Fuel units consumed per tick with the current selection. */
    public int consumptionRate() {
        return Config.baseRate() + Config.perChunkRate() * activeChunkCount();
    }

    public int activeChunkCount() {
        return Integer.bitCount(selectionMask);
    }

    // ------------------------------------------------------------------ chunk tickets

    private void syncTickets(ServerLevel level, boolean active) {
        int desired = active ? selectionMask : 0;
        if (desired == appliedMask) {
            return;
        }
        ChunkPos center = new ChunkPos(worldPosition);
        for (int i = 0; i < CHUNK_COUNT; i++) {
            boolean want = (desired & (1 << i)) != 0;
            boolean had = appliedMask >= 0 && (appliedMask & (1 << i)) != 0;
            if (appliedMask >= 0 && want == had) {
                continue;
            }
            ChunkPos chunk = chunkAt(center, i);
            LoaderTickets.CONTROLLER.forceChunk(level, worldPosition, chunk.x, chunk.z, want, true);
        }
        appliedMask = desired;
    }

    public static ChunkPos chunkAt(ChunkPos center, int index) {
        return new ChunkPos(center.x + (index % GRID_SIZE) - 1, center.z + (index / GRID_SIZE) - 1);
    }

    /** Re-applies every reservation to the index, e.g. after the loader was loaded from disk. */
    private void reserveSelection(ServerLevel level) {
        LoadedChunkIndex index = LoadedChunkIndex.get(level);
        ChunkPos center = new ChunkPos(worldPosition);
        boolean active = fuel > 0L;
        int cleaned = selectionMask;
        for (int i = 0; i < CHUNK_COUNT; i++) {
            if ((selectionMask & (1 << i)) == 0) {
                continue;
            }
            if (!index.claim(chunkAt(center, i), ownerIdOrDefault(), ownerName, worldPosition, active)) {
                // Somebody else got there first (e.g. after a world edit) - give the chunk up.
                cleaned &= ~(1 << i);
            }
        }
        if (cleaned != selectionMask) {
            selectionMask = cleaned | CENTER_BIT;
            setChanged();
        }
    }

    /** Releases all tickets and reservations. Called when the block is removed. */
    public void releaseEverything(ServerLevel level) {
        ChunkPos center = new ChunkPos(worldPosition);
        for (int i = 0; i < CHUNK_COUNT; i++) {
            ChunkPos chunk = chunkAt(center, i);
            LoaderTickets.CONTROLLER.forceChunk(level, worldPosition, chunk.x, chunk.z, false, true);
        }
        appliedMask = 0;
        LoadedChunkIndex.get(level).releaseAll(worldPosition);
    }

    /**
     * Toggles one chunk of the 3x3 area.
     *
     * @return a translation key describing why the toggle failed, or {@code null} on success.
     */
    @Nullable
    public String toggleChunk(ServerLevel level, int index) {
        if (index < 0 || index >= CHUNK_COUNT) {
            return "message.talesloader.invalid_chunk";
        }
        if (index == CENTER_INDEX) {
            return "message.talesloader.center_locked";
        }
        LoadedChunkIndex chunkIndex = LoadedChunkIndex.get(level);
        ChunkPos chunk = chunkAt(new ChunkPos(worldPosition), index);
        boolean selected = (selectionMask & (1 << index)) != 0;
        if (selected) {
            selectionMask &= ~(1 << index);
            chunkIndex.release(chunk, worldPosition);
        } else {
            if (!chunkIndex.claim(chunk, ownerIdOrDefault(), ownerName, worldPosition, fuel > 0L)) {
                return "message.talesloader.chunk_taken";
            }
            selectionMask |= 1 << index;
        }
        setChanged();
        syncToClients();
        return null;
    }

    /** Owner names for the 3x3 area; an empty string means the chunk is free or ours. */
    public List<String> blockedOwners(ServerLevel level) {
        LoadedChunkIndex index = LoadedChunkIndex.get(level);
        ChunkPos center = new ChunkPos(worldPosition);
        List<String> result = new ArrayList<>(CHUNK_COUNT);
        for (int i = 0; i < CHUNK_COUNT; i++) {
            LoadedChunkIndex.Entry entry = index.get(chunkAt(center, i));
            result.add(entry == null || entry.loaderPos().equals(worldPosition) ? "" : entry.ownerName());
        }
        return result;
    }

    // ------------------------------------------------------------------ fuel

    public long getFuel() {
        return fuel;
    }

    public void setFuel(long value) {
        this.fuel = Math.max(0L, Math.min(value, Config.maxFuel()));
        setChanged();
    }

    /** Adds fuel if it fits completely; returns false when the tank has no room for the full amount. */
    public boolean addFuel(long amount) {
        long max = Config.maxFuel();
        if (amount <= 0L || fuel + amount > max) {
            return false;
        }
        fuel += amount;
        setChanged();
        return true;
    }

    public long remainingTicks() {
        int rate = consumptionRate();
        return rate <= 0 ? 0L : fuel / rate;
    }

    // ------------------------------------------------------------------ ownership

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID ownerIdOrDefault() {
        return ownerId == null ? Util.NIL_UUID : ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID id, String name) {
        this.ownerId = id;
        this.ownerName = name;
        setChanged();
        syncToClients();
    }

    public Map<UUID, String> getTrusted() {
        return Map.copyOf(trusted);
    }

    public boolean isTrusted(UUID id) {
        return trusted.containsKey(id);
    }

    public void addTrusted(UUID id, String name) {
        trusted.put(id, name);
        setChanged();
        syncToClients();
    }

    public boolean removeTrusted(String name) {
        boolean removed = trusted.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(name));
        if (removed) {
            setChanged();
            syncToClients();
        }
        return removed;
    }

    public boolean isOwner(Player player) {
        return ownerId == null || ownerId.equals(player.getUUID());
    }

    /** Owner, trusted players and - if enabled - operators may open the GUI. */
    public boolean canUse(Player player) {
        return isOwner(player) || isTrusted(player.getUUID()) || hasOpBypass(player);
    }

    /** Only the owner and operators may break the loader; trusted players may not. */
    public boolean canBreak(Player player) {
        return isOwner(player) || hasOpBypass(player);
    }

    private static boolean hasOpBypass(Player player) {
        return Config.opsBypassOwner() && player.hasPermissions(2);
    }

    private void notifyOwner(ServerLevel level, Component message) {
        if (ownerId == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.talesloader.chunk_loader");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ChunkLoaderMenu(containerId, inventory, this);
    }

    public ContainerData getData() {
        return data;
    }

    public int getSelectionMask() {
        return selectionMask;
    }

    /** Human readable remaining runtime, used by the admin listing command. */
    public String describeRuntime() {
        return TimeFormat.ticksToShort(remainingTicks());
    }

    // ------------------------------------------------------------------ client sync

    /** Pushes owner, fuel and selection to everyone tracking this block (goggle overlay). */
    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ goggle overlay

    private static LangBuilder lang() {
        return Lang.builder(Talesloader.MODID);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        lang().translate("goggles.title")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);

        boolean running = fuel > 0L;
        lang().translate("goggles.runtime", TimeFormat.ticksToShort(remainingTicks()))
                .style(running ? ChatFormatting.GOLD : ChatFormatting.RED)
                .forGoggles(tooltip, 1);
        lang().translate("goggles.chunks", activeChunkCount(), CHUNK_COUNT)
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        lang().translate("goggles.rate", consumptionRate())
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip, 1);

        // Owner and access list are only shown to people who may actually use the loader.
        if (!plugin.talesloader.client.GoggleAccess.maySeeDetails(this)) {
            return true;
        }
        if (!isPlayerSneaking) {
            lang().translate("goggles.sneak_hint")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
            return true;
        }

        lang().translate("goggles.owner", ownerName.isEmpty() ? "-" : ownerName)
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        lang().translate("goggles.trusted", trusted.isEmpty() ? "-" : String.join(", ", trusted.values()))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        return true;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.fuel = tag.getLong("fuel");
        this.selectionMask = (tag.contains("selection") ? tag.getInt("selection") : CENTER_BIT) | CENTER_BIT;
        this.ownerId = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        this.ownerName = tag.getString("ownerName");
        this.trusted.clear();
        ListTag list = tag.getList("trusted", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("id")) {
                trusted.put(entry.getUUID("id"), entry.getString("name"));
            }
        }
        this.appliedMask = -1;
        this.indexInitialised = false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("fuel", fuel);
        tag.putInt("selection", selectionMask);
        if (ownerId != null) {
            tag.putUUID("owner", ownerId);
        }
        tag.putString("ownerName", ownerName);
        ListTag list = new ListTag();
        trusted.forEach((id, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putString("name", name);
            list.add(entry);
        });
        tag.put("trusted", list);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (fuel > 0L) {
            builder.set(ModDataComponents.STORED_FUEL.get(), fuel);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Long stored = input.get(ModDataComponents.STORED_FUEL.get());
        if (stored != null) {
            this.fuel = Math.max(0L, Math.min(stored, Config.maxFuel()));
        }
    }
}
