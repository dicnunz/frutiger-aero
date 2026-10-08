package dev.aero;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

public final class AeroNetworking {
    public record Snapshot(String json) implements CustomPayload {
        public static final Id<Snapshot> ID = new Id<>(AeroMod.id("snapshot"));
        public static final PacketCodec<RegistryByteBuf, Snapshot> CODEC = PacketCodec.ofStatic(
                (buf, value) -> buf.writeString(value.json(), 24000), buf -> new Snapshot(buf.readString(24000)));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Request(String action) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(AeroMod.id("request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.ofStatic(
                (buf, value) -> buf.writeString(value.action(), 80), buf -> new Request(buf.readString(80)));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(Snapshot.ID, Snapshot.CODEC);
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.ID, (payload, context) -> AeroGame.request(context.player(), payload.action()));
    }
    public static void send(ServerPlayerEntity player, String json) {
        if (ServerPlayNetworking.canSend(player, Snapshot.ID)) ServerPlayNetworking.send(player, new Snapshot(json));
    }
}
