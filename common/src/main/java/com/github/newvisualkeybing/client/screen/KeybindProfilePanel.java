package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.ui.MCEditBox;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

final class KeybindProfilePanel {

    static final int WIDTH = 168;
    private static final int ROW_H = 22;
    private static final int BUTTON_H = 20;
    private static final int NAME_BOX_H = 18;

    private final KeybindProfileStore profileStore;
    private final Runnable rebuildEntries;
    private final NoticeSink noticeSink;
    private final Runnable releaseExternalFocus;
    private MCEditBox nameBox;
    private int lastNameSelection = Integer.MIN_VALUE;
    private boolean renaming;

    KeybindProfilePanel(KeybindProfileStore profileStore, Runnable rebuildEntries, NoticeSink noticeSink) {
        this(profileStore, rebuildEntries, noticeSink, () -> {});
    }

    KeybindProfilePanel(KeybindProfileStore profileStore, Runnable rebuildEntries, NoticeSink noticeSink,
                        Runnable releaseExternalFocus) {
        this.profileStore = profileStore;
        this.rebuildEntries = rebuildEntries;
        this.noticeSink = noticeSink;
        this.releaseExternalFocus = releaseExternalFocus == null ? () -> {} : releaseExternalFocus;
    }

    void render(GuiGraphics graphics, Font font, int x, int y, int h, int mouseX, int mouseY) {
        var colors = UITheme.colors();
        String title = Component.translatable("screen.newvisualkeybing.viewer.profile.title").getString();
        int contentY = KeybindViewerScreen.paintPanelBase(graphics, font, x, y, WIDTH, h, title);

        int nameX = x + 10;
        int nameY = contentY + 4;
        ensureNameBox(font, nameX, nameY);
        syncNameBox();
        UITheme.fillRoundedRectFast(graphics, nameX - 2, nameY - 2, WIDTH - 16, NAME_BOX_H + 4, 5, colors.inputBg());
        UITheme.drawRoundedBorderFast(graphics, nameX - 2, nameY - 2, WIDTH - 16, NAME_BOX_H + 4, 5,
                renaming || nameBox.isFocused() ? colors.accent() : colors.widgetBorder());
        nameBox.render(graphics, mouseX, mouseY, 1.0f);

        int rowY = contentY + 30;
        List<KeybindProfileStore.Profile> profiles = profileStore.profiles();
        int selected = profileStore.selectedIndex();
        int buttonTop = buttonTop(y, h);
        int rowBottom = buttonTop - 6;
        for (int i = 0; i < profiles.size() && rowY + ROW_H <= rowBottom; i++) {
            KeybindProfileStore.Profile profile = profiles.get(i);
            boolean active = i == selected;
            boolean hovered = inside(mouseX, mouseY, x + 8, rowY, WIDTH - 16, ROW_H - 2);
            int fill = active ? UITheme.lerpColor(colors.widgetBg(), colors.accent(), 0.42f)
                    : hovered ? UITheme.withAlpha(colors.widgetBg(), 0xB0)
                    : UITheme.withAlpha(colors.widgetBg(), 0x66);
            UITheme.fillRoundedRectFast(graphics, x + 8, rowY, WIDTH - 16, ROW_H - 2, 5, fill);
            if (active) {
                UITheme.fillRoundedRectFast(graphics, x + 10, rowY + 3, 2, ROW_H - 8, 1, colors.accent());
            }
            String label = fit(font, profileStore.compactProfileLabel(profile), WIDTH - 30);
            int labelX = x + 8 + (WIDTH - 16 - font.width(label)) / 2;
            graphics.drawString(font, label, labelX, textY(font, rowY, ROW_H - 2),
                    active ? colors.textPrimary() : colors.textSecondary(), false);
            rowY += ROW_H;
        }

        if (profiles.isEmpty()) {
            String empty = Component.translatable("screen.newvisualkeybing.viewer.profile.empty").getString();
            graphics.drawString(font, fit(font, empty, WIDTH - 20), x + 10,
                    textY(font, rowY, ROW_H - 2), colors.textMuted(), false);
        }

        int halfW = (WIDTH - 22) / 2;
        renderButton(graphics, font, x + 8, buttonTop, halfW, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.save").getString(),
                colors.accent(), inside(mouseX, mouseY, x + 8, buttonTop, halfW, BUTTON_H));
        renderButton(graphics, font, x + 14 + halfW, buttonTop, halfW, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.new").getString(),
                colors.accentLight(), inside(mouseX, mouseY, x + 14 + halfW, buttonTop, halfW, BUTTON_H));
        renderButton(graphics, font, x + 8, buttonTop + BUTTON_H + 5, WIDTH - 16, BUTTON_H,
                Component.translatable(renaming
                        ? "screen.newvisualkeybing.viewer.profile.rename_confirm"
                        : "screen.newvisualkeybing.viewer.profile.rename").getString(),
                colors.accent(), inside(mouseX, mouseY, x + 8, buttonTop + BUTTON_H + 5, WIDTH - 16, BUTTON_H));
        renderButton(graphics, font, x + 8, buttonTop + (BUTTON_H + 5) * 2, WIDTH - 16, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.apply").getString(),
                colors.successColor(), inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 2, WIDTH - 16, BUTTON_H));
        renderButton(graphics, font, x + 8, buttonTop + (BUTTON_H + 5) * 3, halfW, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.export").getString(),
                colors.warningColor(), inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 3, halfW, BUTTON_H));
        renderButton(graphics, font, x + 14 + halfW, buttonTop + (BUTTON_H + 5) * 3, halfW, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.import").getString(),
                colors.accent(), inside(mouseX, mouseY, x + 14 + halfW, buttonTop + (BUTTON_H + 5) * 3, halfW, BUTTON_H));
        renderButton(graphics, font, x + 8, buttonTop + (BUTTON_H + 5) * 4, WIDTH - 16, BUTTON_H,
                Component.translatable("screen.newvisualkeybing.viewer.profile.delete").getString(),
                colors.dangerColor(), inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 4, WIDTH - 16, BUTTON_H));
    }

    boolean mouseClicked(double mouseX, double mouseY, int x, int y, int h) {
        if (!inside(mouseX, mouseY, x, y, WIDTH, h)) return false;

        releaseExternalFocus.run();

        int nameX = x + 10;
        int contentY = y + 28;
        int nameY = contentY + 4;
        boolean inNameBg = inside(mouseX, mouseY, nameX - 2, nameY - 2, WIDTH - 16, NAME_BOX_H + 4);
        if (nameBox != null && inNameBg) {
            nameBox.mouseClicked(mouseX, mouseY, 0);
            nameBox.setFocused(true);
            if (profileStore.selectedProfile() != null) renaming = true;
            return true;
        }
        if (!inNameBg && nameBox != null) {
            nameBox.setFocused(false);
        }

        int buttonTop = buttonTop(y, h);
        int halfW = (WIDTH - 22) / 2;
        if (inside(mouseX, mouseY, x + 8, buttonTop, halfW, BUTTON_H)) {
            KeybindProfileStore.Profile profile = profileStore.saveCurrentProfile(nameText());
            setNameText(profile.name);
            renaming = false;
            rebuildEntries.run();
            noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.saved", profile.name).getString());
            return true;
        }
        if (inside(mouseX, mouseY, x + 14 + halfW, buttonTop, halfW, BUTTON_H)) {
            KeybindProfileStore.Profile profile = profileStore.createProfileFromCurrent(nameText());
            setNameText(profile.name);
            renaming = false;
            rebuildEntries.run();
            noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.created", profile.name).getString());
            return true;
        }
        if (inside(mouseX, mouseY, x + 8, buttonTop + BUTTON_H + 5, WIDTH - 16, BUTTON_H)) {
            if (profileStore.selectedProfile() == null) {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.no_selection").getString());
                return true;
            }
            if (!renaming) {
                beginRename();
                return true;
            }
            commitRename();
            return true;
        }
        if (inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 2, WIDTH - 16, BUTTON_H)) {
            if (profileStore.applySelectedProfile()) {
                rebuildEntries.run();
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.applied",
                        profileStore.selectedProfile().name).getString());
            } else {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.no_selection").getString());
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 3, halfW, BUTTON_H)) {
            Path path = profileStore.exportSelectedProfile();
            if (path != null) {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.exported",
                        path.getFileName().toString()).getString());
            } else {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.no_selection").getString());
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + 14 + halfW, buttonTop + (BUTTON_H + 5) * 3, halfW, BUTTON_H)) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.client.gui.screens.Screen current = mc.screen;
            mc.setScreen(new KeybindProfileImportScreen(current, imported -> {
                setNameText(imported.name);
                renaming = false;
                rebuildEntries.run();
                noticeSink.notice(Component.translatable(
                        "screen.newvisualkeybing.viewer.profile.imported", imported.name).getString());
            }));
            return true;
        }
        if (inside(mouseX, mouseY, x + 8, buttonTop + (BUTTON_H + 5) * 4, WIDTH - 16, BUTTON_H)) {
            String name = profileStore.selectedProfile() == null ? "" : profileStore.selectedProfile().name;
            if (profileStore.deleteSelectedProfile()) {
                syncNameBox(true);
                renaming = false;
                rebuildEntries.run();
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.deleted", name).getString());
            } else {
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.no_selection").getString());
            }
            return true;
        }

        int rowY = contentY + 30;
        int rowBottom = buttonTop - 6;
        List<KeybindProfileStore.Profile> profiles = profileStore.profiles();
        for (int i = 0; i < profiles.size() && rowY + ROW_H <= rowBottom; i++) {
            if (inside(mouseX, mouseY, x + 8, rowY, WIDTH - 16, ROW_H - 2)) {
                profileStore.select(i);
                setNameText(profiles.get(i).name);
                renaming = false;
                return true;
            }
            rowY += ROW_H;
        }
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        return nameBox != null && nameBox.isFocused() && nameBox.charTyped(codePoint, modifiers);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox == null || !nameBox.isFocused()) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            renaming = false;
            syncNameBox(true);
            nameBox.setFocused(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (profileStore.selectedProfile() == null) {
                KeybindProfileStore.Profile profile = profileStore.createProfileFromCurrent(nameText());
                setNameText(profile.name);
                renaming = false;
                rebuildEntries.run();
                noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.created", profile.name).getString());
            } else {
                commitRename();
            }
            return true;
        }
        return nameBox.keyPressed(keyCode, scanCode, modifiers);
    }

    private void beginRename() {
        if (nameBox == null) return;
        KeybindProfileStore.Profile profile = profileStore.selectedProfile();
        if (profile == null) return;
        releaseExternalFocus.run();
        renaming = true;
        nameBox.setValue(profile.name);
        nameBox.setFocused(true);
        nameBox.setHighlightPos(0);
        nameBox.setCursorPosition(profile.name.length());
    }

    private void commitRename() {
        KeybindProfileStore.Profile profile = profileStore.renameSelectedProfile(nameText());
        if (profile == null) {
            noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.no_selection").getString());
            return;
        }
        setNameText(profile.name);
        renaming = false;
        if (nameBox != null) nameBox.setFocused(false);
        rebuildEntries.run();
        noticeSink.notice(Component.translatable("screen.newvisualkeybing.viewer.profile.renamed", profile.name).getString());
    }

    private int buttonTop(int y, int h) {
        return y + h - BUTTON_H * 5 - 28;
    }

    private void ensureNameBox(Font font, int x, int y) {
        int textY = y + (NAME_BOX_H + 4 - font.lineHeight) / 2 - 2;
        if (nameBox == null) {
            nameBox = new MCEditBox(font, x, textY, WIDTH - 20, NAME_BOX_H,
                    Component.translatable("screen.newvisualkeybing.viewer.profile.name"))
                    .withPlaceholder(Component.translatable("screen.newvisualkeybing.viewer.profile.name_placeholder"));
            nameBox.setMaxLength(48);
            syncNameBox(true);
        }
        nameBox.setX(x);
        nameBox.setY(textY);
        nameBox.setHeight(NAME_BOX_H);
    }

    private void syncNameBox() {
        syncNameBox(false);
    }

    private void syncNameBox(boolean forced) {
        if (nameBox == null) return;
        int selection = profileStore.profiles().isEmpty() ? -1 : profileStore.selectedIndex();
        if (forced || (!nameBox.isFocused() && selection != lastNameSelection)) {
            KeybindProfileStore.Profile profile = profileStore.selectedProfile();
            nameBox.setValue(profile == null ? "" : profile.name);
            lastNameSelection = selection;
        }
    }

    private void setNameText(String value) {
        if (nameBox != null) {
            nameBox.setValue(value == null ? "" : value);
            lastNameSelection = profileStore.profiles().isEmpty() ? -1 : profileStore.selectedIndex();
        }
    }

    private String nameText() {
        return nameBox == null ? "" : nameBox.getValue();
    }

    private void renderButton(GuiGraphics graphics, Font font, int x, int y, int w, int h, String label, int accent, boolean hovered) {
        var colors = UITheme.colors();
        UITheme.fillRoundedRectFast(graphics, x, y, w, h, 4,
                UITheme.lerpColor(colors.widgetBg(), accent, hovered ? 0.42f : 0.24f));
        UITheme.drawRoundedBorderFast(graphics, x, y, w, h, 4, UITheme.withAlpha(accent, 0xA0));
        String fitted = fit(font, label, w - 8);
        graphics.drawString(font, fitted, x + (w - font.width(fitted)) / 2,
                textY(font, y, h), colors.textPrimary(), false);
    }

    private static int textY(Font font, int y, int h) {
        return y + (h - font.lineHeight) / 2;
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "..";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(suffix))) + suffix;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    interface NoticeSink {
        void notice(String message);
    }
}
