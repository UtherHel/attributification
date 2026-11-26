package dev.utherhel.attributification.attribute;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.gui.network.SyncMappingsPayload;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sends the server's compiled mappings to the clients and applies them there.
 * <p>
 * Mappings are data-pack files, so only the server reads them. A client also computes attribute
 * values (its own player's, for the HUD and the inventory) and draws item tooltips; without the
 * server's mappings it showed the unmapped numbers. The platform code sends {@link #payload()} to a
 * player who joins, sets {@link #broadcaster} to reach every player after a reload, hands what a
 * client receives to {@link #apply} and calls {@link #clear} when the client leaves the server.
 */
public final class MappingSync {

    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type LIST = new TypeToken<List<CompiledMapping>>() {}.getType();

    /** Sends a payload to every player of the running server; set by the platform code. */
    public static volatile Consumer<SyncMappingsPayload> broadcaster = payload -> {};

    /** Whether the mappings in use came from a remote server (and must go when leaving it). */
    private static volatile boolean fromServer;

    private MappingSync() {}

    public static SyncMappingsPayload payload() {
        return new SyncMappingsPayload(MappingLoader.token(), GSON.toJson(MappingLoader.getMappings(), LIST));
    }

    /** Called by {@link MappingLoader} on the server after every load. */
    static void broadcast() {
        try {
            broadcaster.accept(payload());
        } catch (RuntimeException e) {
            AttributificationLogger.warn("Could not send the mappings to the players: " + e);
        }
    }

    /** Client side: use the server's mappings. */
    public static void apply(SyncMappingsPayload payload) {
        if (payload.token().equals(MappingLoader.token())) {
            return; // the server runs in this game and its mappings are already in use
        }
        List<CompiledMapping> mappings;
        try {
            mappings = GSON.fromJson(payload.jsonContent(), LIST);
        } catch (RuntimeException e) {
            AttributificationLogger.warn("Could not read the mappings the server sent: " + e);
            return;
        }
        fromServer = true;
        MappingLoader.useSynced(mappings == null ? List.of() : mappings);
        AttributificationLogger.info("Using the server's " + MappingLoader.getMappings().size() + " attribute mappings");
    }

    /** Client side: the connection to the server ended; its mappings no longer apply. */
    public static void clear() {
        if (!fromServer) {
            return;
        }
        fromServer = false;
        MappingLoader.useSynced(List.of());
    }
}
