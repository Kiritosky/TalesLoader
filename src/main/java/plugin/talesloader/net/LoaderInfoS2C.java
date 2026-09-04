package plugin.talesloader.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import plugin.talesloader.Talesloader;

import java.util.List;

/**
 * Loader details that are not worth a container data slot: who owns it, who may use it and which of
 * the nine chunks are already taken by somebody else.
 */
public record LoaderInfoS2C(BlockPos pos, String ownerName, List<String> blockedOwners, List<String> trusted)
        implements CustomPacketPayload {
    public static final Type<LoaderInfoS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Talesloader.MODID, "loader_info"));

    private static final StreamCodec<ByteBuf, List<String>> NAME_LIST =
            ByteBufCodecs.stringUtf8(32).apply(ByteBufCodecs.list(64));

    public static final StreamCodec<RegistryFriendlyByteBuf, LoaderInfoS2C> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LoaderInfoS2C::pos,
            ByteBufCodecs.stringUtf8(32), LoaderInfoS2C::ownerName,
            NAME_LIST, LoaderInfoS2C::blockedOwners,
            NAME_LIST, LoaderInfoS2C::trusted,
            LoaderInfoS2C::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
