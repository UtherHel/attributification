package dev.utherhel.attributification.neoforge.gui;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.gui.network.OpenGuiPayload;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers the {@code /attributification gui} command on the NeoForge server.
 * <p>
 * Sends an {@link OpenGuiPayload} to the executing player, which the client
 * handles by opening the mappings editor screen.
 */
@EventBusSubscriber(modid = Attributifaction.MOD_ID)
public final class AttributificationCommand {

    private AttributificationCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                literal("attributification")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("gui")
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    if (source.getEntity() instanceof ServerPlayerEntity player) {
                                        PacketDistributor.sendToPlayer(player, new OpenGuiPayload());
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
    }
}
