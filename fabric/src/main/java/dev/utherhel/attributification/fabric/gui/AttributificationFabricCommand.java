package dev.utherhel.attributification.fabric.gui;

import com.mojang.brigadier.CommandDispatcher;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.gui.network.OpenGuiPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class AttributificationFabricCommand {
    private AttributificationFabricCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommand(dispatcher));
    }

    private static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("attributification")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("gui")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player != null) {
                            PacketByteBuf buf = PacketByteBufs.create();
                            new OpenGuiPayload().encode(buf);
                            ServerPlayNetworking.send(player, OpenGuiPayload.ID, buf);
                        }
                        ctx.getSource().sendFeedback(() ->
                            Text.translatable("command.attributification.gui.opening"), true);
                        return 1;
                    }))
                .then(literal("reload")
                    .executes(ctx -> {
                        Attributifaction.reloadAttributeUnifier();
                        ctx.getSource().sendFeedback(() ->
                            Text.literal("Reloaded " + Attributifaction.getConfig().getMappings().size() + " attribute mappings"), true);
                        return 1;
                    }))
        );
    }
}
