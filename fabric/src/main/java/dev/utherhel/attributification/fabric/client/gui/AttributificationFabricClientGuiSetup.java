package dev.utherhel.attributification.fabric.client.gui;

import dev.utherhel.attributification.client.gui.MappingsScreen;
import dev.utherhel.attributification.gui.network.AttributesListPayload;
import dev.utherhel.attributification.gui.network.ConvertLegacyMappingsPayload;
import dev.utherhel.attributification.gui.network.DeleteMappingFilePayload;
import dev.utherhel.attributification.gui.network.LegacyMappingsFoundPayload;
import dev.utherhel.attributification.gui.network.MappingFileContentPayload;
import dev.utherhel.attributification.gui.network.MappingFilesListPayload;
import dev.utherhel.attributification.gui.network.OpenGuiPayload;
import dev.utherhel.attributification.gui.network.RequestAttributesListPayload;
import dev.utherhel.attributification.gui.network.RequestMappingFileContentPayload;
import dev.utherhel.attributification.gui.network.RequestMappingFilesPayload;
import dev.utherhel.attributification.gui.network.SaveMappingFilePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

@Environment(EnvType.CLIENT)
public final class AttributificationFabricClientGuiSetup implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MappingsScreen.callbacks = new MappingsScreen.Callbacks() {
            @Override
            public void requestFiles() {
                sendToServer(new RequestMappingFilesPayload());
            }

            @Override
            public void requestFileContent(String fileName) {
                sendToServer(new RequestMappingFileContentPayload(fileName));
            }

            @Override
            public void saveFile(String fileName, String jsonContent) {
                sendToServer(new SaveMappingFilePayload(fileName, jsonContent));
            }

            @Override
            public void deleteFile(String fileName) {
                sendToServer(new DeleteMappingFilePayload(fileName));
            }

            @Override
            public void requestAttributesList() {
                sendToServer(new RequestAttributesListPayload());
            }

            @Override
            public void convertLegacyMappings() {
                sendToServer(new ConvertLegacyMappingsPayload());
            }
        };

        ClientPlayNetworking.registerGlobalReceiver(OpenGuiPayload.ID, (client, handler, buf, responseSender) -> {
            client.execute(MappingsScreen::deliverOpen);
        });

        ClientPlayNetworking.registerGlobalReceiver(MappingFilesListPayload.ID, (client, handler, buf, responseSender) -> {
            MappingFilesListPayload payload = MappingFilesListPayload.decode(buf);
            client.execute(() -> MappingsScreen.deliverFilesList(payload.files()));
        });

        ClientPlayNetworking.registerGlobalReceiver(MappingFileContentPayload.ID, (client, handler, buf, responseSender) -> {
            MappingFileContentPayload payload = MappingFileContentPayload.decode(buf);
            client.execute(() -> MappingsScreen.deliverFileContent(payload.fileName(), payload.jsonContent()));
        });

        ClientPlayNetworking.registerGlobalReceiver(AttributesListPayload.ID, (client, handler, buf, responseSender) -> {
            AttributesListPayload payload = AttributesListPayload.decode(buf);
            client.execute(() -> MappingsScreen.deliverAttributes(payload.attributes()));
        });

        ClientPlayNetworking.registerGlobalReceiver(LegacyMappingsFoundPayload.ID, (client, handler, buf, responseSender) -> {
            LegacyMappingsFoundPayload payload = LegacyMappingsFoundPayload.decode(buf);
            client.execute(() -> MappingsScreen.deliverLegacyCount(payload.count()));
        });
    }

    private static void sendToServer(Object payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        java.util.function.Consumer<PacketByteBuf> encoder;
        java.util.function.Supplier<net.minecraft.util.Identifier> idSupplier;

        if (payload instanceof RequestMappingFilesPayload p) {
            encoder = p::encode;
            idSupplier = () -> RequestMappingFilesPayload.ID;
        } else if (payload instanceof RequestMappingFileContentPayload p) {
            encoder = p::encode;
            idSupplier = () -> RequestMappingFileContentPayload.ID;
        } else if (payload instanceof SaveMappingFilePayload p) {
            encoder = p::encode;
            idSupplier = () -> SaveMappingFilePayload.ID;
        } else if (payload instanceof DeleteMappingFilePayload p) {
            encoder = p::encode;
            idSupplier = () -> DeleteMappingFilePayload.ID;
        } else if (payload instanceof RequestAttributesListPayload p) {
            encoder = p::encode;
            idSupplier = () -> RequestAttributesListPayload.ID;
        } else if (payload instanceof ConvertLegacyMappingsPayload p) {
            encoder = p::encode;
            idSupplier = () -> ConvertLegacyMappingsPayload.ID;
        } else {
            return;
        }

        encoder.accept(buf);
        ClientPlayNetworking.send(idSupplier.get(), buf);
    }
}
