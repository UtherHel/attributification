package dev.utherhel.attributification.forge.network;

//? if neoforge && <1.20.5 {
/*import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;

// Forge's NetworkEvent.Context over a NeoForge 20.4 payload context
public final class NetworkEvent {

    private NetworkEvent() {}

    public static final class Context {

        private final PlayPayloadContext context;

        Context(PlayPayloadContext context) {
            this.context = context;
        }

        // Runs the task on the main thread of the receiving side
        public void enqueueWork(Runnable task) {
            context.workHandler().submitAsync(task);
        }

        public NetworkDirection getDirection() {
            return context.flow() == PacketFlow.SERVERBOUND
                ? NetworkDirection.PLAY_TO_SERVER
                : NetworkDirection.PLAY_TO_CLIENT;
        }

        public ServerPlayer getSender() {
            return context.player().orElse(null) instanceof ServerPlayer player ? player : null;
        }

        public void setPacketHandled(boolean handled) {
            // NeoForge payloads are always consumed by their handler
        }
    }
}
*///?}
