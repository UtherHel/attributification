package dev.utherhel.attributification.gui.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The JSON of a mapping file inside a packet, deflate-compressed.
 * <p>
 * A plain string is limited to 32767 characters, and a packet from the client to the server to
 * 32767 bytes in all, so a large mapping file could neither be opened nor saved (the client was
 * disconnected). Compressed, a file of a few hundred kilobytes fits.
 */
public final class JsonText {

    /** What a client-to-server packet may hold, less room for the file name and the packet's own bytes. */
    public static final int MAX_COMPRESSED_TO_SERVER = 30000;
    /** Never inflate more than this: a malformed or hostile packet cannot make the receiver run out of memory. */
    private static final int MAX_INFLATED = 8 * 1024 * 1024;
    private static final int MAX_COMPRESSED = 2 * 1024 * 1024;

    private JsonText() {
    }

    public static byte[] compress(String json) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(out)) {
            deflater.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /** Whether {@code json} fits into a packet to the server. */
    public static boolean fitsToServer(String json) {
        return compress(json).length <= MAX_COMPRESSED_TO_SERVER;
    }

    public static void write(FriendlyByteBuf buf, String json) {
        buf.writeByteArray(compress(json));
    }

    public static String read(FriendlyByteBuf buf) {
        byte[] compressed = buf.readByteArray(MAX_COMPRESSED);
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            for (int n; (n = in.read(chunk)) > 0; ) {
                total += n;
                if (total > MAX_INFLATED) {
                    throw new IllegalArgumentException("mapping file larger than " + MAX_INFLATED + " bytes");
                }
                out.write(chunk, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("broken mapping file data: " + e.getMessage(), e);
        }
    }
}
