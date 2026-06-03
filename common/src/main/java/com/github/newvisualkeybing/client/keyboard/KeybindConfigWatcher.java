package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Daemon-thread file watcher for the mod's config directory. Reloads stores when their
 * backing JSON changes externally (git pull, manual edit, sync from another machine).
 *
 * <p>Distinguishes our own writes from external changes by comparing the on-disk content
 * to the store's current serialization — equality means we just wrote it ourselves. This
 * avoids the brittle "track our last mtime" approach which races with the watch event
 * delivery.
 */
public final class KeybindConfigWatcher {

    private static volatile KeybindConfigWatcher INSTANCE;

    public static KeybindConfigWatcher global() {
        KeybindConfigWatcher local = INSTANCE;
        if (local == null) {
            synchronized (KeybindConfigWatcher.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new KeybindConfigWatcher();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    private final Path dir;
    private WatchService service;
    private final Map<String, Watched> watches = new ConcurrentHashMap<>();

    private KeybindConfigWatcher() {
        Path root = Minecraft.getInstance().options.getFile().toPath().toAbsolutePath().getParent();
        if (root == null) root = Path.of(".");
        this.dir = root.resolve("config").resolve(Constants.MOD_ID);
        try {
            Files.createDirectories(dir);
            this.service = FileSystems.getDefault().newWatchService();
            dir.register(service,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            Thread t = new Thread(this::run, "newvisualkeybing-config-watcher");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            Constants.LOG.warn("Config watcher unavailable, hot-reload disabled: {}", e.toString());
            this.service = null;
        }
    }

    /**
     * Register {@code fileName} for hot reload. {@code currentSerialization} should
     * return the exact JSON string the store would write today (used to detect
     * self-writes); {@code onExternalChange} runs on the client thread when the file
     * differs from that snapshot.
     */
    public void watch(String fileName, Supplier<String> currentSerialization, Runnable onExternalChange) {
        if (fileName == null || onExternalChange == null) return;
        watches.put(fileName, new Watched(currentSerialization, onExternalChange));
    }

    private void run() {
        if (service == null) return;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = service.take();
                // Editors often emit several events per save (temp file rename, mtime
                // update, etc.); a short coalescing window collapses those bursts so we
                // don't reload twice for one logical change.
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                    Object ctx = event.context();
                    if (!(ctx instanceof Path relative)) continue;
                    String name = relative.getFileName().toString();
                    Watched watched = watches.get(name);
                    if (watched == null) continue;
                    dispatchIfChanged(name, watched);
                }
                if (!key.reset()) break;
            }
        } catch (InterruptedException | ClosedWatchServiceException ignored) {
        }
    }

    private void dispatchIfChanged(String name, Watched watched) {
        Path full = dir.resolve(name);
        String onDisk;
        try {
            if (!Files.isRegularFile(full)) return;
            onDisk = Files.readString(full, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        String inMemory;
        try {
            inMemory = watched.serializer == null ? null : watched.serializer.get();
        } catch (Throwable e) {
            inMemory = null;
        }
        if (inMemory != null && normalize(onDisk).equals(normalize(inMemory))) {
            return; // our own write — ignore
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(watched.onExternalChange);
    }

    private static String normalize(String json) {
        return json == null ? "" : json.trim();
    }

    private record Watched(Supplier<String> serializer, Runnable onExternalChange) {}
}
