package dev.utherhel.attributification.forge.gui;

//? if forge || (neoforge && <1.20.5) {
/*import com.mojang.brigadier.CommandDispatcher;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.gui.network.OpenGuiPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
//? if forge {
/^import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
^///?} else {
import dev.utherhel.attributification.forge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
//?}

import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid = Attributifaction.MOD_ID)
public final class AttributificationCommand {
    private AttributificationCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            literal("attributification")
                .requires(source -> source.hasPermission(2))
                .then(literal("gui")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayer();
                        if (player != null) {
                            AttributificationPayloadHandler.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> player),
                                new OpenGuiPayload()
                            );
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
