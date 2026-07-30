package dev.utherhel.attributification.neoforge.client.gui;

import dev.utherhel.attributification.Attributifaction;
import dev.utherhel.attributification.client.gui.MappingsScreen;
import dev.utherhel.attributification.gui.network.*;
import dev.utherhel.attributification.neoforge.gui.AttributificationPayloadHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side setup that wires network payload callbacks to open the common {@link MappingsScreen}.
 */
@EventBusSubscriber(modid = Attributifaction.MOD_ID, value = Dist.CLIENT)
public final class AttributificationClientGuiSetup {

    private AttributificationClientGuiSetup() {}

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // ── Set screen callbacks ────────────────────────────────────
            MappingsScreen.callbacks = new MappingsScreen.Callbacks() {
                @Override
                public void requestFiles() {
                    PacketDistributor.sendToServer(
                        new RequestMappingFilesPayload()
                    );
                }

                @Override
                public void requestFileContent(String fileName) {
                    PacketDistributor.sendToServer(
                        new RequestMappingFileContentPayload(fileName)
                    );
                }

                @Override
                public void saveFile(String fileName, String jsonContent) {
                    PacketDistributor.sendToServer(
                        new SaveMappingFilePayload(fileName, jsonContent)
                    );
                }

                @Override
                public void deleteFile(String fileName) {
                    PacketDistributor.sendToServer(
                        new DeleteMappingFilePayload(fileName)
                    );
                }

                @Override
                public void requestAttributesList() {
                    PacketDistributor.sendToServer(
                        new RequestAttributesListPayload()
                    );
                }

                @Override
                public void convertLegacyMappings() {
                    PacketDistributor.sendToServer(
                        new ConvertLegacyMappingsPayload()
                    );
                }
            };

            // ── OpenGuiPayload → open the mappings screen ──────────────
            AttributificationPayloadHandler.onOpenGui = payload -> {
                MappingsScreen.deliverOpen();
            };

            // ── MappingFilesListPayload → deliver to screen ────────────
            AttributificationPayloadHandler.onMappingFilesList = payload -> {
                MappingsScreen.deliverFilesList(payload.files());
            };

            // ── MappingFileContentPayload → deliver to screen ──────────
            AttributificationPayloadHandler.onMappingFileContent = payload -> {
                MappingsScreen.deliverFileContent(
                    payload.fileName(),
                    payload.jsonContent()
                );
            };

            // ── AttributesListPayload → cache for the search popup ─────
            AttributificationPayloadHandler.onAttributesList = payload -> {
                MappingsScreen.deliverAttributes(payload.attributes());
            };

            // ── LegacyMappingsFoundPayload → deliver to screen ─────────
            AttributificationPayloadHandler.onLegacyMappingsFound = payload -> {
                MappingsScreen.deliverLegacyCount(payload.count());
            };
        });
    }
}
