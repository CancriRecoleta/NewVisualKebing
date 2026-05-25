package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.ui.MCButton;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lists every JSON file under {@code config/<modid>/exports/}, sorted newest first, and
 * lets the user pick one to import. Replaces the previous "import latest" shortcut so
 * profiles synced from other machines or pulled from version control can be selected
 * explicitly.
 */
public class KeybindProfileImportScreen extends FixedScaleScreen {

    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 28;
    private static final int ROW_H = 38;
    private static final DateTimeFormatter LABEL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Screen parent;
    private final KeybindProfileStore profileStore = KeybindProfileStore.global();
    private final Consumer<KeybindProfileStore.Profile> onImported;

    private List<KeybindProfileStore.ExportEntry> entries = List.of();
    private MCButton backButton;
    private MCButton refreshButton;
    private int scrollOffset;
    private int totalListH;
    private String notice;
    private long noticeUntil;

    public KeybindProfileImportScreen(Screen parent, Consumer<KeybindProfileStore.Profile> onImported) {
        super(Component.translatable("screen.newvisualkeybing.viewer.profile.import.title"));
        this.parent = parent;
        this.onImported = onImported == null ? p -> {} : onImported;
    }

    @Override
    protected void init() {
        super.init();
        applyFixedScaleMetrics();
        UITheme.setMode(UITheme.Mode.DARK);

        int btnGap = 6;
        int backW = 60;
        int refreshW = 96;
        int xRefresh = width - 12 - refreshW;
        int xBack = xRefresh - btnGap - backW;

        backButton = MCButton.create(xBack, 10, backW, 20,
                Component.translatable("screen.newvisualkeybing.viewer.back"), b -> onClose());
        addRenderableWidget(backButton);

        refreshButton = MCButton.create(xRefresh, 10, refreshW, 20,
                Component.translatable("screen.newvisualkeybing.viewer.profile.import.refresh"),
                b -> reload());
        addRenderableWidget(refreshButton);

        reload();
    }

    @Override
    protected void onFixedScaleMetricsChanged() {
        super.onFixedScaleMetricsChanged();
        if (backButton != null && refreshButton != null) {
            int btnGap = 6;
            int xRefresh = width - 12 - refreshButton.getWidth();
            int xBack = xRefresh - btnGap - backButton.getWidth();
            backButton.setX(xBack);
            refreshButton.setX(xRefresh);
        }
    }

