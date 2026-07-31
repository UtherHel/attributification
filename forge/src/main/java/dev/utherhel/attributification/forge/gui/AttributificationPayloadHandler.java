package dev.utherhel.attributification.forge.gui;

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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AttributificationPayloadHandler {

    private static final String PROTOCOL_VERSION = "1";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(Identifier.of(Attributifaction.MOD_ID, "main"))
        .clientAcceptedVersions(PROTOCOL_VERSION::equals)
        .serverAcceptedVersions(PROTOCOL_VERSION::equals)
        .networkProtocolVersion(() -> PROTOCOL_VERSION)
        .simpleChannel();

    // Client-side callbacks (set by AttributificationClientGuiSetup on client only)
    public static Runnable openGuiHandler = () -> {};
    public static Consumer<MappingFilesListPayload> filesListHandler = p -> {};
    public static BiConsumer<String, String> fileContentHandler = (n, c) -> {};
    public static Consumer<List<String>> attributesHandler = a -> {};
    public static Consumer<Integer> legacyCountHandler = c -> {};

    private static int nextId = 0;

    public static void register() {
        registerMessage(OpenGuiPayload.class, OpenGuiPayload::encode, OpenGuiPayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                    openGuiHandler.run();
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(RequestMappingFilesPayload.class, RequestMappingFilesPayload::encode, RequestMappingFilesPayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                    ServerPlayerEntity player = ctx.get().getSender();
                    if (player != null) {
                        MappingFilesListPayload response = buildFileList(player.getServer());
                        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(MappingFilesListPayload.class, MappingFilesListPayload::encode, MappingFilesListPayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                    filesListHandler.accept(msg);
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(RequestMappingFileContentPayload.class, RequestMappingFileContentPayload::encode, RequestMappingFileContentPayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                    ServerPlayerEntity player = ctx.get().getSender();
                    if (player != null) {
                        handleRequestFileContent(player, msg, player.getServer().getResourceManager());
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(MappingFileContentPayload.class, MappingFileContentPayload::encode, MappingFileContentPayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                    fileContentHandler.accept(msg.fileName(), msg.jsonContent());
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(SaveMappingFilePayload.class, SaveMappingFilePayload::encode, SaveMappingFilePayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                    ServerPlayerEntity player = ctx.get().getSender();
                    if (player != null) {
                        handleSaveFile(player.getServer(), msg);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(DeleteMappingFilePayload.class, DeleteMappingFilePayload::encode, DeleteMappingFilePayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                    ServerPlayerEntity player = ctx.get().getSender();
                    if (player != null) {
                        handleDeleteFile(player.getServer(), msg);
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(RequestAttributesListPayload.class, RequestAttributesListPayload::encode, RequestAttributesListPayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                    ServerPlayerEntity player = ctx.get().getSender();
                    if (player != null) {
                        List<String> attrs = net.minecraft.registry.Registries.ATTRIBUTE.getIds()
                            .stream()
                            .map(Identifier::toString)
                            .sorted()
                            .toList();
                        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AttributesListPayload(attrs));

                        Path configDir = FMLPaths.CONFIGDIR.get();
                        int legacyCount = LegacyMappingConverter.countLegacyMappings(configDir);
                        if (legacyCount > 0) {
                            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new LegacyMappingsFoundPayload(legacyCount));
                        }
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(AttributesListPayload.class, AttributesListPayload::encode, AttributesListPayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                    attributesHandler.accept(msg.attributes());
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(LegacyMappingsFoundPayload.class, LegacyMappingsFoundPayload::encode, LegacyMappingsFoundPayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                    legacyCountHandler.accept(msg.count());
                }
            });
            ctx.get().setPacketHandled(true);
        });

        registerMessage(ConvertLegacyMappingsPayload.class, ConvertLegacyMappingsPayload::encode, ConvertLegacyMappingsPayload::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> {
                if (ctx.get().getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                    ServerPlayerEntity player = ctx.get().getSender();
                    if (player != null) {
                        MinecraftServer server = player.getServer();
                        Path configDir = FMLPaths.CONFIGDIR.get();

                        String id = MappingFileNames.defaultId("legacy_converted.json");
                        Path outputFile = getWorldFilePath(server, id);

                        try {
                            ensureWorldDatapackMeta(server);
                        } catch (IOException e) {
                            AttributificationLogger.warn("Failed to prepare world datapack: " + e.getMessage());
                            return;
                        }

                        int count = LegacyMappingConverter.convertAndSave(configDir, outputFile);
                        if (count > 0) {
                            AttributificationLogger.info("Converted " + count + " legacy mappings");
                            MappingLoader.load(server.getResourceManager());

                            MappingFilesListPayload fileList = buildFileList(server);
                            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), fileList);
                        }
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        });
    }

    private static <T> void registerMessage(Class<T> clazz, BiConsumer<T, PacketByteBuf> encoder, java.util.function.Function<PacketByteBuf, T> decoder, BiConsumer<T, java.util.function.Supplier<NetworkEvent.Context>> consumer) {
        CHANNEL.registerMessage(nextId++, clazz, encoder, decoder, consumer);
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
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MappingFileContentPayload(id, content));
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
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MappingFileContentPayload(id, content.toString()));
                return;
            } catch (IOException e) {
                AttributificationLogger.error("Failed to read mapping file " + id + ": " + e.getMessage());
            }
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MappingFileContentPayload(id, "{\n  \"mappings\": []\n}"));
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

    private AttributificationPayloadHandler() {}
}
