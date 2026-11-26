package com.ruslan.glitch.network;

import com.ruslan.glitch.GlitchMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Сервер -> клиент. variant: 0 скример с лицом, 1 короткие помехи, 2 сильные помехи (финал), 3 белая вспышка (победа). */
public record ScarePayload(int durationTicks, int variant) implements CustomPayload {
    public static final CustomPayload.Id<ScarePayload> ID = new CustomPayload.Id<>(GlitchMod.id("scare"));
    public static final PacketCodec<RegistryByteBuf, ScarePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ScarePayload::durationTicks,
            PacketCodecs.VAR_INT, ScarePayload::variant,
            ScarePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