    private void reload() {
        entries = profileStore.availableExports();
        totalListH = entries.size() * ROW_H;
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, totalListH - listHeight()));
    }

    private int listTop() { return HEADER_H + 6; }
    private int listHeight() { return height - HEADER_H - FOOTER_H - 12; }
    private int listX() { return 16; }
    private int listW() { return width - listX() * 2; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        applyFixedScaleMetrics();
        int fmx = fixedMouseX(mouseX);
        int fmy = fixedMouseY(mouseY);
        pushFixedScale(graphics);
        try {
            var colors = UITheme.colors();
            graphics.fill(0, 0, width, height, UITheme.withAlpha(colors.panelBg(), 0xE6));
            renderHeader(graphics);
            renderList(graphics, fmx, fmy);
            renderFooter(graphics);
            super.render(graphics, fmx, fmy, partialTick);
            renderNotice(graphics);
        } finally {
            popFixedScale(graphics);
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        var colors = UITheme.colors();
        UITheme.drawGlassPanel(graphics, 4, 4, width - 8, HEADER_H - 4, 8);
        String title = Component.translatable("screen.newvisualkeybing.viewer.profile.import.title").getString();
        String count = Component.translatable("screen.newvisualkeybing.viewer.profile.import.count",
                entries.size()).getString();
        int titleY = (HEADER_H - font.lineHeight * 2 - 3) / 2;
        graphics.drawString(font, title, 16, titleY, colors.textPrimary(), false);
        graphics.drawString(font, count, 16, titleY + font.lineHeight + 3, colors.textSecondary(), false);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        var colors = UITheme.colors();
        int top = listTop();
        int h = listHeight();
        int x = listX();
        int w = listW();
        UITheme.fillRoundedRectFast(graphics, x, top, w, h, 8, UITheme.withAlpha(colors.headerBg(), 0xC0));
        UITheme.drawRoundedBorderFast(graphics, x, top, w, h, 8, colors.widgetBorder());

        if (entries.isEmpty()) {
            String empty = Component.translatable("screen.newvisualkeybing.viewer.profile.no_exports").getString();
            graphics.drawString(font, empty, x + (w - font.width(empty)) / 2,
                    top + (h - font.lineHeight) / 2, colors.textMuted(), false);
            return;
        }

        enableFixedScissor(graphics, x + 1, top + 1, x + w - 1, top + h - 1);
        int drawY = top + 4 - scrollOffset;
        for (KeybindProfileStore.ExportEntry entry : entries) {
            if (drawY + ROW_H >= top && drawY <= top + h) {
                boolean hovered = mouseX >= x + 6 && mouseX < x + w - 6
                        && mouseY >= drawY && mouseY < drawY + ROW_H - 2;
                renderRow(graphics, entry, x + 6, drawY, w - 12, hovered);
            }
            drawY += ROW_H;
        }
        graphics.disableScissor();

        if (totalListH > h) {
            float ratio = (float) h / totalListH;
            int sbH = Math.max(20, (int) (h * ratio));
            int sbY = top + (int) ((float) scrollOffset / totalListH * h);
            UITheme.fillRoundedRectFast(graphics, x + w - 6, top, 4, h, 2, colors.scrollbarTrack());
            UITheme.fillRoundedRectFast(graphics, x + w - 6, sbY, 4, sbH, 2, colors.scrollbarThumb());
        }
    }

    private void renderRow(GuiGraphics graphics, KeybindProfileStore.ExportEntry entry,
                           int x, int y, int w, boolean hovered) {
        var colors = UITheme.colors();
        int rowH = ROW_H - 4;
        int bg = hovered ? UITheme.lerpColor(colors.widgetBg(), colors.accent(), 0.18f)
                : UITheme.withAlpha(colors.widgetBg(), 0xA8);
        UITheme.fillRoundedRectFast(graphics, x, y, w, rowH, 6, bg);
        UITheme.drawRoundedBorderFast(graphics, x, y, w, rowH, 6,
                UITheme.withAlpha(colors.accent(), hovered ? 0xC8 : 0x70));

        int textX = x + 10;
        String name = entry.profileName == null ? "(unnamed)" : entry.profileName;
        graphics.drawString(font, KeybindViewerScreen.fitToWidth(font, name, w - 200),
                textX, y + 6, colors.textPrimary(), false);

        String meta = Component.translatable("screen.newvisualkeybing.viewer.profile.import.meta",
                entry.bindingCount, entry.comboCount).getString();
        graphics.drawString(font, meta, textX, y + 6 + font.lineHeight + 3, colors.textSecondary(), false);

        String when = entry.exportedAt != null && !entry.exportedAt.isBlank()
                ? entry.exportedAt
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.modifiedAt), ZoneId.systemDefault())
                        .format(LABEL_TIME);
        int whenW = font.width(when);
        graphics.drawString(font, when, x + w - whenW - 12, y + 6, colors.textMuted(), false);

        String fileName = entry.path.getFileName().toString();
        int fileNameW = font.width(fileName);
        graphics.drawString(font, KeybindViewerScreen.fitToWidth(font, fileName, 240),
                x + w - Math.min(fileNameW, 240) - 12, y + 6 + font.lineHeight + 3,
                colors.textMuted(), false);
    }

    private void renderFooter(GuiGraphics graphics) {
        var colors = UITheme.colors();
        int y = height - FOOTER_H;
        graphics.fill(0, y, width, y + 1, colors.divider());
        String hint = Component.translatable("screen.newvisualkeybing.viewer.profile.import.hint").getString();
        graphics.drawString(font, hint, (width - font.width(hint)) / 2,
                y + (FOOTER_H - font.lineHeight) / 2, colors.textSecondary(), false);
    }

    private void renderNotice(GuiGraphics graphics) {
        if (notice == null || System.currentTimeMillis() > noticeUntil) return;
        var colors = UITheme.colors();
        int pad = 8;
        int w = font.width(notice) + pad * 2;
        int x = (width - w) / 2;
        int y = height - FOOTER_H - 28;
        UITheme.fillRoundedRectFast(graphics, x, y, w, 22, 6,
                UITheme.withAlpha(colors.panelBg(), 0xE0));
        UITheme.drawRoundedBorderFast(graphics, x, y, w, 22, 6, colors.accent());
        graphics.drawString(font, notice, x + pad, y + 7, colors.textPrimary(), false);
    }

    private void showNotice(String text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 2_500L;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        applyFixedScaleMetrics();
        double fmx = fixedMouseX(mouseX);
        double fmy = fixedMouseY(mouseY);
        int top = listTop();
        int h = listHeight();
        int x = listX();
        int w = listW();
        if (fmx >= x && fmx < x + w && fmy >= top && fmy < top + h) {
            int drawY = top + 4 - scrollOffset;
            for (KeybindProfileStore.ExportEntry entry : entries) {
                int rowBottom = drawY + ROW_H - 2;
                if (fmy >= drawY && fmy < rowBottom && fmx >= x + 6 && fmx < x + w - 6) {
                    KeybindProfileStore.Profile imported = profileStore.importExport(entry.path);
                    if (imported != null) {
                        showNotice(Component.translatable(
                                "screen.newvisualkeybing.viewer.profile.imported",
                                imported.name).getString());
                        onImported.accept(imported);
                    } else {
                        showNotice(Component.translatable(
                                "screen.newvisualkeybing.viewer.profile.import.failed",
                                entry.path.getFileName().toString()).getString());
                    }
                    return true;
                }
                drawY += ROW_H;
            }
        }
        return super.mouseClicked(fmx, fmy, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        applyFixedScaleMetrics();
        scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * ROW_H * 2), 0,
                Math.max(0, totalListH - listHeight()));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
