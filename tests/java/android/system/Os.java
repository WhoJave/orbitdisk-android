package android.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/** JVM-only lstat: never dereference even dangling or cyclic symbolic links. */
public final class Os {
    private Os() {}
    public static StructStat lstat(String path) throws ErrnoException {
        try {
            BasicFileAttributes attrs = Files.readAttributes(Paths.get(path),
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            int mode = attrs.isSymbolicLink() ? OsConstants.S_IFLNK
                    : attrs.isDirectory() ? OsConstants.S_IFDIR
                    : attrs.isRegularFile() ? OsConstants.S_IFREG : 0;
            return new StructStat(mode, attrs.size());
        } catch (IOException e) {
            throw new ErrnoException("lstat", e);
        }
    }
}
