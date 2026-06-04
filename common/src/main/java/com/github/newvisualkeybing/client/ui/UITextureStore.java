package com.github.newvisualkeybing.client.ui;

import com.github.newvisualkeybing.Constants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads user-supplied UI textures for the {@link UITheme.Skin#CUSTOM} skin and draws them.
 *
 * <p>Textures live under {@code config/newvisualkeybing/ui_textures/}, organised into <b>packs</b>:
 * <ul>
 *   <li><b>Loose files</b> placed directly in the folder form the implicit "default" pack.</li>
 *   <li>A <b>subfolder</b> containing PNGs is a pack named after the folder.</li>
 *   <li>A <b>.zip archive</b> is a pack named after the file (textures may sit at the zip root or
 *       inside a single wrapping folder).</li>
 * </ul>
 * The discovered packs form an in-memory index ({@link #packs()}); exactly one is active at a time
 * (its id is persisted by the caller and passed to {@link #reload(String)}). Each {@link UITextureSlot}
 * maps to a {@code <id>.png}; an optional {@code pack.json} overrides per-slot nine-slice borders,
 * tinting, and scale mode. Missing slots fall back to the vanilla procedural look.
 *
 * <p>{@link #reload(String)} must run on the render thread (it uploads GL textures).
 */
public final class UITextureStore {

    /** Stable id of the implicit pack made of loose files in the root folder. */
    public static final String LOOSE_ID = "@loose";

    public enum PackType { LOOSE, FOLDER, ZIP }

    /** One entry in the pack index. */
    public record PackInfo(String id, String displayName, PackType type, Path path) {}

    private static volatile UITextureStore INSTANCE;

    public static UITextureStore global() {
        UITextureStore local = INSTANCE;
        if (local == null) {
            synchronized (UITextureStore.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new UITextureStore();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    private record Loaded(ResourceLocation id, int width, int height, int border,
                          UITextureSlot.ScaleMode mode, boolean tint, DynamicTexture texture) {}

    private final Path dir;
    private final Map<UITextureSlot, Loaded> loaded = new EnumMap<>(UITextureSlot.class);
    private volatile List<PackInfo> packs = List.of();
    private volatile String activePackId;
    private boolean everLoaded;

    private UITextureStore() {
        Path root = Minecraft.getInstance().options.getFile().toPath().toAbsolutePath().getParent();
        if (root == null) root = Path.of(".");
        this.dir = root.resolve("config").resolve(Constants.MOD_ID).resolve("ui_textures");
        ensureScaffold();
    }

    /** Absolute path of the texture folder, for user-facing notices. */
    public Path directory() {
        return dir;
    }

    /** Number of slots currently loaded (after the most recent reload). */
    public int loadedCount() {
        return loaded.size();
    }

    public boolean has(UITextureSlot slot) {
        return loaded.containsKey(slot);
    }

    /** The discovered pack index (unmodifiable snapshot from the last reload). */
    public List<PackInfo> packs() {
        return List.copyOf(packs);
    }

    /** Id of the pack currently loaded, or {@code null} if none. */
    public String activePackId() {
        return activePackId;
    }

    /** Display name of the active pack, or {@code "—"} if none. */
    public String activePackName() {
        for (PackInfo p : packs) {
            if (p.id().equals(activePackId)) return p.displayName();
        }
        return "—";
    }

    /** Id of the next pack in the index (wrapping), or {@code null} if there are no packs. */
    public String nextPackId() {
        if (packs.isEmpty()) return null;
        int idx = -1;
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).id().equals(activePackId)) { idx = i; break; }
        }
        return packs.get((idx + 1) % packs.size()).id();
    }

    /** Loads once on first use so the CUSTOM skin works before an explicit reload. */
    public void ensureLoaded(String desiredPackId) {
        if (!everLoaded) reload(desiredPackId);
    }

    /**
     * Rediscover the pack index, resolve {@code desiredPackId} against it (falling back to the loose
     * default, then the first pack), and (re)register that pack's textures. Previous textures are
     * released first. Must run on the render thread.
     */
    public void reload(String desiredPackId) {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        for (Loaded old : loaded.values()) {
            tm.release(old.id());
            old.texture().close();
        }
        loaded.clear();

        ensureScaffold();
        packs = discover();
        PackInfo active = resolveActive(desiredPackId);
        activePackId = active == null ? null : active.id();
        if (active != null) {
            Constants.LOG.info("[NewVisualKeybing] Loading UI texture pack '{}' ({}); {} pack(s) indexed",
                    active.id(), active.type(), packs.size());
            loadPack(active, tm);
        } else {
            Constants.LOG.info("[NewVisualKeybing] No UI texture packs found in {}", dir);
        }
        everLoaded = true; // only after a full attempt, so a failed run can be retried
    }

    /** Build the pack index from loose files, subfolders, and .zip archives in the folder. */
    private List<PackInfo> discover() {
        List<PackInfo> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        if (looseHasAnySlot()) {
            out.add(new PackInfo(LOOSE_ID, tr("newvisualkeybing.ui_textures.pack.default"), PackType.LOOSE, dir));
        }
        try (var stream = Files.list(dir)) {
            stream.sorted().forEach(p -> {
                String fn = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    if (!fn.equals(LOOSE_ID) && folderHasPng(p)) out.add(new PackInfo(fn, fn, PackType.FOLDER, p));
                } else if (fn.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    String id = fn.substring(0, fn.length() - 4);
                    if (!id.equals(LOOSE_ID)) out.add(new PackInfo(id, id, PackType.ZIP, p));
                }
            });
        } catch (IOException e) {
            Constants.LOG.warn("[NewVisualKeybing] Failed to list ui_textures folder", e);
        }
        return out;
    }

    private PackInfo resolveActive(String desiredPackId) {
        if (packs.isEmpty()) return null;
        if (desiredPackId != null && !desiredPackId.isBlank()) {
            for (PackInfo p : packs) {
                if (p.id().equals(desiredPackId)) return p;
            }
        }
        for (PackInfo p : packs) {
            if (p.id().equals(LOOSE_ID)) return p;
        }
        return packs.get(0);
    }

    private boolean looseHasAnySlot() {
        for (UITextureSlot s : UITextureSlot.values()) {
            if (Files.isRegularFile(dir.resolve(s.fileName()))) return true;
        }
        return false;
    }

    private static boolean folderHasPng(Path folder) {
        try (var s = Files.list(folder)) {
            return s.anyMatch(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"));
        } catch (IOException e) {
            return false;
        }
    }

    /** Load every slot PNG from the given pack's source, registering GL textures. */
    private void loadPack(PackInfo pack, TextureManager tm) {
        try (PackSource src = openSource(pack)) {
            Map<String, JsonObject> meta = readPackMeta(src);
            warnUnknownPackKeys(meta);
            for (UITextureSlot slot : UITextureSlot.values()) {
                if (!src.has(slot.fileName())) continue;
                NativeImage image = null;
                try (InputStream in = src.open(slot.fileName())) {
                    image = NativeImage.read(in);
                    int w = image.getWidth();
                    int h = image.getHeight();
                    if (w <= 0 || h <= 0) {
                        Constants.LOG.warn("[NewVisualKeybing] UI texture {} has invalid size {}x{}", slot.fileName(), w, h);
                        continue; // finally closes the image
                    }
                    JsonObject m = meta.get(slot.id());
                    int border = m != null && m.has("border") ? m.get("border").getAsInt() : slot.defaultBorder();
                    boolean tint = m != null && m.has("tint") ? m.get("tint").getAsBoolean() : slot.tintable();
                    UITextureSlot.ScaleMode mode = parseMode(m, slot.scaleMode());
                    border = Math.max(0, Math.min(border, Math.min(w, h) / 2));

                    DynamicTexture tex = new DynamicTexture(image);
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "ui_custom/" + slot.id());
                    tm.register(id, tex);
                    loaded.put(slot, new Loaded(id, w, h, border, mode, tint, tex));
                    image = null; // ownership transferred to the DynamicTexture; don't close below
                } catch (IOException | RuntimeException e) {
                    Constants.LOG.warn("[NewVisualKeybing] Failed to load UI texture {}", slot.fileName(), e);
                } finally {
                    if (image != null) image.close(); // free native memory on any failure path
                }
            }
        } catch (IOException | RuntimeException e) {
            Constants.LOG.warn("[NewVisualKeybing] Failed to open UI texture pack '{}'", pack.id(), e);
        }
    }

    // --- Pack sources (folder or zip) ---------------------------------------------------------

    /** A flat name→stream provider so {@link #loadPack} ignores whether the pack is a folder or zip. */
    private interface PackSource extends AutoCloseable {
        boolean has(String fileName);
        InputStream open(String fileName) throws IOException;
        @Override void close();
    }

    private PackSource openSource(PackInfo pack) throws IOException {
        return pack.type() == PackType.ZIP ? new ZipSource(pack.path()) : new FolderSource(pack.path());
    }

    private static final class FolderSource implements PackSource {
        private final Path root;

        FolderSource(Path root) { this.root = root; }

        @Override public boolean has(String fileName) { return Files.isRegularFile(root.resolve(fileName)); }

        @Override public InputStream open(String fileName) throws IOException { return Files.newInputStream(root.resolve(fileName)); }

        @Override public void close() {}
    }

    private static final class ZipSource implements PackSource {
        private final ZipFile zip;
        // Lower-cased bare file name -> shallowest matching entry, so both flat zips and zips with a
        // single wrapping folder ("mypack/key.png") resolve "key.png" case-insensitively.
        private final Map<String, ZipEntry> byName = new HashMap<>();

        ZipSource(Path file) throws IOException {
            ZipFile z = new ZipFile(file.toFile());
            try {
                var entries = z.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.isDirectory()) continue;
                    String name = e.getName();
                    String base = name.substring(name.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                    ZipEntry prev = byName.get(base);
                    if (prev == null || depth(name) < depth(prev.getName())) byName.put(base, e);
                }
            } catch (RuntimeException ex) {
                try { z.close(); } catch (IOException ignored) {} // don't leak the handle on a bad zip
                throw ex;
            }
            this.zip = z;
        }

        private static int depth(String n) {
            int c = 0;
            for (int i = 0; i < n.length(); i++) if (n.charAt(i) == '/') c++;
            return c;
        }

        @Override public boolean has(String fileName) { return byName.containsKey(fileName.toLowerCase(Locale.ROOT)); }

        @Override public InputStream open(String fileName) throws IOException { return zip.getInputStream(byName.get(fileName.toLowerCase(Locale.ROOT))); }

        @Override public void close() {
            try { zip.close(); } catch (IOException ignored) {}
        }
    }

    /** Warn (once per reload) about pack.json keys that don't name a real slot — usually typos. */
    private static void warnUnknownPackKeys(Map<String, JsonObject> meta) {
        if (meta.isEmpty()) return;
        java.util.Set<String> valid = new java.util.HashSet<>();
        for (UITextureSlot s : UITextureSlot.values()) valid.add(s.id());
        for (String key : meta.keySet()) {
            if (!valid.contains(key)) {
                Constants.LOG.warn("[NewVisualKeybing] pack.json: unknown slot \"{}\" ignored", key);
            }
        }
    }

    /**
     * Draw {@code slot} into the rectangle. Returns false if the slot has no texture, so callers can
     * fall back to procedural drawing.
     */
    public boolean draw(UITextureSlot slot, GuiGraphics g, int x, int y, int w, int h) {
        return drawTinted(slot, g, x, y, w, h, 0xFFFFFFFF);
    }

    public boolean drawTinted(UITextureSlot slot, GuiGraphics g, int x, int y, int w, int h, int argb) {
        Loaded t = loaded.get(slot);
        if (t == null || w <= 0 || h <= 0) return false;
        // 1.21.2 removed GuiGraphics.setColor; the tint is now a per-quad ARGB argument to blit
        // (white-opaque 0xFFFFFFFF = untinted), threaded through to each region draw below.
        switch (t.mode()) {
            case STRETCH -> blitRegion(g, t.id(), x, y, w, h, 0f, 0f, t.width(), t.height(), t.width(), t.height(), argb);
            case TILE -> drawTiled(g, t, x, y, w, h, argb);
            case NINE_SLICE -> drawNineSlice(g, t, x, y, w, h, argb);
        }
        return true;
    }

    /**
     * Draw a key cap for the given status colour: prefers a per-status override texture (drawn
     * untinted), else the generic tinted {@code key}. Returns false if neither is present.
     */
    public boolean drawKeyFace(GuiGraphics g, int x, int y, int w, int h,
                               UITextureSlot statusOverride, int tintColor) {
        if (statusOverride != null && has(statusOverride)) {
            return draw(statusOverride, g, x, y, w, h);
        }
        return drawTinted(UITextureSlot.KEY, g, x, y, w, h, tintColor);
    }

    public boolean hasAnyKeyTexture() {
        return has(UITextureSlot.KEY) || has(UITextureSlot.KEY_FREE) || has(UITextureSlot.KEY_SELF)
                || has(UITextureSlot.KEY_OTHER) || has(UITextureSlot.KEY_COMBO) || has(UITextureSlot.KEY_CONFLICT);
    }

    private void drawTiled(GuiGraphics g, Loaded t, int x, int y, int w, int h, int argb) {
        for (int ty = 0; ty < h; ty += t.height()) {
            int dh = Math.min(t.height(), h - ty);
            for (int tx = 0; tx < w; tx += t.width()) {
                int dw = Math.min(t.width(), w - tx);
                blitRegion(g, t.id(), x + tx, y + ty, dw, dh, 0f, 0f, dw, dh, t.width(), t.height(), argb);
            }
        }
    }

    private void drawNineSlice(GuiGraphics g, Loaded t, int x, int y, int w, int h, int argb) {
        int tw = t.width();
        int th = t.height();
        // Clamp the corner inset by both the target and the source, so a small image can never
        // produce a negative source mid-region (the border is already source-clamped at load too).
        int b = Math.max(0, Math.min(t.border(), Math.min(Math.min(w, h), Math.min(tw, th)) / 2));
        if (b <= 0) {
            blitRegion(g, t.id(), x, y, w, h, 0f, 0f, tw, th, tw, th, argb);
            return;
        }
        int midW = w - b * 2;
        int midH = h - b * 2;
        int srcMidW = tw - b * 2;
        int srcMidH = th - b * 2;
        // corners (native size)
        blitRegion(g, t.id(), x, y, b, b, 0f, 0f, b, b, tw, th, argb);
        blitRegion(g, t.id(), x + w - b, y, b, b, tw - b, 0f, b, b, tw, th, argb);
        blitRegion(g, t.id(), x, y + h - b, b, b, 0f, th - b, b, b, tw, th, argb);
        blitRegion(g, t.id(), x + w - b, y + h - b, b, b, tw - b, th - b, b, b, tw, th, argb);
        // edges
        if (midW > 0 && srcMidW > 0) {
            blitRegion(g, t.id(), x + b, y, midW, b, b, 0f, srcMidW, b, tw, th, argb);
            blitRegion(g, t.id(), x + b, y + h - b, midW, b, b, th - b, srcMidW, b, tw, th, argb);
        }
        if (midH > 0 && srcMidH > 0) {
            blitRegion(g, t.id(), x, y + b, b, midH, 0f, b, b, srcMidH, tw, th, argb);
            blitRegion(g, t.id(), x + w - b, y + b, b, midH, tw - b, b, b, srcMidH, tw, th, argb);
        }
        // centre
        if (midW > 0 && midH > 0 && srcMidW > 0 && srcMidH > 0) {
            blitRegion(g, t.id(), x + b, y + b, midW, midH, b, b, srcMidW, srcMidH, tw, th, argb);
        }
    }

    /**
     * 1.21.2 reworked {@code GuiGraphics.blit}: the texture's {@code RenderType} is now a leading
     * {@code Function<ResourceLocation, RenderType>}, the destination size moved to after the source
     * region, and the tint is a trailing ARGB {@code int} (the old {@code setColor} was removed). This
     * adapter preserves the pre-1.21.2 argument order the callers above use — destination (x, y, w, h),
     * then source (u, v, w, h), then texture (w, h) — and maps it onto the new signature, applying
     * {@code argb} as the per-quad colour.
     */
    private static void blitRegion(GuiGraphics g, ResourceLocation tex,
                                   int dx, int dy, int dw, int dh,
                                   float su, float sv, int sw, int sh,
                                   int tw, int th, int argb) {
        g.blit(RenderType::guiTextured, tex, dx, dy, su, sv, sw, sh, dw, dh, tw, th, argb);
    }

    private static UITextureSlot.ScaleMode parseMode(JsonObject m, UITextureSlot.ScaleMode fallback) {
        if (m == null || !m.has("scale")) return fallback;
        String s = m.get("scale").getAsString().trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "stretch" -> UITextureSlot.ScaleMode.STRETCH;
            case "tile" -> UITextureSlot.ScaleMode.TILE;
            case "nineslice", "nine_slice", "9slice" -> UITextureSlot.ScaleMode.NINE_SLICE;
            default -> fallback;
        };
    }

    private static Map<String, JsonObject> readPackMeta(PackSource src) {
        Map<String, JsonObject> out = new HashMap<>();
        if (!src.has("pack.json")) return out;
        try (InputStream in = src.open("pack.json");
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject slots = root.has("slots") && root.get("slots").isJsonObject()
                    ? root.getAsJsonObject("slots") : root;
            for (String key : slots.keySet()) {
                if (slots.get(key).isJsonObject()) out.put(key, slots.getAsJsonObject(key));
            }
        } catch (IOException | RuntimeException e) {
            Constants.LOG.warn("[NewVisualKeybing] Failed to read pack.json", e);
        }
        return out;
    }

    private void ensureScaffold() {
        try {
            Files.createDirectories(dir);
            Path readme = dir.resolve("README.md");
            if (!Files.isRegularFile(readme)) {
                try (Writer w = Files.newBufferedWriter(readme, StandardCharsets.UTF_8)) {
                    w.write(generateReadme());
                }
            }
            Path pack = dir.resolve("pack.json");
            if (!Files.isRegularFile(pack)) {
                try (Writer w = Files.newBufferedWriter(pack, StandardCharsets.UTF_8)) {
                    w.write(generatePackTemplate());
                }
            }
        } catch (IOException e) {
            Constants.LOG.warn("[NewVisualKeybing] Could not create ui_textures folder/docs", e);
        }
    }

    /** Resolve a translation key against the active language (the docs are written per-language). */
    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    /** Quote + escape a string as a JSON string literal. */
    private static String jsonStr(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(ch);
            }
        }
        return b.append("\"").toString();
    }

    private static String generatePackTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_comment\": ").append(jsonStr(tr("newvisualkeybing.ui_textures.pack.comment"))).append(",\n");
        sb.append("  \"slots\": {\n");
        UITextureSlot[] slots = UITextureSlot.values();
        for (int i = 0; i < slots.length; i++) {
            UITextureSlot s = slots[i];
            sb.append("    \"").append(s.id()).append("\": { \"border\": ").append(s.defaultBorder())
                    .append(", \"tint\": ").append(s.tintable())
                    .append(", \"scale\": \"").append(s.scaleMode().name().toLowerCase(Locale.ROOT).replace("_", ""))
                    .append("\" }").append(i < slots.length - 1 ? "," : "").append("\n");
        }
        sb.append("  }\n}\n");
        return sb.toString();
    }

    private static String generateReadme() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(tr("newvisualkeybing.ui_textures.readme.title")).append("\n\n");
        sb.append(tr("newvisualkeybing.ui_textures.readme.intro")).append("\n\n");

        sb.append("## ").append(tr("newvisualkeybing.ui_textures.readme.packs_title")).append("\n\n");
        sb.append(tr("newvisualkeybing.ui_textures.readme.packs")).append("\n\n");

        sb.append("## ").append(tr("newvisualkeybing.ui_textures.readme.howto_title")).append("\n\n");
        sb.append(tr("newvisualkeybing.ui_textures.readme.howto")).append("\n\n");

        sb.append("## ").append(tr("newvisualkeybing.ui_textures.readme.components_title")).append("\n\n");
        sb.append("| ").append(tr("newvisualkeybing.ui_textures.readme.col.file"))
                .append(" | ").append(tr("newvisualkeybing.ui_textures.readme.col.scale"))
                .append(" | ").append(tr("newvisualkeybing.ui_textures.readme.col.border"))
                .append(" | ").append(tr("newvisualkeybing.ui_textures.readme.col.tint"))
                .append(" | ").append(tr("newvisualkeybing.ui_textures.readme.col.size"))
                .append(" | ").append(tr("newvisualkeybing.ui_textures.readme.col.use")).append(" |\n");
        sb.append("|---|---|---|---|---|---|\n");
        String yes = tr("newvisualkeybing.ui_textures.readme.yes");
        String no = tr("newvisualkeybing.ui_textures.readme.no");
        for (UITextureSlot s : UITextureSlot.values()) {
            sb.append("| `").append(s.fileName()).append("` | ")
                    .append(s.scaleMode().name().toLowerCase(Locale.ROOT).replace("_", "-")).append(" | ")
                    .append(s.scaleMode() == UITextureSlot.ScaleMode.NINE_SLICE ? s.defaultBorder() + " px" : "—").append(" | ")
                    .append(s.tintable() ? yes : no).append(" | ")
                    .append(s.recommendedWidth()).append("×").append(s.recommendedHeight()).append(" px | ")
                    .append(tr(s.descKey())).append(" |\n");
        }
        sb.append("\n## ").append(tr("newvisualkeybing.ui_textures.readme.pack_title")).append("\n\n");
        sb.append(tr("newvisualkeybing.ui_textures.readme.pack_body")).append("\n");
        return sb.toString();
    }
}
