package dev.orbitdisk.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Standalone deterministic integration tests; all fixtures are disposable. */
public final class StorageScannerTest {
    private static final StorageScanner SCANNER = new StorageScanner();
    private static int passed;
    private interface Test { void run(Path dir) throws Exception; }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Path file(Path path, int size) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.write(path, new byte[size]);
    }
    private static StorageScanner.Result scan(Path root) throws Exception {
        return SCANNER.scan(root.toFile(), new AtomicBoolean(), null);
    }
    private static void run(String name, Test test) throws Exception {
        Path dir = Files.createTempDirectory("orbitdisk-test-");
        try {
            test.run(dir);
            passed++;
            System.out.println("PASS " + name);
        } finally {
            // Files.walk does not follow links, so external targets stay untouched.
            try (Stream<Path> paths = Files.walk(dir)) {
                Path[] entries = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                for (Path entry : entries) Files.delete(entry);
            }
        }
    }
    private static void sorted(StorageScanner.Node node) {
        for (int i = 1; i < node.children.size(); i++) {
            StorageScanner.Node a = node.children.get(i - 1), b = node.children.get(i);
            check(a.size > b.size || (a.size == b.size && a.path.compareTo(b.path) <= 0),
                    "children must sort by size descending then path: " + node.path);
        }
        for (StorageScanner.Node child : node.children) sorted(child);
    }
    private static void cancelled(Test action, Path dir) throws Exception {
        try {
            action.run(dir);
            throw new AssertionError("expected CancellationException");
        } catch (CancellationException expected) {
            // Correct failure contract; partial results must not escape.
        }
    }
    public static void main(String[] args) throws Exception {
        run("total bytes, recursive directory sorting, repeat invocation", dir -> {
            file(dir.resolve("small/a"), 4);
            file(dir.resolve("large/b"), 19);
            file(dir.resolve("large/a"), 7);
            file(dir.resolve("large/equal"), 7);
            file(dir.resolve("root"), 9);
            file(dir.resolve("zero"), 0);
            Files.createDirectory(dir.resolve("empty"));
            for (int i = 0; i < 2; i++) {
                StorageScanner.Result r = scan(dir);
                check(r.root.directory && r.root.size == 46, "recursive byte total");
                check(r.files == 6 && r.folders == 4 && r.skipped == 0, "entry counts");
                check(r.root.children.get(0).name.equals("large"), "largest directory first");
                check(r.largest.size() == 6, "largest contains only files");
                sorted(r.root);
            }
        });
        run("largest top100, descending order and deterministic ties", dir -> {
            List<Path> expected = new ArrayList<>();
            for (int i = 0; i < 150; i++) {
                expected.add(file(dir.resolve(String.format("file-%03d", i)), i / 3));
            }
            expected.sort((a, b) -> {
                try {
                    int order = Long.compare(Files.size(b), Files.size(a));
                    return order != 0 ? order : a.toString().compareTo(b.toString());
                } catch (IOException e) { throw new RuntimeException(e); }
            });
            StorageScanner.Result r = scan(dir);
            check(r.files == 150 && r.largest.size() == 100, "top100 limit");
            check(r.root.size == 3675, "all files count toward total, not just top100");
            for (int i = 0; i < 100; i++) {
                StorageScanner.Node n = r.largest.get(i);
                check(!n.directory && n.path.equals(expected.get(i).toString()), "top100 rank " + i);
                check(n.size == Files.size(expected.get(i)), "top100 bytes " + i);
            }
            sorted(r.root);
        });
        run("symlink file, directory, dangling, cycle and root skipped", dir -> {
            Path root = Files.createDirectory(dir.resolve("root"));
            Path outside = file(dir.resolve("outside/data"), 999);
            file(root.resolve("real"), 5);
            Files.createSymbolicLink(root.resolve("file-link"), outside);
            Files.createSymbolicLink(root.resolve("dir-link"), outside.getParent());
            Files.createSymbolicLink(root.resolve("dangling"), dir.resolve("missing"));
            Files.createSymbolicLink(root.resolve("cycle"), root);
            StorageScanner.Result r = scan(root);
            check(r.root.size == 5 && r.files == 1 && r.folders == 1, "links not traversed");
            check(r.skipped == 4 && r.root.children.size() == 1 && r.largest.size() == 1,
                    "links excluded from tree and top100");
            r = scan(root.resolve("dir-link"));
            check(r.root == null && r.skipped == 1 && r.files == 0 && r.folders == 0
                    && r.largest.isEmpty(), "symlink root skipped");
            check(Files.size(outside) == 999, "external target unchanged");
        });
        run("pre-cancelled request", dir -> {
            file(dir.resolve("data"), 1);
            cancelled(p -> SCANNER.scan(p.toFile(), new AtomicBoolean(true), null), dir);
            check(scan(dir).files == 1, "cancelled scan must not poison subsequent scans");
        });
        run("cancellation during progress callback", dir -> {
            for (int i = 0; i < 300; i++) file(dir.resolve("file-" + i), 1);
            AtomicBoolean flag = new AtomicBoolean();
            int[] callbacks = {0};
            cancelled(p -> SCANNER.scan(p.toFile(), flag, (count, path) -> {
                check(count == 256 && Files.isRegularFile(java.nio.file.Paths.get(path)),
                        "progress count/path at first callback");
                callbacks[0]++;
                flag.set(true);
            }), dir);
            check(callbacks[0] == 1, "cancel immediately after first callback");
            check(scan(dir).files == 300, "fresh scan after in-flight cancellation");
        });
        run("thread interruption cancellation", dir -> {
            Thread.currentThread().interrupt();
            try {
                cancelled(p -> scan(p), dir);
                check(Thread.currentThread().isInterrupted(), "interrupt status preserved");
            } finally {
                Thread.interrupted();
            }
            check(scan(dir).folders == 1, "scan works after interrupt cleared");
        });
        run("nonexistent root", dir -> {
            StorageScanner.Result r = scan(dir.resolve("missing"));
            check(r.root == null && r.files == 0 && r.folders == 0 && r.skipped == 1
                    && r.largest.isEmpty(), "missing root produces empty skipped result");
        });
        System.out.println("OK: " + passed + " scanner tests passed");
    }
}
