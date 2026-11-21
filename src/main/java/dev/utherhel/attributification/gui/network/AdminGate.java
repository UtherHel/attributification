package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.AttributificationLogger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.11 {
/*import net.minecraft.server.permissions.Permissions;
*///?}

/**
 * Server-side permission check for the mapping editor's client-to-server requests.
 * <p>
 * The editor is opened by an operator-only command, but its packets can be sent by any connected
 * client, so every request that reads, writes, deletes or converts mapping files checks the
 * sender again here, with the same level as the command.
 */
public final class AdminGate {

    /** Game master level (2), the level of {@code /gamerule} and similar commands. */
    public static final int PERMISSION_LEVEL = 2;

    private AdminGate() {
    }

    public static boolean isAllowed(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (isOperator(player)) {
            return true;
        }
        AttributificationLogger.warn("Ignoring mapping editor request from " + player.getName().getString()
            + ": operator permission level " + PERMISSION_LEVEL + " required");
        return false;
    }

    public static boolean isOperator(ServerPlayer player) {
        //? if >=1.21.11 {
        /*return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        *///?} else {
        return player.hasPermissions(PERMISSION_LEVEL);
        //?}
    }

    /** For {@code .requires(...)} of the editor command. */
    public static boolean isOperator(CommandSourceStack source) {
        //? if >=1.21.11 {
        /*return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        *///?} else {
        return source.hasPermission(PERMISSION_LEVEL);
        //?}
    }
}
