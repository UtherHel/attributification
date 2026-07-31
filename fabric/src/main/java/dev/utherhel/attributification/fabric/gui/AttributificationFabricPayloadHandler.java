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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class AttributificationFabricPayloadHandler {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(RequestMappingFilesPayload.ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                MappingFilesListPayload response = buildFileList(server);
                sendToPlayer(player, response);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestMappingFileContentPayload.ID, (server, player, handler, buf, responseSender) -> {
            RequestMappingFileContentPayload payload = RequestMappingFileContentPayload.decode(buf);
            server.execute(() -> {
                handleRequestFileContent(player, payload, server.getResourceManager());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveMappingFilePayload.ID, (server, player, handler, buf, responseSender) -> {
            SaveMappingFilePayload payload = SaveMappingFilePayload.decode(buf);
            server.execute(() -> {
                handleSaveFile(server, payload);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DeleteMappingFilePayload.ID, (server, player, handler, buf, responseSender) -> {
            DeleteMappingFilePayload payload = DeleteMappingFilePayload.decode(buf);
            server.execute(() -> {
                handleDeleteFile(server, payload);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestAttributesListPayload.ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                List<String> attrs = net.minecraft.registry.Registries.ATTRIBUTE.getIds()
                    .stream()
                    .map(Identifier::toString)
                    .sorted()
                    .toList();
                sendToPlayer(player, new AttributesListPayload(attrs));

                Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
                int legacyCount = LegacyMappingConverter.countLegacyMappings(configDir);
                if (legacyCount > 0) {
                    sendToPlayer(player, new LegacyMappingsFoundPayload(legacyCount));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ConvertLegacyMappingsPayload.ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                MinecraftServer srv = server;
                Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();

                String id = MappingFileNames.defaultId("legacy_converted.json");
                Path outputFile = getWorldFilePath(srv, id);

                try {
                    ensureWorldDatapackMeta(srv);
                } catch (IOException e) {
                    AttributificationLogger.warn("Failed to prepare world datapack: " + e.getMessage());
                    return;
                }

                int count = LegacyMappingConverter.convertAndSave(configDir, outputFile);
                if (count > 0) {
                    AttributificationLogger.info("Converted " + count + " legacy mappings");
                    MappingLoader.load(srv.getResourceManager());

                    MappingFilesListPayload fileList = buildFileList(srv);
                    sendToPlayer(player, fileList);
                }
            });
        });
    }

    private static void sendToPlayer(ServerPlayerEntity player, Object payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        Identifier id;
        if (payload instanceof MappingFilesListPayload p) {
            id = MappingFilesListPayload.ID;
            p.encode(buf);
        } else if (payload instanceof MappingFileContentPayload p) {
            id = MappingFileContentPayload.ID;
            p.encode(buf);
        } else if (payload instanceof AttributesListPayload p) {
            id = AttributesListPayload.ID;
            p.encode(buf);
        } else if (payload instanceof LegacyMappingsFoundPayload p) {
            id = LegacyMappingsFoundPayload.ID;
            p.encode(buf);
        } else if (payload instanceof OpenGuiPayload p) {
            id = OpenGuiPayload.ID;
            p.encode(buf);
        } else {
            AttributificationLogger.warn("Unknown payload type: " + payload.getClass().getName());
            return;
        }
        ServerPlayNetworking.send(player, id, buf);
    }

    private static MappingFilesListPayload buildFileList(MinecraftServer server) {
        Map<String, MappingFileEntry> byId = new LinkedHashMap<>();

        if (server != null) {
            for (String id : listWorldFileIds(server)) {
                byId.put(id, new MappingFileEntry(id, true));
            }
        }

        ResourceManager resourceManager = server.getResourceManager();
        if (resourceManager != null) {
            Map<Identifier, Resource> resources = resourceManager.findResources(
                MappingFileNames.MAPPINGS_DIR,
                path -> path.getPath().endsWith(".json")
            );
            for (Identifier resourceId : resources.keySet()) {
                String id = MappingFileNames.id(
                    resourceId.getNamespace(),
                    resourceId.getPath().substring(MappingFileNames.MAPPINGS_DIR.length() + 1)
                );
                byId.putIfAbsent(id, new MappingFileEntry(id, false));
            }
        }

        List<MappingFileEntry> files = new ArrayList<>(byId.values());
        files.sort((a, b) -> a.fileName().compareTo(b.fileName()));

        return new MappingFilesListPayload(files);
    }

    private static void handleRequestFileContent(ServerPlayerEntity player, RequestMappingFileContentPayload payload, ResourceManager resourceManager) {
        String id = MappingFileNames.toValidId(payload.fileName());
        if (id == null) {
            AttributificationLogger.warn("Rejected mapping file id: " + payload.fileName());
            return;
        }

        Path worldFilePath = getWorldFilePath(player.getServer(), id);
        if (Files.isRegularFile(worldFilePath)) {
            try {
                String content = Files.readString(worldFilePath, StandardCharsets.UTF_8);
                sendToPlayer(player, new MappingFileContentPayload(id, content));
                return;
            } catch (IOException e) {
                AttributificationLogger.warn("Failed to read " + id + ": " + e.getMessage());
            }
        }

        Identifier resourceId = Identifier.of(
            MappingFileNames.namespaceOf(id),
            MappingFileNames.MAPPINGS_DIR + "/" + MappingFileNames.fileNameOf(id)
        );
        Resource resource = resourceManager == null ? null : resourceManager.getResource(resourceId).orElse(null);
        if (resource != null) {
            try (Reader reader = new java.io.InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                StringBuilder content = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                while ((n = reader.read(buf)) > 0) {
                    content.append(buf, 0, n);
                }
                sendToPlayer(player, new MappingFileContentPayload(id, content.toString()));
                return;
            } catch (IOException e) {
                AttributificationLogger.error("Failed to read mapping file " + id + ": " + e.getMessage());
            }
        }

        sendToPlayer(player, new MappingFileContentPayload(id, "{\n  \"mappings\": []\n}"));
    }

    private static void handleSaveFile(MinecraftServer server, SaveMappingFilePayload payload) {
        String id = MappingFileNames.toValidId(payload.fileName());
        if (id == null) {
            AttributificationLogger.warn("Rejected mapping file id: " + payload.fileName());
            return;
        }
        Path filePath = getWorldFilePath(server, id);

        try {
            JsonObject json = JsonParser.parseString(payload.jsonContent()).getAsJsonObject();
            Files.createDirectories(filePath.getParent());
            ensureWorldDatapackMeta(server);
            Files.writeString(filePath, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            AttributificationLogger.info("Saved mapping file: " + id);
            MappingLoader.load(server.getResourceManager());
        } catch (Exception e) {
            AttributificationLogger.error("Failed to save mapping file " + id + ": " + e.getMessage());
        }
    }

    private static void handleDeleteFile(MinecraftServer server, DeleteMappingFilePayload payload) {
        String id = MappingFileNames.toValidId(payload.fileName());
        if (id == null) {
            AttributificationLogger.warn("Rejected mapping file id: " + payload.fileName());
            return;
        }
        Path filePath = getWorldFilePath(server, id);
        try {
            Files.deleteIfExists(filePath);
            AttributificationLogger.info("Deleted mapping file: " + id);
            MappingLoader.load(server.getResourceManager());
        } catch (IOException e) {
            AttributificationLogger.error("Failed to delete mapping file " + id + ": " + e.getMessage());
        }
    }

    private static Path getWorldDatapackRoot(MinecraftServer server) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("datapacks/attributification_gui");
    }

    private static Path getWorldFilePath(MinecraftServer server, String id) {
        return getWorldDatapackRoot(server)
            .resolve("data")
            .resolve(MappingFileNames.namespaceOf(id))
            .resolve(MappingFileNames.MAPPINGS_DIR)
            .resolve(MappingFileNames.fileNameOf(id));
    }

    private static List<String> listWorldFileIds(MinecraftServer server) {
        Path dataDir = getWorldDatapackRoot(server).resolve("data");
        if (!Files.isDirectory(dataDir)) return List.of();

        List<String> ids = new ArrayList<>();
        try (var namespaces = Files.list(dataDir)) {
            for (Path nsDir : namespaces.toList()) {
                if (!Files.isDirectory(nsDir)) continue;
                String namespace = nsDir.getFileName().toString();
                Path mappingsDir = nsDir.resolve(MappingFileNames.MAPPINGS_DIR);
                if (!Files.isDirectory(mappingsDir)) continue;

                try (var stream = Files.list(mappingsDir)) {
                    stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(MappingFileNames::isValidFileName)
                        .forEach(name -> ids.add(MappingFileNames.id(namespace, name)));
                }
            }
        } catch (IOException e) {
            AttributificationLogger.warn("Failed to list world datapack mappings: " + e.getMessage());
        }
        return ids;
    }

    private static void ensureWorldDatapackMeta(MinecraftServer server) throws IOException {
        Path meta = getWorldDatapackRoot(server).resolve("pack.mcmeta");
        if (Files.exists(meta)) return;

        Files.createDirectories(meta.getParent());
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Attributification GUI mappings");
        pack.addProperty("pack_format", SharedConstants.getGameVersion().getResourceVersion(ResourceType.SERVER_DATA));
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        Files.writeString(meta, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
    }

    private AttributificationFabricPayloadHandler() {}
}
