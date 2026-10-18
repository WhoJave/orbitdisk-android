package android.system;

/** Only the fields read by StorageScanner are modeled. */
public final class StructStat {
    public final int st_mode;
    public final long st_size;
    public StructStat(int mode, long size) {
        st_mode = mode;
        st_size = size;
    }
}
