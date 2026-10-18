package dev.orbitdisk.app;

import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Scans accessible local storage without following symbolic links. */
public final class StorageScanner {
    private static final int MAX_DEPTH = 128;
    private static final int LARGEST_LIMIT = 100;
    private static final Comparator<Node> DESCENDING = new Comparator<Node>() {
        @Override public int compare(Node a, Node b) {
            int sizeOrder = Long.compare(b.size, a.size);
            return sizeOrder != 0 ? sizeOrder : a.path.compareTo(b.path);
        }
    };

    public static class Node {
        public String name, path;
        public long size;
        public boolean directory;
        public List<Node> children = new ArrayList<>();

        public Node(String name, String path, boolean directory) {
            this.name = name;
            this.path = path;
            this.directory = directory;
        }
    }

    public static class Result {
        public Node root;
        public long files, folders, skipped;
        public List<Node> largest = new ArrayList<>();
    }

    public interface Progress {
        void update(long files, String path);
    }

    /** Cancellation throws CancellationException; each invocation has independent state. */
    public Result scan(File root, AtomicBoolean cancelled, Progress progress) throws Exception {
        if (root == null || cancelled == null) {
            throw new IllegalArgumentException("root and cancelled must not be null");
        }
        State state = new State(cancelled, progress);
        state.result.root = state.visit(root.getAbsoluteFile(), 0);
        state.checkCancelled();
        state.result.largest.addAll(state.largest);
        state.result.largest.sort(DESCENDING);
        return state.result;
    }

    private static final class State {
        final Result result = new Result();
        final AtomicBoolean cancelled;
        final Progress progress;
        final PriorityQueue<Node> largest = new PriorityQueue<>(LARGEST_LIMIT,
                new Comparator<Node>() {
                    @Override public int compare(Node a, Node b) {
                        return DESCENDING.compare(b, a);
                    }
                });

        State(AtomicBoolean cancelled, Progress progress) {
            this.cancelled = cancelled;
            this.progress = progress;
        }

        void checkCancelled() {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Storage scan cancelled");
            }
        }

        Node visit(File file, int depth) {
            checkCancelled();
            if (depth > MAX_DEPTH) {
                result.skipped++;
                return null;
            }
            String path = file.getAbsolutePath();
            StructStat stat;
            try {
                stat = Os.lstat(path);
                if (OsConstants.S_ISLNK(stat.st_mode)) {
                    result.skipped++;
                    return null;
                }
                // Ignore sockets, devices, FIFOs and other non-storage entries.
                if (!OsConstants.S_ISDIR(stat.st_mode)
                        && !OsConstants.S_ISREG(stat.st_mode)) {
                    result.skipped++;
                    return null;
                }
                if (!file.canRead()) {
                    result.skipped++;
                    return null;
                }
            } catch (android.system.ErrnoException | SecurityException e) {
                result.skipped++;
                return null;
            }
            checkCancelled();
            String name = file.getName();
            Node node = new Node(name.isEmpty() ? path : name, path,
                    OsConstants.S_ISDIR(stat.st_mode));
            if (!node.directory) {
                node.size = Math.max(0L, stat.st_size);
                result.files++;
                if (largest.size() < LARGEST_LIMIT) {
                    largest.offer(node);
                } else if (DESCENDING.compare(node, largest.peek()) < 0) {
                    largest.poll();
                    largest.offer(node);
                }
                if (progress != null && (result.files & 255L) == 0L) {
                    progress.update(result.files, path);
                }
                checkCancelled();
                return node;
            }
            result.folders++;
            File[] entries;
            try {
                entries = file.listFiles();
            } catch (SecurityException e) {
                result.skipped++;
                return node;
            }
            checkCancelled();
            if (entries == null) {
                result.skipped++;
                return node;
            }
            for (File entry : entries) {
                checkCancelled();
                Node child = visit(entry, depth + 1);
                if (child != null) {
                    node.children.add(child);
                    // Saturate instead of wrapping for extremely large trees.
                    node.size = Long.MAX_VALUE - node.size < child.size
                            ? Long.MAX_VALUE : node.size + child.size;
                }
            }
            checkCancelled();
            node.children.sort(DESCENDING);
            return node;
        }
    }
}
