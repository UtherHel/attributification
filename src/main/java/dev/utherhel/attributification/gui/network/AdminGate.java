package dev.utherhel.attributification.gui.network;

import dev.utherhel.attributification.AttributificationLogger;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side permission check for the mapping editor's client-to-server requests.
 * <p>
 * The editor is opened by an operator-only command, but its packets can be sent by any connected
 * client, so every request that reads, writes, deletes or converts mapping files checks the
 * sender again here, with the same level as the command.
 */
public final class AdminGate {

    public static final int PERMISSION_LEVEL = 2;

    private AdminGate() {
    }

    public static boolean isAllowed(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(PERMISSION_LEVEL)) {
            return true;
        }
        AttributificationLogger.warn("Ignoring mapping editor request from " + player.getName().getString()
            + ": operator permission level " + PERMISSION_LEVEL + " required");
        return false;
    }
}
