package dev.orbitdisk.app;

import java.util.Comparator;
import java.util.Locale;

/** StorageStats dataBytes includes cacheBytes: cache must never be added twice. */
public final class AppStorageEntry {
    public final String packageName, name, error;
    public final boolean system;
    public final long appBytes, dataBytes, cacheBytes;
    public AppStorageEntry(String pkg, String name, boolean system, long app, long data, long cache, String error) {
        this.packageName=pkg; this.name=name; this.system=system;
        this.appBytes=app; this.dataBytes=data; this.cacheBytes=cache; this.error=error;
    }
    public boolean available() { return error==null && appBytes>=0 && dataBytes>=0 && cacheBytes>=0 && appBytes<=Long.MAX_VALUE-dataBytes; }
    public long total() { return available()?appBytes+dataBytes:-1; }
    public boolean matches(String query, boolean includeSystem) {
        String q=query.toLowerCase(Locale.ROOT);
        return (includeSystem || !system) && (name.toLowerCase(Locale.ROOT).contains(q) || packageName.toLowerCase(Locale.ROOT).contains(q));
    }
    public static final Comparator<AppStorageEntry> ORDER=(a,b)-> {
        int size=Long.compare(b.total(),a.total());
        if(size!=0)return size;
        int name=a.name.compareToIgnoreCase(b.name);
        return name!=0?name:a.packageName.compareTo(b.packageName);
    };
}
