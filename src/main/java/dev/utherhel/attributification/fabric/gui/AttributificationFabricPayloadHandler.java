package dev.utherhel.attributification.fabric.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.AttributificationLogger;
import dev.utherhel.attributification.attribute.MappingLoader;
import dev.utherhel.attributification.config.LegacyMappingConverter;
import dev.utherhel.attributification.gui.MappingFileNames;
import dev.utherhel.attributification.gui.network.AttributesListPayload;
import dev.utherhel.attributification.gui.network.ConvertLegacyMappingsPayload;
import dev.utherhel.attributification.gui.network.DeleteMappingFilePayload;
import dev.utherhel.attributification.gui.network.LegacyMappingsFoundPayload;
import dev.utherhel.attributification.gui.network.MappingFileContentPayload;
import dev.utherhel.attributification.gui.network.MappingFileEntry;
import dev.utherhel.attributification.gui.network.MappingFilesListPayload;
import dev.utherhel.attributification.gui.network.OpenGuiPayload;
import dev.utherhel.attributification.gui.network.RequestAttributesListPayload;
import dev.utherhel.attributification.gui.network.RequestMappingFileContentPayload;
import dev.utherhel.attributification.gui.network.RequestMappingFilesPayload;
import dev.utherhel.attributification.gui.network.SaveMappingFilePayload;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class AttributificationFabricPayloadHandler {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static volatile ResourceManager currentResourceManager;

    public static void register() {
        // Register all payload types
        PayloadTypeRegistry.playS2C().register(
            OpenGuiPayload.ID,
            OpenGuiPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
            RequestMappingFilesPayload.ID,
            RequestMappingFilesPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
            MappingFilesListPayload.ID,
            MappingFilesListPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
            RequestMappingFileContentPayload.ID,
            RequestMappingFileContentPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
            MappingFileContentPayload.ID,
            MappingFileContentPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
            SaveMappingFilePayload.ID,
            SaveMappingFilePayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
            DeleteMappingFilePayload.ID,
            DeleteMappingFilePayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
            AttributesListPayload.ID,
            AttributesListPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
            RequestAttributesListPayload.ID,
            RequestAttributesListPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
            LegacyMappingsFoundPayload.ID,
            LegacyMappingsFoundPayload.CODEC
        );
        PayloadTypeRegistry.playC2S().register(
            ConvertLegacyMappingsPayload.ID,
            ConvertLegacyMappingsPayload.CODEC
        );

        // Register receivers
        ServerPlayNetworking.registerGlobalReceiver(
            RequestMappingFilesPayload.ID,
            (payload, context) -> {
                context.server().execute(() -> {
                    if (!dev.utherhel.attributification.gui.network.AdminGate.isAllowed(context.player())) {
                        return;
                    }
                    ServerPlayer player = context.player();
                    MappingFilesListPayload response = buildFileList(
                        context.server(),
                        currentResourceManager
                    );
                    ServerPlayNetworking.send(player, response);
                });
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(
            RequestMappingFileContentPayload.ID,
            (payload, context) -> {
                context.server().execute(() -> {
                    if (!dev.utherhel.attributification.gui.network.AdminGate.isAllowed(context.player())) {
                        return;
                    }
                    ServerPlayer player = context.player();
                    handleRequestFileContent(
                        player,
                        payload,
                        currentResourceManager
                    );
                });
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(
            SaveMappingFilePayload.ID,
            (payload, context) -> {
                context.server().execute(() -> {
                    if (!dev.utherhel.attributification.gui.network.AdminGate.isAllowed(context.player())) {
                        return;
                    }
                    handleSaveFile(context.server(), payload);
                });
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(
            DeleteMappingFilePayload.ID,
            (payload, context) -> {
                context.server().execute(() -> {
                    if (!dev.utherhel.attributification.gui.network.AdminGate.isAllowed(context.player())) {
                        return;
                    }
                    handleDeleteFile(context.server(), payload);
                });
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(
            RequestAttributesListPayload.ID,
            (payload, context) -> {
                context.server().execute(() -> {
                    if (!dev.utherhel.attributification.gui.network.AdminGate.isAllowed(context.player())) {
                        return;
                    }
                    ServerPlayer player = context.player();
                    List<String> attrs =
                        net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.keySet()
                            .stream()
                            .map(net.minecraft.resources.ResourceLocation::toString)
                            .sorted()
                            .toList();
                    ServerPlayNetworking.send(
                        player,
                        new AttributesListPayload(attrs)
                    );

                    // Check for legacy Fzzy Config mappings
                    Path configDir =
                        net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
                    int legacyCount =
                        LegacyMappingConverter.countLegacyMappings(configDir);
                    if (legacyCount > 0) {
                        ServerPlayNetworking.send(
                            player,
                            new LegacyMappingsFoundPayload(legacyCount)
                        );
                    }
                });
            }
        );

        ServerPlayNetworking.registerGlobalReceiver(
            ConvertLegacyMappingsPayload.ID,
            (payload, context) -> {
                context.server().execute(() -> {
                    if (!dev.utherhel.attributification.gui.network.AdminGate.isAllowed(context.player())) {
                        return;
                    }
                    ServerPlayer player = context.player();
                    MinecraftServer server = context.server();
                    Path configDir =
                        net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();

                    String id = MappingFileNames.defaultId(
                        "legacy_converted.json"
                    );
                    Path outputFile = getWorldFilePath(server, id);

                    try {
                        ensureWorldDatapackMeta(server);
                    } catch (IOException e) {
                        AttributificationLogger.warn(
                            "Failed to prepare world datapack: " +
                                e.getMessage()
                        );
                        return;
                    }

                    int count = LegacyMappingConverter.convertAndSave(
                        configDir,
                        outputFile
                    );
                    if (count > 0) {
                        AttributificationLogger.info(
                            "Converted " + count + " legacy mappings"
                        );

                        // Reload mappings
                        if (currentResourceManager != null) {
                            MappingLoader.load(currentResourceManager);
                        }

                        // Send updated file list
                        MappingFilesListPayload fileList = buildFileList(
                            server,
                            currentResourceManager
                        );
                        ServerPlayNetworking.send(player, fileList);
                    }
                });
            }
        );
    }

    public static void setResourceManager(ResourceManager manager) {
        currentResourceManager = manager;
    }

    private static MappingFilesListPayload buildFileList(
        MinecraftServer server,
        ResourceManager resourceManager
    ) {
        // Keyed by mapping file id ("namespace/name.json") so a file saved into
        // the world shadows the built-in one instead of showing twice.
        Map<String, MappingFileEntry> byId = new LinkedHashMap<>();

        // World datapack — editable, and wins over built-in resources.
        if (server != null) {
            for (String id : listWorldFileIds(server)) {
                byId.put(id, new MappingFileEntry(id, true));
            }
        }

        // Built-in datapack resources — read-only, can be overridden.
        if (resourceManager != null) {
            Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                MappingFileNames.MAPPINGS_DIR,
                id -> id.getPath().endsWith(".json")
            );
            for (ResourceLocation resourceId : resources.keySet()) {
                String id = MappingFileNames.id(
                    resourceId.getNamespace(),
                    resourceId
                        .getPath()
                        .substring(MappingFileNames.MAPPINGS_DIR.length() + 1)
                );
                byId.putIfAbsent(id, new MappingFileEntry(id, false));
            }
        }

        List<MappingFileEntry> files = new ArrayList<>(byId.values());
        files.sort((a, b) -> a.fileName().compareTo(b.fileName()));

        return new MappingFilesListPayload(files);
    }

    private static void handleRequestFileContent(
        ServerPlayer player,
        RequestMappingFileContentPayload payload,
        ResourceManager resourceManager
    ) {
        String id = MappingFileNames.toValidId(payload.fileName());
        if (id == null) {
            AttributificationLogger.warn(
                "Rejected mapping file id: " + payload.fileName()
            );
            return;
        }

        // Try the world datapack first — that copy overrides the built-in
        // resource, so it must be what the GUI shows.
        Path worldFilePath = getWorldFilePath(player.getServer(), id);
        if (Files.isRegularFile(worldFilePath)) {
            try {
                String content = Files.readString(
                    worldFilePath,
                    StandardCharsets.UTF_8
                );
                ServerPlayNetworking.send(
                    player,
                    new MappingFileContentPayload(id, content)
                );
                return;
            } catch (IOException e) {
                AttributificationLogger.warn(
                    "Failed to read " + id + ": " + e.getMessage()
                );
            }
        }

        // Fall back to datapack resources (built-in, read-only files)
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
            MappingFileNames.namespaceOf(id),
            MappingFileNames.MAPPINGS_DIR +
                "/" +
                MappingFileNames.fileNameOf(id)
        );
        Resource resource = resourceManager == null
            ? null
            : resourceManager.getResource(resourceId).orElse(null);
        if (resource != null) {
            try (
                Reader reader = new java.io.InputStreamReader(
                    resource.open(),
                    StandardCharsets.UTF_8
                )
            ) {
                StringBuilder content = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) > 0) {
                    content.append(buf, 0, n);
                }
                ServerPlayNetworking.send(
                    player,
                    new MappingFileContentPayload(id, content.toString())
                );
                return;
            } catch (IOException e) {
                AttributificationLogger.error(
                    "Failed to read mapping file " + id + ": " + e.getMessage()
                );
            }
        }

        // Return empty template if not found
        ServerPlayNetworking.send(
            player,
            new MappingFileContentPayload(id, "{\n  \"mappings\": []\n}")
        );
    }

    private static void handleSaveFile(
        MinecraftServer server,
        SaveMappingFilePayload payload
    ) {
        String id = MappingFileNames.toValidId(payload.fileName());
        if (id == null) {
            AttributificationLogger.warn(
                "Rejected mapping file id: " + payload.fileName()
            );
            return;
        }
        Path filePath = getWorldFilePath(server, id);

        try {
            // Validate JSON before writing anything
            JsonObject json = JsonParser.parseString(
                payload.jsonContent()
            ).getAsJsonObject();

            Files.createDirectories(filePath.getParent());
            ensureWorldDatapackMeta(server);
            Files.writeString(
                filePath,
                new GsonBuilder().setPrettyPrinting().create().toJson(json),
                StandardCharsets.UTF_8
            );
            AttributificationLogger.info("Saved mapping file: " + id);
            MappingLoader.load(currentResourceManager); // Reload
        } catch (Exception e) {
            AttributificationLogger.error(
                "Failed to save mapping file " + id + ": " + e.getMessage()
            );
        }
    }

    private static void handleDeleteFile(
        MinecraftServer server,
        DeleteMappingFilePayload payload
    ) {
        String id = MappingFileNames.toValidId(payload.fileName());
        if (id == null) {
            AttributificationLogger.warn(
                "Rejected mapping file id: " + payload.fileName()
            );
            return;
        }
        Path filePath = getWorldFilePath(server, id);
        try {
            Files.deleteIfExists(filePath);
            AttributificationLogger.info("Deleted mapping file: " + id);
            MappingLoader.load(currentResourceManager); // Reload
        } catch (IOException e) {
            AttributificationLogger.error(
                "Failed to delete mapping file " + id + ": " + e.getMessage()
            );
        }
    }

    /**
     * Root of the world datapack that holds every GUI-authored mapping file.
     * <p>
     * Path: {@code <world>/datapacks/attributification_gui/}
     */
    private static Path getWorldDatapackRoot(MinecraftServer server) {
        return server
            .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("datapacks/attributification_gui");
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
            .resolve(MappingFileNames.MAPPINGS_DIR)
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
                Path mappingsDir = nsDir.resolve(
                    MappingFileNames.MAPPINGS_DIR
                );
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
            SharedConstants.getCurrentVersion()
                .getPackVersion(PackType.SERVER_DATA)
        );
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        Files.writeString(
            meta,
            new GsonBuilder().setPrettyPrinting().create().toJson(root),
            StandardCharsets.UTF_8
        );
    }

    private AttributificationFabricPayloadHandler() {}
}
