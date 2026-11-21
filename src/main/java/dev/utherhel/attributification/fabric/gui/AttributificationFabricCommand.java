package dev.utherhel.attributification.fabric.gui;

//? if fabric && >=1.21 {
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.gui.network.OpenGuiPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public final class AttributificationFabricCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                literal("attributification")
                    .requires(source -> source.hasPermission(2))
                    .then(literal("gui")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (source.getEntity() instanceof ServerPlayer player) {
                                ServerPlayNetworking.send(player, new OpenGuiPayload());
                                source.sendSuccess(
                                    () -> Component.translatable("command.attributification.gui.opening"),
                                    false
                                );
                            } else {
                                source.sendFailure(Component.translatable("command.attributification.gui.player_only"));
                            }
                            return 1;
                        })
                    )
            );
        });
    }
    private AttributificationFabricCommand() {}
}
//?}
//? if fabric && <1.21 {
/*import com.mojang.brigadier.CommandDispatcher;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.gui.network.OpenGuiPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public final class AttributificationFabricCommand {
    private AttributificationFabricCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommand(dispatcher));
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("attributification")
                .requires(source -> source.hasPermission(2))
                .then(literal("gui")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player != null) {
                            FriendlyByteBuf buf = PacketByteBufs.create();
                            new OpenGuiPayload().encode(buf);
                            ServerPlayNetworking.send(player, OpenGuiPayload.ID, buf);
                        }
                        ctx.getSource().sendSuccess(() ->
                            Component.translatable("command.attributification.gui.opening"), true);
                        return 1;
                    }))
                .then(literal("reload")
                    .executes(ctx -> {
                        Attributifaction.reloadAttributeUnifier();
                        ctx.getSource().sendSuccess(() ->
                            Component.literal("Reloaded " + Attributifaction.getConfig().getMappings().size() + " attribute mappings"), true);
                        return 1;
                    }))
        );
    }
}
*///?}
