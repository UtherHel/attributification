package dev.utherhel.attributification.neoforge.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.attribute.MappingLoader;
import dev.utherhel.attributification.config.LegacyMappingConverter;
import dev.utherhel.attributification.gui.MappingFileNames;
import dev.utherhel.attributification.gui.network.*;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Server-side handler for Attributification GUI network payloads.
 * <p>
 * Registers all custom payloads in {@link RegisterPayloadHandlersEvent} and
 * handles client-to-server requests for mapping file operations.
 * <p>
 * Client-bound payloads delegate to static callbacks set by
 * {@link dev.utherhel.attributification.neoforge.client.gui.AttributificationClientGuiSetup}.
 */
public final class AttributificationPayloadHandler {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final String MAPPINGS_DIR = "attributification/mappings";
    private static final String GUI_DATAPACK_NAME = "attributification_gui";

    // Current resource manager, updated on datapack sync
    public static volatile ResourceManager currentResourceManager;

    // Client-bound payload callbacks — set by AttributificationClientGuiSetup (client only)
    public static volatile Consumer<OpenGuiPayload> onOpenGui;
    public static volatile Consumer<MappingFilesListPayload> onMappingFilesList;
    public static volatile Consumer<MappingFileContentPayload> onMappingFileContent;
    public static volatile Consumer<AttributesListPayload> onAttributesList;
    public static volatile Consumer<LegacyMappingsFoundPayload> onLegacyMappingsFound;

    private AttributificationPayloadHandler() {}

    /**
     * Registers all custom payloads.
     * Called on both logical sides during mod construction.
     */
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar(Attributifaction.MOD_ID);

        // ── Server → Client ────────────────────────────────────────────
        // The no-op / callback approach: the actual client logic is wired
        // via the static Consumer fields above, set by the client setup.

        registrar.playToClient(
            OpenGuiPayload.ID,
            OpenGuiPayload.CODEC,
            (payload, context) -> {
                if (onOpenGui != null) {
                    context.enqueueWork(() -> onOpenGui.accept(payload));
                }
            }
        );

        registrar.playToClient(
            MappingFilesListPayload.ID,
            MappingFilesListPayload.CODEC,
            (payload, context) -> {
                if (onMappingFilesList != null) {
                    context.enqueueWork(() ->
                        onMappingFilesList.accept(payload)
                    );
                }
            }
        );

        registrar.playToClient(
            MappingFileContentPayload.ID,
            MappingFileContentPayload.CODEC,
            (payload, context) -> {
                if (onMappingFileContent != null) {
                    context.enqueueWork(() ->
                        onMappingFileContent.accept(payload)
                    );
                }
            }
        );

        registrar.playToClient(
            AttributesListPayload.ID,
            AttributesListPayload.CODEC,
            (payload, context) -> {
                if (onAttributesList != null) {
                    context.enqueueWork(() -> onAttributesList.accept(payload));
                }
            }
        );

        registrar.playToClient(
            LegacyMappingsFoundPayload.ID,
            LegacyMappingsFoundPayload.CODEC,
            (payload, context) -> {
                if (onLegacyMappingsFound != null) {
                    context.enqueueWork(() ->
                        onLegacyMappingsFound.accept(payload)
                    );
                }
            }
        );

        registrar.playToServer(
            ConvertLegacyMappingsPayload.ID,
            ConvertLegacyMappingsPayload.CODEC,
            AttributificationPayloadHandler::handleConvertLegacyMappings
        );

        registrar.playToServer(
            RequestAttributesListPayload.ID,
            RequestAttributesListPayload.CODEC,
            AttributificationPayloadHandler::handleRequestAttributesList
        );

        // ── Client → Server ────────────────────────────────────────────

        registrar.playToServer(
            RequestMappingFilesPayload.ID,
            RequestMappingFilesPayload.CODEC,
            AttributificationPayloadHandler::handleRequestMappingFiles
        );

        registrar.playToServer(
            RequestMappingFileContentPayload.ID,
            RequestMappingFileContentPayload.CODEC,
            AttributificationPayloadHandler::handleRequestMappingFileContent
        );

