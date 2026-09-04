package plugin.talesloader.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import plugin.talesloader.Talesloader;

/** Owner added or removed a trusted player in the loader GUI. */
public record TrustC2S(BlockPos pos, String name, boolean add) implements CustomPacketPayload {
    public static final Type<TrustC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Talesloader.MODID, "trust"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrustC2S> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TrustC2S::pos,
            ByteBufCodecs.stringUtf8(16), TrustC2S::name,
            ByteBufCodecs.BOOL, TrustC2S::add,
            TrustC2S::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
