package dev.utherhel.attributification.forge.network;

//? if neoforge && <1.20.5 {
/*import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

// The part of Forge's SimpleChannel that the Forge payload handler uses, over NeoForge 20.4 payloads:
// every message becomes a payload "<channel path>/<index>" that wraps the message object.
public final class SimpleChannel {

    private final ResourceLocation name;
    private final String version;
    private final List<Message<?>> messages = new ArrayList<>();
    private final Map<Class<?>, Message<?>> byType = new HashMap<>();

    SimpleChannel(ResourceLocation name, String version) {
        this.name = name;
        this.version = version;
    }

    public <T> void registerMessage(
        int index,
        Class<T> type,
        BiConsumer<T, FriendlyByteBuf> encoder,
        Function<FriendlyByteBuf, T> decoder,
        BiConsumer<T, Supplier<NetworkEvent.Context>> handler
    ) {
        ResourceLocation id = new ResourceLocation(name.getNamespace(), name.getPath() + "/" + index);
        Message<T> message = new Message<>(id, encoder, decoder, handler);
        messages.add(message);
        byType.put(type, message);
    }

    // Registers all messages; call from RegisterPayloadHandlerEvent on the mod event bus
    public void register(RegisterPayloadHandlerEvent event) {
        IPayloadRegistrar registrar = event.registrar(name.getNamespace()).versioned(version).optional();
        for (Message<?> message : messages) {
            message.register(registrar);
        }
    }

    public <T> void send(PacketDistributor.Target target, T message) {
        net.neoforged.neoforge.network.PacketDistributor.PLAYER.with(target.player()).send(wrap(message));
    }

    public <T> void sendToServer(T message) {
        net.neoforged.neoforge.network.PacketDistributor.SERVER.noArg().send(wrap(message));
    }

    @SuppressWarnings("unchecked")
    private <T> Packet<T> wrap(T message) {
        Message<T> type = (Message<T>) byType.get(message.getClass());
        if (type == null) throw new IllegalArgumentException("Unregistered message " + message.getClass().getName());
        return new Packet<>(type, message);
    }

    record Packet<T>(Message<T> message, T value) implements CustomPacketPayload {
        @Override
        public void write(FriendlyByteBuf buf) {
            message.encoder().accept(value, buf);
        }

        @Override
        public ResourceLocation id() {
            return message.id();
        }
    }

    record Message<T>(
        ResourceLocation id,
        BiConsumer<T, FriendlyByteBuf> encoder,
        Function<FriendlyByteBuf, T> decoder,
        BiConsumer<T, Supplier<NetworkEvent.Context>> handler
    ) {
        void register(IPayloadRegistrar registrar) {
            registrar.<Packet<T>>play(
                id,
                buf -> new Packet<>(this, decoder.apply(buf)),
                (packet, context) -> {
                    NetworkEvent.Context ctx = new NetworkEvent.Context(context);
                    handler.accept(packet.value(), () -> ctx);
                }
            );
        }
    }
}
*///?}
