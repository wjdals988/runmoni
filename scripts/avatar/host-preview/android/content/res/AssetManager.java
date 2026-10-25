package android.content.res;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AssetManager {
    private final Path root;
    public AssetManager(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public InputStream open(String name) throws IOException {
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root)) throw new IOException("Asset escaped local root: " + name);
        return Files.newInputStream(path);
    }
}
