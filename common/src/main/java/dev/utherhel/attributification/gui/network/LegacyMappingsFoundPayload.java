package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.Attributifaction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client: notifies that legacy Fzzy Config mappings were found
 * and offers conversion.
 *
 * @param count the number of legacy mappings found (0 = none)
 */
public record LegacyMappingsFoundPayload(int count) implements CustomPacketPayload {

    public static final Type<LegacyMappingsFoundPayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Attributifaction.MOD_ID, "legacy_mappings_found")
    );

    public static final StreamCodec<FriendlyByteBuf, LegacyMappingsFoundPayload> CODEC =
        new StreamCodec<>() {
            @Override
            public LegacyMappingsFoundPayload decode(FriendlyByteBuf buf) {
                return new LegacyMappingsFoundPayload(buf.readVarInt());
            }

            @Override
            public void encode(FriendlyByteBuf buf, LegacyMappingsFoundPayload payload) {
                buf.writeVarInt(payload.count);
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
