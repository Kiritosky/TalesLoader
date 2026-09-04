package plugin.talesloader.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import plugin.talesloader.Talesloader;
import plugin.talesloader.block.ChunkLoaderBlockEntity;
import plugin.talesloader.data.LoadedChunkIndex;
import plugin.talesloader.net.ModNetwork;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = Talesloader.MODID)
public final class TalesLoaderCommands {
    private TalesLoaderCommands() {
    }

    @SubscribeEvent
    static void register(final RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("talesloader")
                .then(Commands.literal("map").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ModNetwork.sendMap(player, player.chunkPosition());
                    return 1;
                }))
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> listLoaders(context.getSource())));
        event.getDispatcher().register(root);
    }

    private static int listLoaders(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Map<BlockPos, LoadedChunkIndex.Entry> loaders = new LinkedHashMap<>();
        Map<BlockPos, Integer> chunkCounts = new LinkedHashMap<>();
        LoadedChunkIndex.get(level).all().forEach((chunk, entry) -> {
            loaders.putIfAbsent(entry.loaderPos(), entry);
            chunkCounts.merge(entry.loaderPos(), 1, Integer::sum);
        });

        if (loaders.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.talesloader.list_empty"), false);
            return 0;
        }

        loaders.forEach((pos, entry) -> {
            String runtime = level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity loader
                    ? loader.describeRuntime()
                    : "?";
            source.sendSuccess(() -> Component.translatable("command.talesloader.list_entry",
                    entry.ownerName().isEmpty() ? "?" : entry.ownerName(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    chunkCounts.getOrDefault(pos, 0),
                    runtime,
                    Component.translatable(entry.active()
                            ? "command.talesloader.state_active"
                            : "command.talesloader.state_idle")), false);
        });
        return loaders.size();
    }
}
