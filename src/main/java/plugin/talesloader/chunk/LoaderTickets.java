package plugin.talesloader.chunk;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import plugin.talesloader.Talesloader;
import plugin.talesloader.block.ChunkLoaderBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the NeoForge chunk ticket controller used by every chunk loader block.
 * Tickets are keyed by the loader's {@link BlockPos} and survive server restarts.
 */
@EventBusSubscriber(modid = Talesloader.MODID)
public final class LoaderTickets {
    /** Ticks to wait after a level loads before orphaned tickets are checked. */
    private static final int GRACE_TICKS = 200;

    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(Talesloader.MODID, "chunk_loader"),
            LoaderTickets::collectTickets);

    private static final Map<ResourceKey<Level>, PendingValidation> PENDING = new ConcurrentHashMap<>();

    private LoaderTickets() {
    }

    @EventBusSubscriber(modid = Talesloader.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        static void registerControllers(final RegisterTicketControllersEvent event) {
            event.register(CONTROLLER);
        }
    }

    /**
     * Runs while the level is still loading, so it must not touch blocks - reading a block state here
     * would synchronously generate chunks during startup, which is exactly where heavy worldgen mods
     * stall or dead-lock. The actual check is deferred to {@link #onLevelTick}.
     */
    private static void collectTickets(ServerLevel level, TicketHelper helper) {
        if (helper.getBlockTickets().isEmpty()) {
            return;
        }
        List<Owner> owners = new ArrayList<>();
        helper.getBlockTickets().forEach((pos, tickets) -> owners.add(new Owner(pos.immutable(),
                new LongOpenHashSet(tickets.nonTicking()), new LongOpenHashSet(tickets.ticking()))));
        PENDING.put(level.dimension(), new PendingValidation(owners, GRACE_TICKS));
    }

    /**
     * Drops tickets whose loader block no longer exists (world edits, crashes, restored backups), so the
     * server never keeps chunks alive without a visible cause. Runs once, after the level is up and the
     * forced chunks have had time to load.
     */
    @SubscribeEvent
    static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PendingValidation pending = PENDING.get(level.dimension());
        if (pending == null) {
            return;
        }
        if (--pending.remainingTicks > 0) {
            return;
        }
        PENDING.remove(level.dimension());

        for (Owner owner : pending.owners) {
            if (level.getBlockState(owner.pos).getBlock() instanceof ChunkLoaderBlock) {
                continue;
            }
            release(level, owner.pos, owner.nonTicking, false);
            release(level, owner.pos, owner.ticking, true);
            Talesloader.LOGGER.info("Removed orphaned chunk loader tickets at {} in {}",
                    owner.pos, level.dimension().location());
        }
    }

    private static void release(ServerLevel level, BlockPos owner, LongSet chunks, boolean ticking) {
        chunks.forEach((long packed) -> {
            ChunkPos chunk = new ChunkPos(packed);
            CONTROLLER.forceChunk(level, owner, chunk.x, chunk.z, false, ticking);
        });
    }

    private record Owner(BlockPos pos, LongSet nonTicking, LongSet ticking) {
    }

    private static final class PendingValidation {
        private final List<Owner> owners;
        private int remainingTicks;

        private PendingValidation(List<Owner> owners, int remainingTicks) {
            this.owners = owners;
            this.remainingTicks = remainingTicks;
        }
    }
}
