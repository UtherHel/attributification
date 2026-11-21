package dev.utherhel.attributification.forge.network;

//? if neoforge && <1.20.5 {
/*import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;

// Forge's PacketDistributor.PLAYER target, for SimpleChannel.send
public final class PacketDistributor {

    public static final PacketDistributor PLAYER = new PacketDistributor();

    private PacketDistributor() {}

    public Target with(Supplier<ServerPlayer> player) {
        return new Target(player.get());
    }

    public record Target(ServerPlayer player) {}
}
*///?}