        registrar.playToServer(
            SaveMappingFilePayload.ID,
            SaveMappingFilePayload.CODEC,
            AttributificationPayloadHandler::handleSaveMappingFile
        );

        registrar.playToServer(
            DeleteMappingFilePayload.ID,
            DeleteMappingFilePayload.CODEC,
            AttributificationPayloadHandler::handleDeleteMappingFile
        );
    }

    // ── Request: convert legacy mappings ────────────────────────────────

    private static void handleConvertLegacyMappings(
        final ConvertLegacyMappingsPayload payload,
        final IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer gateSender)
                    || !dev.utherhel.attributification.gui.network.AdminGate.isAllowed(gateSender)) {
                return;
            }
            MinecraftServer server = context.player().getServer();
            if (server == null) return;

            Path configDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
            String id = MappingFileNames.defaultId("legacy_converted.json");
            Path outputPath = getWorldFilePath(server, id);

            try {
                ensureWorldDatapackMeta(server);
            } catch (IOException e) {
                AttributificationLogger.warn(
                    "Failed to prepare world datapack: " + e.getMessage()
                );
                return;
            }

            int count = LegacyMappingConverter.convertAndSave(
                configDir,
                outputPath
            );
            if (count > 0) {
                AttributificationLogger.info(
                    "Converted " + count + " legacy mappings"
                );

                // Reload mappings
                ResourceManager rm = currentResourceManager;
                if (rm != null) {
                    MappingLoader.load(rm);
                }

                // Notify player
                if (context.player() instanceof ServerPlayer player) {
                    player.displayClientMessage(
                        Component.literal(
                            "Converted " +
                                count +
                                " legacy mappings to new format"
                        ),
                        false
                    );
                }

                // Send updated file list
                handleRequestMappingFiles(
                    new RequestMappingFilesPayload(),
                    context
                );
            }
        });
    }

    private static void handleRequestMappingFiles(
        final RequestMappingFilesPayload payload,
        final IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer gateSender)
                    || !dev.utherhel.attributification.gui.network.AdminGate.isAllowed(gateSender)) {
                return;
            }
            MinecraftServer server = context.player().getServer();
            if (server == null) return;

            ResourceManager rm = currentResourceManager;
            if (rm == null) return;

            // Keyed by mapping file id ("namespace/name.json") so a file saved
            // into the world shadows the built-in one instead of showing twice.
            Map<String, MappingFileEntry> byId = new LinkedHashMap<>();

            // World datapack — editable, and wins over built-in resources.
            for (String id : listWorldFileIds(server)) {
                byId.put(id, new MappingFileEntry(id, true));
            }

            // Built-in datapack resources — read-only, can be overridden.
            Map<ResourceLocation, Resource> resources = rm.listResources(
                MAPPINGS_DIR,
                id -> id.getPath().endsWith(".json")
            );
            for (ResourceLocation resourceId : resources.keySet()) {
                // resourceId = "namespace:attributification/mappings/name.json"
                String id = MappingFileNames.id(
                    resourceId.getNamespace(),
                    resourceId
                        .getPath()
                        .substring(MAPPINGS_DIR.length() + 1)
                );
                byId.putIfAbsent(id, new MappingFileEntry(id, false));
            }

            List<MappingFileEntry> files = new ArrayList<>(byId.values());
            files.sort((a, b) -> a.fileName().compareTo(b.fileName()));

            // Reply with the file list
            context.reply(new MappingFilesListPayload(files));
        });
    }

    // ── Request: list all attributes ─────────────────────────────────────

    private static void handleRequestAttributesList(
        final RequestAttributesListPayload payload,
        final IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer gateSender)
                    || !dev.utherhel.attributification.gui.network.AdminGate.isAllowed(gateSender)) {
                return;
            }
            List<String> attrs =
                net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.keySet()
                    .stream()
                    .map(net.minecraft.resources.ResourceLocation::toString)
                    .sorted()
                    .toList();
            context.reply(new AttributesListPayload(attrs));

            // Check for legacy Fzzy Config mappings
            Path configDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
            int legacyCount = LegacyMappingConverter.countLegacyMappings(
                configDir
            );
            if (legacyCount > 0) {
                context.reply(new LegacyMappingsFoundPayload(legacyCount));
            }
        });
    }

    // ── Request: read a specific mapping file ───────────────────────────

    private static void handleRequestMappingFileContent(
        final RequestMappingFileContentPayload payload,
        final IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer gateSender)
                    || !dev.utherhel.attributification.gui.network.AdminGate.isAllowed(gateSender)) {
                return;
            }
            String id = MappingFileNames.toValidId(payload.fileName());
            if (id == null) {
                AttributificationLogger.warn(
                    "Rejected mapping file id: " + payload.fileName()
                );
                return;
            }
            String content = null;

            // Try editable world datapack first — that copy overrides the
            // built-in resource, so it must be what the GUI shows.
            MinecraftServer server = context.player().getServer();
            if (server != null) {
                Path filePath = getWorldFilePath(server, id);
                if (Files.isRegularFile(filePath)) {
                    try {
                        content = Files.readString(
                            filePath,
                            StandardCharsets.UTF_8
                        );
                    } catch (IOException e) {
                        AttributificationLogger.warn(
                            "Failed to read " + id + ": " + e.getMessage()
                        );
                    }
                }
            }

            // Fall back to datapack resources (built-in, read-only files)
            if (content == null && currentResourceManager != null) {
                ResourceLocation resId = ResourceLocation.fromNamespaceAndPath(
                    MappingFileNames.namespaceOf(id),
                    MAPPINGS_DIR + "/" + MappingFileNames.fileNameOf(id)
                );
                try {
                    Resource res = currentResourceManager
                        .getResource(resId)
                        .orElse(null);
                    if (res != null) {
                        try (Reader reader = res.openAsReader()) {
                            StringBuilder sb = new StringBuilder();
                            char[] buf = new char[4096];
                            int len;
                            while ((len = reader.read(buf)) != -1) {
                                sb.append(buf, 0, len);
                            }
                            content = sb.toString();
                        }
                    }
                } catch (IOException e) {
                    AttributificationLogger.warn(
                        "Failed to read resource " +
                            resId +
                            ": " +
                            e.getMessage()
                    );
                }
            }

            if (content != null) {
                context.reply(new MappingFileContentPayload(id, content));
            } else {
                // Return empty template if file not found
                context.reply(
                    new MappingFileContentPayload(
                        id,
                        "{\n  \"mappings\": []\n}"
                    )
                );
            }
        });
    }

    // ── Request: save a mapping file ────────────────────────────────────

    private static void handleSaveMappingFile(
        final SaveMappingFilePayload payload,
        final IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer gateSender)
                    || !dev.utherhel.attributification.gui.network.AdminGate.isAllowed(gateSender)) {
                return;
            }
            MinecraftServer server = context.player().getServer();
            if (server == null) return;

            String id = MappingFileNames.toValidId(payload.fileName());
            if (id == null) {
                AttributificationLogger.warn(
                    "Rejected mapping file id: " + payload.fileName()
                );
                if (context.player() instanceof ServerPlayer player) {
                    player.displayClientMessage(
                        Component.translatable(
                            "command.attributification.gui.invalid_name",
                            String.valueOf(payload.fileName())
                        ),
                        false
                    );
                }
                return;
            }

            Path filePath = getWorldFilePath(server, id);
            try {
                // Validate that the content is well-formed JSON before writing
                JsonObject json = JsonParser.parseString(
                    payload.jsonContent()
                ).getAsJsonObject();

                Files.createDirectories(filePath.getParent());
                ensureWorldDatapackMeta(server);
                Files.writeString(
                    filePath,
                    GSON.toJson(json),
                    StandardCharsets.UTF_8
                );

                AttributificationLogger.info("Saved mapping file: " + id);

                // Reload mappings so changes take effect immediately
                ResourceManager rm = currentResourceManager;
                if (rm != null) {
                    MappingLoader.load(rm);
                }

                // Notify the player
                if (context.player() instanceof ServerPlayer player) {
                    player.displayClientMessage(
                        Component.translatable(
                            "command.attributification.gui.saved",
                            id
                        ),
                        false
                    );
                }
            } catch (Exception e) {
                AttributificationLogger.warn(
                    "Failed to save mapping file " + id + ": " + e.getMessage()
                );
            }
        });
    }

    // ── Request: delete a mapping file ──────────────────────────────────

    private static void handleDeleteMappingFile(
        final DeleteMappingFilePayload payload,
        final IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer gateSender)
                    || !dev.utherhel.attributification.gui.network.AdminGate.isAllowed(gateSender)) {
                return;
            }
            MinecraftServer server = context.player().getServer();
            if (server == null) return;

            String id = MappingFileNames.toValidId(payload.fileName());
            if (id == null) {
                AttributificationLogger.warn(
                    "Rejected mapping file id: " + payload.fileName()
                );
                return;
            }

            Path filePath = getWorldFilePath(server, id);
            try {
                boolean deleted = Files.deleteIfExists(filePath);
                if (deleted) {
                    AttributificationLogger.info("Deleted mapping file: " + id);

                    // Reload mappings
                    ResourceManager rm = currentResourceManager;
                    if (rm != null) {
                        MappingLoader.load(rm);
                    }

                    // Notify the player
                    if (context.player() instanceof ServerPlayer player) {
                        player.displayClientMessage(
                            Component.translatable(
                                "command.attributification.gui.deleted",
                                id
                            ),
                            false
                        );
                    }
                }
            } catch (IOException e) {
                AttributificationLogger.warn(
                    "Failed to delete mapping file " +
                        id +
                        ": " +
                        e.getMessage()
                );
            }
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Root of the world datapack that holds every GUI-authored mapping file.
     * <p>
     * Path: {@code <world>/datapacks/attributification_gui/}
     */
    private static Path getWorldDatapackRoot(MinecraftServer server) {
        return server
            .getWorldPath(LevelResource.ROOT)
            .resolve("datapacks/" + GUI_DATAPACK_NAME);
    }

    /**
     * Resolves a mapping file id to its path inside the world datapack.
     * <p>
     * Writing under the id's own namespace is what makes an override work: a
     * file at {@code data/<ns>/attributification/mappings/<name>.json} shadows
     * the built-in resource with the same id, because world datapacks load
     * after mod-provided ones.
     */
    private static Path getWorldFilePath(MinecraftServer server, String id) {
        return getWorldDatapackRoot(server)
            .resolve("data")
            .resolve(MappingFileNames.namespaceOf(id))
            .resolve(MAPPINGS_DIR)
            .resolve(MappingFileNames.fileNameOf(id));
    }

    /** Lists the mapping file ids present in the world datapack. */
    private static List<String> listWorldFileIds(MinecraftServer server) {
        Path dataDir = getWorldDatapackRoot(server).resolve("data");
        if (!Files.isDirectory(dataDir)) return List.of();

        List<String> ids = new ArrayList<>();
        try (var namespaces = Files.list(dataDir)) {
            for (Path nsDir : namespaces.toList()) {
                if (!Files.isDirectory(nsDir)) continue;
                String namespace = nsDir.getFileName().toString();
                Path mappingsDir = nsDir.resolve(MAPPINGS_DIR);
                if (!Files.isDirectory(mappingsDir)) continue;

                try (var stream = Files.list(mappingsDir)) {
                    stream
                        .filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(MappingFileNames::isValidFileName)
                        .forEach(name ->
                            ids.add(MappingFileNames.id(namespace, name))
                        );
                }
            }
        } catch (IOException e) {
            AttributificationLogger.warn(
                "Failed to list world datapack mappings: " + e.getMessage()
            );
        }
        return ids;
    }

    /**
     * Writes {@code pack.mcmeta} for the world datapack if it is missing.
     * Without it the folder is not a datapack at all and nothing the GUI saves
     * would ever be loaded.
     */
    private static void ensureWorldDatapackMeta(MinecraftServer server)
        throws IOException {
        Path meta = getWorldDatapackRoot(server).resolve("pack.mcmeta");
        if (Files.exists(meta)) return;

        Files.createDirectories(meta.getParent());
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Attributification GUI mappings");
        pack.addProperty(
            "pack_format",
            SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA)
        );
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        Files.writeString(meta, GSON.toJson(root), StandardCharsets.UTF_8);
    }
}
