package dev.utherhel.attributification.forge.gui;

import com.mojang.brigadier.CommandDispatcher;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.gui.network.OpenGuiPayload;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import static net.minecraft.server.command.CommandManager.literal;

@Mod.EventBusSubscriber(modid = Attributifaction.MOD_ID)
public final class AttributificationCommand {
    private AttributificationCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<ServerCommandSource> dispatcher = event.getDispatcher();

        dispatcher.register(
            literal("attributification")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("gui")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player != null) {
                            AttributificationPayloadHandler.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> player),
                                new OpenGuiPayload()
                            );
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
