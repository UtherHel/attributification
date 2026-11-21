package dev.utherhel.attributification.forge.network;

//? if neoforge && <1.20.5 {
/*import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

// Forge's NetworkRegistry.ChannelBuilder for SimpleChannel. The accepted-version predicates are not
// used: NeoForge negotiates the channel by its version and lets clients without the mod join.
public final class NetworkRegistry {

    private NetworkRegistry() {}

    public static final class ChannelBuilder {

        private final ResourceLocation name;
        private Supplier<String> version = () -> "1";

        private ChannelBuilder(ResourceLocation name) {
            this.name = name;
        }

        public static ChannelBuilder named(ResourceLocation name) {
            return new ChannelBuilder(name);
        }

        public ChannelBuilder clientAcceptedVersions(Predicate<String> accepted) {
            return this;
        }

        public ChannelBuilder serverAcceptedVersions(Predicate<String> accepted) {
            return this;
        }

        public ChannelBuilder networkProtocolVersion(Supplier<String> version) {
            this.version = version;
            return this;
        }

        public SimpleChannel simpleChannel() {
            return new SimpleChannel(name, version.get());
        }
    }
}
*///?}
