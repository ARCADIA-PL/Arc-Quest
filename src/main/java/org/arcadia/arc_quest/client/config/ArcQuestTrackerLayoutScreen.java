package org.arcadia.arc_quest.client.config;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.quest.tracker.QuestTrackerPanel;
import org.arcadia.arc_quest.client.hud.quest.tracker.TrackerLayout;
import org.arcadia.arc_quest.config.ArcQuestTrackerConfig;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** Full-screen tracker preview. Only the explicit Save action persists the draft. */
public final class ArcQuestTrackerLayoutScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int HANDLE_SIZE = 16;
    private static final double SCALE_STEP = 0.05;

    private final Screen parent;
    private final int themeColor;
    private final QuestTrackerPanel preview = new QuestTrackerPanel();
    private TrackerLayout.Settings draft;
    private TrackerLayout.Frame frame;
    private Gesture gesture = Gesture.NONE;
    private TrackerLayout.Settings dragSettings;
    private TrackerLayout.Frame dragFrame;
    private double dragMouseX;
    private double dragMouseY;
    private Rect toolbar;
    private List<FormattedCharSequence> hintLines = List.of();
    private Button smaller;
    private Button larger;
    private Button reset;
    private Button cancel;
    private Button save;

    public ArcQuestTrackerLayoutScreen(Screen parent) {
        this(parent, 0x56C8FF);
    }

    public ArcQuestTrackerLayoutScreen(Screen parent, int themeColor) {
        super(Component.translatable("gui.arc_quest.tracker_layout.title"));
        this.parent = parent;
        this.themeColor = themeColor & 0xFFFFFF;
        this.draft = ArcQuestTrackerConfig.layout();
    }

    /** Current unsaved layout, also useful to integrations inspecting the editor. */
    public TrackerLayout.Settings draftLayout() {
        return draft;
    }

    /** Screen-space preview bounds; available after the screen has initialized. */
    public TrackerLayout.Frame previewBounds() {
        return frame;
    }

    @Override
    protected void init() {
        gesture = Gesture.NONE;
        smaller = addRenderableWidget(new EditorButton(Component.literal("−"),
                button -> changeScale(draft.scale() - SCALE_STEP),
                Component.translatable("gui.arc_quest.tracker_layout.smaller")));
        larger = addRenderableWidget(new EditorButton(Component.literal("+"),
                button -> changeScale(draft.scale() + SCALE_STEP),
                Component.translatable("gui.arc_quest.tracker_layout.larger")));
        reset = addRenderableWidget(new EditorButton(
                Component.translatable("gui.arc_quest.tracker_layout.reset"), button -> {
            draft = TrackerLayout.DEFAULT;
            resolveFrame();
        }));
        cancel = addRenderableWidget(new EditorButton(CommonComponents.GUI_CANCEL, button -> onClose()));
        save = addRenderableWidget(new EditorButton(
                Component.translatable("gui.arc_quest.tracker_layout.save"), button -> {
            ArcQuestTrackerConfig.saveLayout(draft);
            onClose();
        }));
        resolveFrame();
        layoutToolbar();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the game HUD visible, so position can be judged against the hotbar
        // and other overlays. Rendering the parent would cover that reference.
        if (minecraft == null || minecraft.level == null) {
            graphics.fill(0, 0, width, height, 0xFF101720);
        }
        frame = preview.renderPreview(graphics, width, height, partialTick, draft);
        graphics.flush();
        // Item and text batches may leave a foreground depth value. The editing
        // chrome starts a separate depth pass, like the existing text-size modal.
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        } finally {
            RenderSystem.depthMask(depthWrite);
        }
        if (gesture == Gesture.NONE) layoutToolbar();
        renderSelection(graphics, mouseX, mouseY);
        renderToolbar(graphics);
        smaller.active = draft.scale() > TrackerLayout.MIN_SCALE + 0.0001;
        larger.active = draft.scale() < TrackerLayout.MAX_SCALE - 0.0001;
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.flush();
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    private void renderSelection(GuiGraphics graphics, int mouseX, int mouseY) {
        if (frame == null) return;
        int left = (int) Math.floor(frame.x());
        int top = (int) Math.floor(frame.y());
        int right = (int) Math.ceil(frame.x() + frame.width());
        int bottom = (int) Math.ceil(frame.y() + frame.height());
        boolean hovered = (toolbar == null || !toolbar.contains(mouseX, mouseY))
                && (previewContains(mouseX, mouseY) || resizeHandle().contains(mouseX, mouseY));
        int color = withAlpha(themeColor, hovered || gesture != Gesture.NONE ? 245 : 145);
        graphics.fill(left - 1, top - 1, right + 1, top, color);
        graphics.fill(left - 1, bottom, right + 1, bottom + 1, color);
        graphics.fill(left - 1, top, left, bottom, color);
        graphics.fill(right, top, right + 1, bottom, color);
        int handleColor = gesture == Gesture.RESIZE || resizeHandle().contains(mouseX, mouseY)
                ? 0xFFFFFFFF : withAlpha(themeColor, 255);
        graphics.fill(right - 7, bottom - 2, right + 1, bottom + 1, handleColor);
        graphics.fill(right - 2, bottom - 7, right + 1, bottom + 1, handleColor);
        graphics.fill(right - 7, bottom - 6, right - 4, bottom - 4, handleColor);
    }

    private void renderToolbar(GuiGraphics graphics) {
        HudAnimUtil.drawFrame(graphics, toolbar.x(), toolbar.y(), toolbar.width(), toolbar.height(),
                0xF2141922, withAlpha(themeColor, 210));
        graphics.fill(toolbar.x(), toolbar.y(), toolbar.right(), toolbar.y() + 2,
                withAlpha(themeColor, 255));
        // The title is clipped on very narrow windows; actions always stay inside.
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(1, toolbar.width() - 125)),
                toolbar.x() + 10, toolbar.y() + 13, 0xFFF4F8FF, false);
        graphics.drawCenteredString(font, Component.translatable(
                        "gui.arc_quest.mod_config.scale_value", Math.round(draft.scale() * 100)),
                toolbar.right() - 58, toolbar.y() + 13, withAlpha(themeColor, 255));
        int textY = toolbar.y() + 34;
        for (FormattedCharSequence line : hintLines) {
            graphics.drawString(font, line, toolbar.x() + 10, textY, 0xFFB5C1D0, false);
            textY += font.lineHeight + 2;
        }
    }

    private void layoutToolbar() {
        int toolbarWidth = Math.max(1, Math.min(420, width - MARGIN * 2));
        Component hint = Component.translatable("gui.arc_quest.tracker_layout.hint");
        List<FormattedCharSequence> allLines = font.split(hint, Math.max(1, toolbarWidth - 20));
        int maxHintLines = Math.max(0, (height - MARGIN * 2 - 68) / (font.lineHeight + 2));
        hintLines = allLines.subList(0, Math.min(allLines.size(), maxHintLines));
        int toolbarHeight = 68 + hintLines.size() * (font.lineHeight + 2);
        int left = Math.min(MARGIN, Math.max(0, width - toolbarWidth));
        int right = Math.max(0, width - MARGIN - toolbarWidth);
        int top = Math.min(MARGIN, Math.max(0, height - toolbarHeight));
        int bottom = Math.max(0, height - MARGIN - toolbarHeight);
        // Prefer bottom-left, then whichever corner obstructs the preview least.
        Rect[] candidates = {new Rect(left, bottom, toolbarWidth, toolbarHeight),
                new Rect(left, top, toolbarWidth, toolbarHeight),
                new Rect(right, bottom, toolbarWidth, toolbarHeight),
                new Rect(right, top, toolbarWidth, toolbarHeight)};
        toolbar = candidates[0];
        for (Rect candidate : candidates) {
            if (overlap(candidate) < overlap(toolbar)) toolbar = candidate;
        }
        position(smaller, toolbar.right() - 106, toolbar.y() + 8, 20, 20);
        position(larger, toolbar.right() - 30, toolbar.y() + 8, 20, 20);
        int buttonWidth = Math.max(1, (toolbar.width() - 32) / 3);
        int buttonY = toolbar.bottom() - 30;
        position(reset, toolbar.x() + 10, buttonY, buttonWidth, 20);
        position(cancel, toolbar.x() + 16 + buttonWidth, buttonY, buttonWidth, 20);
        position(save, toolbar.right() - 10 - buttonWidth, buttonY, buttonWidth, 20);
    }

    private double overlap(Rect rectangle) {
        if (frame == null) return 0;
        double width = Math.max(0, Math.min(rectangle.right(), frame.x() + frame.width() + 8)
                - Math.max(rectangle.x(), frame.x() - 8));
        double height = Math.max(0, Math.min(rectangle.bottom(), frame.y() + frame.height() + 8)
                - Math.max(rectangle.y(), frame.y() - 8));
        return width * height;
    }

    private static void position(Button button, int x, int y, int width, int height) {
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || toolbar != null && toolbar.contains(mouseX, mouseY)) return false;
        if (frame == null) return false;
        if (resizeHandle().contains(mouseX, mouseY)) gesture = Gesture.RESIZE;
        else if (previewContains(mouseX, mouseY)) gesture = Gesture.MOVE;
        else return false;
        dragMouseX = mouseX;
        dragMouseY = mouseY;
        dragFrame = frame;
        dragSettings = draft;
        setFocused(null);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button != 0 || gesture == Gesture.NONE) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        double dx = mouseX - dragMouseX;
        double dy = mouseY - dragMouseY;
        if (gesture == Gesture.MOVE) {
            draft = TrackerLayout.moveTo(dragSettings, width, height, guiScale(), dragFrame.contentHeight(),
                    dragFrame.x() + dx, dragFrame.y() + dy);
        } else {
            double ratio = 1 + (dx * dragFrame.width() + dy * dragFrame.height())
                    / Math.max(1, dragFrame.width() * dragFrame.width() + dragFrame.height() * dragFrame.height());
            TrackerLayout.Settings resized = new TrackerLayout.Settings(dragSettings.x(), dragSettings.y(),
                    dragSettings.scale() * ratio);
            draft = TrackerLayout.moveTo(resized, width, height, guiScale(), dragFrame.contentHeight(),
                    dragFrame.x(), dragFrame.y());
        }
        resolveFrame();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && gesture != Gesture.NONE) {
            gesture = Gesture.NONE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (scrollY != 0 && gesture == Gesture.NONE && previewContains(mouseX, mouseY)
                && (toolbar == null || !toolbar.contains(mouseX, mouseY))) {
            changeScale(draft.scale() + Math.signum(scrollY) * SCALE_STEP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (gesture == Gesture.NONE && frame != null) {
            double step = hasShiftDown() ? 10 : 1;
            double dx = keyCode == GLFW.GLFW_KEY_LEFT ? -step : keyCode == GLFW.GLFW_KEY_RIGHT ? step : 0;
            double dy = keyCode == GLFW.GLFW_KEY_UP ? -step : keyCode == GLFW.GLFW_KEY_DOWN ? step : 0;
            if (dx != 0 || dy != 0) {
                draft = TrackerLayout.moveTo(draft, width, height, guiScale(), frame.contentHeight(),
                        frame.x() + dx, frame.y() + dy);
                resolveFrame();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void changeScale(double scale) {
        if (frame == null) return;
        TrackerLayout.Settings resized = new TrackerLayout.Settings(draft.x(), draft.y(), scale);
        draft = TrackerLayout.moveTo(resized, width, height, guiScale(), frame.contentHeight(),
                frame.x(), frame.y());
        resolveFrame();
    }

    private void resolveFrame() {
        int contentHeight = frame == null ? 90 : frame.contentHeight();
        frame = TrackerLayout.resolve(width, height, guiScale(), contentHeight, draft, 0);
    }

    private double guiScale() {
        return minecraft == null ? 1 : minecraft.getWindow().getGuiScale();
    }

    private boolean previewContains(double mouseX, double mouseY) {
        return frame != null && mouseX >= frame.x() - 2 && mouseX < frame.x() + frame.width() + 2
                && mouseY >= frame.y() - 2 && mouseY < frame.y() + frame.height() + 2;
    }

    private Rect resizeHandle() {
        int x = (int) Math.round(frame.x() + frame.width()) - HANDLE_SIZE / 2;
        int y = (int) Math.round(frame.y() + frame.height()) - HANDLE_SIZE / 2;
        return new Rect(Math.max(0, Math.min(width - HANDLE_SIZE, x)),
                Math.max(0, Math.min(height - HANDLE_SIZE, y)), HANDLE_SIZE, HANDLE_SIZE);
    }

    @Override
    public void onClose() {
        gesture = Gesture.NONE;
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    private enum Gesture { NONE, MOVE, RESIZE }

    private record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }

    private final class EditorButton extends Button {
        private EditorButton(Component text, OnPress action) {
            this(text, action, text);
        }

        private EditorButton(Component text, OnPress action, Component narration) {
            super(0, 0, 20, 20, text, action, supplier -> narration.copy());
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int accent = active ? themeColor : 0x6A7888;
            boolean hovered = active && isHoveredOrFocused();
            HudAnimUtil.drawFrame(graphics, getX(), getY(), getWidth(), getHeight(),
                    hovered ? 0xFF263648 : 0xFF161E28,
                    withAlpha(hovered ? 0xFFFFFF : accent, hovered ? 225 : 145));
            graphics.fill(getX(), getY(), getX() + 2, getY() + getHeight(), withAlpha(accent, 220));
            graphics.drawCenteredString(font, getMessage(), getX() + getWidth() / 2,
                    getY() + (getHeight() - font.lineHeight) / 2 + 1,
                    active ? 0xFFF4F8FF : 0xFF7E8A98);
        }
    }
}
