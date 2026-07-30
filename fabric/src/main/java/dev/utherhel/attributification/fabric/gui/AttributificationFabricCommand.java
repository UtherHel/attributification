package dev.utherhel.attributification.fabric.gui;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.gui.network.OpenGuiPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;

public final class AttributificationFabricCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                literal("attributification")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(literal("gui")
                        .executes(ctx -> {
                            ServerCommandSource source = ctx.getSource();
                            if (source.getEntity() instanceof ServerPlayerEntity player) {
                                ServerPlayNetworking.send(player, new OpenGuiPayload());
                                source.sendFeedback(
                                    () -> Text.translatable("command.attributification.gui.opening"),
                                    false
                                );
                            } else {
                                source.sendError(Text.translatable("command.attributification.gui.player_only"));
                            }
                            return 1;
                        })
                    )
            );
        });
    }
    private AttributificationFabricCommand() {}
}
