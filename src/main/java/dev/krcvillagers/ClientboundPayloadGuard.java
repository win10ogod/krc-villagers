package dev.krcvillagers;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Dedicated servers register S2C codecs for negotiation and sending, but never execute their handlers. */
public final class ClientboundPayloadGuard {
    public static void handle(CustomPacketPayload payload,IPayloadContext context) {
        throw new IllegalStateException("Clientbound payload dispatched on a dedicated server: "+payload.type().id());
    }
}
