package dev.utherhel.attributification.forge.client.gui;

//? if forge || (neoforge && <1.20.5) {
/*import dev.utherhel.attributification.client.gui.MappingsScreen;
import dev.utherhel.attributification.gui.network.ConvertLegacyMappingsPayload;
import dev.utherhel.attributification.gui.network.DeleteMappingFilePayload;
import dev.utherhel.attributification.gui.network.RequestAttributesListPayload;
import dev.utherhel.attributification.gui.network.RequestMappingFileContentPayload;
import dev.utherhel.attributification.gui.network.RequestMappingFilesPayload;
import dev.utherhel.attributification.gui.network.SaveMappingFilePayload;
import dev.utherhel.attributification.forge.gui.AttributificationPayloadHandler;
//? if forge {
/^import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
^///?} else {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

@OnlyIn(Dist.CLIENT)
public final class AttributificationClientGuiSetup {

    private AttributificationClientGuiSetup() {}

    public static void init() {
        MappingsScreen.callbacks = new MappingsScreen.Callbacks() {
            @Override
            public void requestFiles() {
                AttributificationPayloadHandler.CHANNEL.sendToServer(new RequestMappingFilesPayload());
            }

            @Override
            public void requestFileContent(String fileName) {
                AttributificationPayloadHandler.CHANNEL.sendToServer(new RequestMappingFileContentPayload(fileName));
            }

            @Override
            public void saveFile(String fileName, String jsonContent) {
                AttributificationPayloadHandler.CHANNEL.sendToServer(new SaveMappingFilePayload(fileName, jsonContent));
            }

            @Override
            public void deleteFile(String fileName) {
                AttributificationPayloadHandler.CHANNEL.sendToServer(new DeleteMappingFilePayload(fileName));
            }

            @Override
            public void requestAttributesList() {
                AttributificationPayloadHandler.CHANNEL.sendToServer(new RequestAttributesListPayload());
            }

            @Override
            public void convertLegacyMappings() {
                AttributificationPayloadHandler.CHANNEL.sendToServer(new ConvertLegacyMappingsPayload());
            }
        };

        AttributificationPayloadHandler.openGuiHandler = MappingsScreen::deliverOpen;
        AttributificationPayloadHandler.filesListHandler = p -> MappingsScreen.deliverFilesList(p.files());
        AttributificationPayloadHandler.fileContentHandler = (n, c) -> MappingsScreen.deliverFileContent(n, c);
        AttributificationPayloadHandler.attributesHandler = MappingsScreen::deliverAttributes;
        AttributificationPayloadHandler.legacyCountHandler = MappingsScreen::deliverLegacyCount;

        // the server's mappings stop applying when the client leaves the server
        //? if forge {
        /^net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
        ^///?} else {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) ->
        //?}
                dev.utherhel.attributification.attribute.MappingSync.clear());
    }
}
*///?}
