package plugin.talesloader.net;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import plugin.talesloader.Config;
import plugin.talesloader.Talesloader;
import plugin.talesloader.block.ChunkLoaderBlockEntity;
import plugin.talesloader.client.ClientPayloadHandler;
import plugin.talesloader.data.LoadedChunkIndex;
import plugin.talesloader.menu.ChunkLoaderMenu;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = Talesloader.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    private ModNetwork() {
    }

    @SubscribeEvent
    static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(ToggleChunkC2S.TYPE, ToggleChunkC2S.STREAM_CODEC, ModNetwork::handleToggle);
        registrar.playToServer(TrustC2S.TYPE, TrustC2S.STREAM_CODEC, ModNetwork::handleTrust);
        registrar.playToServer(RequestMapC2S.TYPE, RequestMapC2S.STREAM_CODEC, ModNetwork::handleMapRequest);

        registrar.playToClient(LoaderInfoS2C.TYPE, LoaderInfoS2C.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleLoaderInfo(payload, context));
        registrar.playToClient(MapDataS2C.TYPE, MapDataS2C.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleMapData(payload, context));
    }

    // ------------------------------------------------------------------ server side

    private static void handleToggle(ToggleChunkC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ChunkLoaderBlockEntity loader = resolveOpenLoader(context.player(), payload.pos());
            if (loader == null || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            String error = loader.toggleChunk(level, payload.index());
            if (error != null) {
                player.displayClientMessage(Component.translatable(error), true);
            }
            sendLoaderInfo(player, loader);
        });
    }

    private static void handleTrust(TrustC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ChunkLoaderBlockEntity loader = resolveOpenLoader(context.player(), payload.pos());
            if (loader == null || !(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!loader.isOwner(player) && !(Config.opsBypassOwner() && player.hasPermissions(2))) {
                player.displayClientMessage(Component.translatable("message.talesloader.owner_only"), true);
                return;
            }
            String name = payload.name().trim();
            if (name.isEmpty() || name.length() > 16) {
                return;
            }
            if (payload.add()) {
                Optional<GameProfile> profile = player.server.getProfileCache() == null
                        ? Optional.empty()
                        : player.server.getProfileCache().get(name);
                if (profile.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.talesloader.unknown_player", name), true);
                    return;
                }
                loader.addTrusted(profile.get().getId(), profile.get().getName());
            } else if (!loader.removeTrusted(name)) {
                return;
            }
            sendLoaderInfo(player, loader);
        });
    }

    private static void handleMapRequest(RequestMapC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ChunkPos center = player.containerMenu instanceof ChunkLoaderMenu menu
                    ? new ChunkPos(menu.getPos())
                    : player.chunkPosition();
            sendMap(player, center);
        });
    }

    /** Resolves the loader the player currently has open, guarding against spoofed positions. */
    @Nullable
    private static ChunkLoaderBlockEntity resolveOpenLoader(@Nullable Player player, BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(serverPlayer.containerMenu instanceof ChunkLoaderMenu menu)
                || !menu.getPos().equals(pos)) {
            return null;
        }
        return serverPlayer.level().getBlockEntity(pos) instanceof ChunkLoaderBlockEntity loader
                && loader.canUse(serverPlayer) ? loader : null;
    }

    public static void sendLoaderInfo(ServerPlayer player, ChunkLoaderBlockEntity loader) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<String> trusted = new ArrayList<>(loader.getTrusted().values());
        PacketDistributor.sendToPlayer(player, new LoaderInfoS2C(loader.getBlockPos(), loader.getOwnerName(),
                loader.blockedOwners(level), trusted));
    }

    public static void sendMap(ServerPlayer player, ChunkPos center) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int radius = Config.mapRadius();
        List<MapDataS2C.Claim> claims = new ArrayList<>();
        for (LoadedChunkIndex.Positioned positioned : LoadedChunkIndex.get(level).around(center, radius)) {
            LoadedChunkIndex.Entry entry = positioned.entry();
            claims.add(new MapDataS2C.Claim(positioned.chunkX(), positioned.chunkZ(),
                    entry.ownerId(), entry.ownerName(), entry.active()));
        }
        PacketDistributor.sendToPlayer(player, new MapDataS2C(center.x, center.z, radius, claims));
    }
}
