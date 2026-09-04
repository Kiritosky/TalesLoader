package plugin.talesloader.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import plugin.talesloader.Talesloader;

/**
 * Asks the server for the chunk map. The server picks the centre itself (open loader, otherwise the
 * player's own chunk), so this packet carries no position a client could tamper with.
 */
public record RequestMapC2S() implements CustomPacketPayload {
    public static final RequestMapC2S INSTANCE = new RequestMapC2S();

    public static final Type<RequestMapC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Talesloader.MODID, "request_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMapC2S> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
