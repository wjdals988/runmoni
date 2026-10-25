package android.content;

import android.content.res.AssetManager;
import java.nio.file.Path;

public final class Context {
    private final AssetManager assets;
    public Context(Path assetRoot) { assets = new AssetManager(assetRoot); }
    public Context getApplicationContext() { return this; }
    public AssetManager getAssets() { return assets; }
}
