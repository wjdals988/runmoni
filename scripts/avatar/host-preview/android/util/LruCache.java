package android.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> {
    private final int maximum;
    private int size;
    private final LinkedHashMap<K, V> values = new LinkedHashMap<>(16, .75f, true);
    public LruCache(int maximum) {
        if (maximum <= 0) throw new IllegalArgumentException("Cache maximum must be positive");
        this.maximum = maximum;
    }
    protected int sizeOf(K key, V value) { return 1; }
    public synchronized V get(K key) { return values.get(key); }
    public synchronized V put(K key, V value) {
        V previous = values.put(key, value);
        size += sizeOf(key, value);
        if (previous != null) size -= sizeOf(key, previous);
        while (size > maximum && !values.isEmpty()) {
            Map.Entry<K, V> eldest = values.entrySet().iterator().next();
            size -= sizeOf(eldest.getKey(), eldest.getValue());
            values.remove(eldest.getKey());
        }
        return previous;
    }
}
