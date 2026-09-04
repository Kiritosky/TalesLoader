package plugin.talesloader.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import plugin.talesloader.Talesloader;

/** Player toggled one of the 3x3 chunks in the loader GUI. */
public record ToggleChunkC2S(BlockPos pos, int index) implements CustomPacketPayload {
    public static final Type<ToggleChunkC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Talesloader.MODID, "toggle_chunk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleChunkC2S> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ToggleChunkC2S::pos,
            ByteBufCodecs.VAR_INT, ToggleChunkC2S::index,
            ToggleChunkC2S::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
