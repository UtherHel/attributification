package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client: notifies that legacy Fzzy Config mappings were found
 * and offers conversion.
 *
 * @param count the number of legacy mappings found (0 = none)
 */
public record LegacyMappingsFoundPayload(int count) implements CustomPayload {

    public static final Id<LegacyMappingsFoundPayload> ID = new Id<>(
        Identifier.of(Attributifaction.MOD_ID, "legacy_mappings_found")
    );

    public static final PacketCodec<PacketByteBuf, LegacyMappingsFoundPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public LegacyMappingsFoundPayload decode(PacketByteBuf buf) {
                return new LegacyMappingsFoundPayload(buf.readVarInt());
            }

            @Override
            public void encode(PacketByteBuf buf, LegacyMappingsFoundPayload payload) {
                buf.writeVarInt(payload.count);
            }
        };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
