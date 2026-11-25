import io.github.headlesshq.headlessmc.lwjgl.shadow.asm.ClassReader;
import io.github.headlesshq.headlessmc.lwjgl.shadow.asm.ClassVisitor;
import io.github.headlesshq.headlessmc.lwjgl.shadow.asm.ClassWriter;
import io.github.headlesshq.headlessmc.lwjgl.shadow.asm.ModuleVisitor;
import io.github.headlesshq.headlessmc.lwjgl.shadow.asm.Opcodes;
import io.github.headlesshq.headlessmc.lwjgl.shadow.asm.tree.ClassNode;
import io.github.headlesshq.headlessmc.lwjgl.transformer.AsmUtil;
import io.github.headlesshq.headlessmc.lwjgl.transformer.LwjglTransformer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Headless LWJGL for dev/modtest clients: the static form of HeadlessMc's patch (github.com/headlesshq/headlessmc,
 * MIT), run once per LWJGL jar and cached. Every LWJGL class goes through HeadlessMc's LwjglTransformer (what its
 * java agent does at class load), which turns the native calls into no-ops: no window, no OpenGL context, no audio.
 *
 * <p>The rewritten classes call HeadlessMc's redirection API. On Forge/NeoForge the jars become modules, so the
 * LWJGL module-info gets a {@code requires} on the API jar, which is rewritten as an automatic module with a fixed
 * name (its own module-info, which FML's jar handler would not read, is dropped). ASM is HeadlessMc's shaded copy.
 *
 * <pre>java -cp headlessmc-lwjgl-all.jar;lwjgl*.jar LwjglPatch.java MODULE --api IN OUT [IN OUT]...</pre>
 */
public final class LwjglPatch {
    private LwjglPatch() {
    }

    public static void main(String[] args) throws IOException {
        String module = args[0];
        int i = 1;
        if (args.length > i + 2 && args[i].equals("--api")) {
            api(Path.of(args[i + 1]), Path.of(args[i + 2]), module);
            i += 3;
        }
        LwjglTransformer transformer = new LwjglTransformer();
        for (; i + 1 < args.length; i += 2) {
            lwjgl(Path.of(args[i]), Path.of(args[i + 1]), transformer, module);
        }
    }

    private static void lwjgl(Path in, Path out, LwjglTransformer transformer, String module) throws IOException {
        copy(in, out, (name, data) -> {
            if (name.endsWith("module-info.class")) return requires(data, module);
            if (name.endsWith(".class") && name.contains("org/lwjgl/")) {
                ClassNode node = AsmUtil.read(data);
                transformer.transform(node);
                return AsmUtil.write(node, ClassWriter.COMPUTE_FRAMES);
            }
            return data;
        });
    }

    private static void api(Path in, Path out, String module) throws IOException {
        copy(in, out, (name, data) -> {
            if (name.endsWith("module-info.class")) return null;
            if (name.equals("META-INF/MANIFEST.MF")) {
                return ("Manifest-Version: 1.0\r\nAutomatic-Module-Name: " + module + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8);
            }
            return data;
        });
    }

    private interface Entry {
        byte[] map(String name, byte[] data) throws IOException;
    }

    private static void copy(Path in, Path out, Entry entry) throws IOException {
        Files.createDirectories(out.getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        try (InputStream is = Files.newInputStream(in); ZipInputStream zin = new ZipInputStream(is);
             OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zout = new ZipOutputStream(os)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory() || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")) continue;
                byte[] data = entry.map(name, zin.readAllBytes());
                if (data == null) continue;
                zout.putNextEntry(new ZipEntry(name));
                zout.write(data);
                zout.closeEntry();
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
    }

    private static byte[] requires(byte[] data, String module) {
        ClassWriter w = new ClassWriter(0);
        new ClassReader(data).accept(new ClassVisitor(Opcodes.ASM9, w) {
            @Override
            public ModuleVisitor visitModule(String name, int access, String version) {
                return new ModuleVisitor(Opcodes.ASM9, super.visitModule(name, access, version)) {
                    @Override
                    public void visitEnd() {
                        super.visitRequire(module, 0, null);
                        super.visitEnd();
                    }
                };
            }
        }, 0);
        return w.toByteArray();
    }
}
