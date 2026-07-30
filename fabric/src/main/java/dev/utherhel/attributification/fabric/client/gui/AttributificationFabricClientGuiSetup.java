package dev.utherhel.attributification.fabric.client.gui;

import dev.utherhel.attributification.client.gui.MappingsScreen;
import dev.utherhel.attributification.gui.network.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class AttributificationFabricClientGuiSetup
    implements ClientModInitializer
{

    @Override
    public void onInitializeClient() {
        // Set the callbacks for the common MappingsScreen
        MappingsScreen.callbacks = new MappingsScreen.Callbacks() {
            @Override
            public void requestFiles() {
                ClientPlayNetworking.send(new RequestMappingFilesPayload());
            }

            @Override
            public void requestFileContent(String fileName) {
                ClientPlayNetworking.send(
                    new RequestMappingFileContentPayload(fileName)
                );
            }

            @Override
            public void saveFile(String fileName, String jsonContent) {
                ClientPlayNetworking.send(
                    new SaveMappingFilePayload(fileName, jsonContent)
                );
            }

            @Override
            public void deleteFile(String fileName) {
                ClientPlayNetworking.send(
                    new DeleteMappingFilePayload(fileName)
                );
            }

            @Override
            public void requestAttributesList() {
                ClientPlayNetworking.send(new RequestAttributesListPayload());
            }

            @Override
            public void convertLegacyMappings() {
                ClientPlayNetworking.send(new ConvertLegacyMappingsPayload());
            }
        };

        // When server tells us to open the GUI
        ClientPlayNetworking.registerGlobalReceiver(
            OpenGuiPayload.ID,
            (payload, context) -> {
                context.client().execute(MappingsScreen::deliverOpen);
            }
        );

        // When the server sends the file list
        ClientPlayNetworking.registerGlobalReceiver(
            MappingFilesListPayload.ID,
            (payload, context) -> {
                context.client().execute(() -> {
                    MappingsScreen.deliverFilesList(payload.files());
                });
            }
        );

        // When the server sends file content
        ClientPlayNetworking.registerGlobalReceiver(
            MappingFileContentPayload.ID,
            (payload, context) -> {
                context.client().execute(() -> {
                    MappingsScreen.deliverFileContent(
                        payload.fileName(),
                        payload.jsonContent()
                    );
                });
            }
        );

        // When the server sends attributes list
        ClientPlayNetworking.registerGlobalReceiver(
            AttributesListPayload.ID,
            (payload, context) -> {
                context.client().execute(() -> {
                    MappingsScreen.deliverAttributes(payload.attributes());
                });
            }
        );

        // When the server notifies about legacy mappings
        ClientPlayNetworking.registerGlobalReceiver(
            LegacyMappingsFoundPayload.ID,
            (payload, context) -> {
                context.client().execute(() -> {
                    MappingsScreen.deliverLegacyCount(payload.count());
                });
            }
        );
    }
}
