package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;

public class ArcQuestJournalTabElement extends ArcGuiElement {
    private final QuestJournalScreen screen;
    private float tabSlideAnim = 0f;
    private float tabWidthAnim = 0f;

    public ArcQuestJournalTabElement(QuestJournalScreen screen) {
        super(0, 38, JournalConstants.LIST_WIDTH, JournalConstants.TAB_HEIGHT);
        this.screen = screen;
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        this.x = JournalConstants.LIST_MARGIN - (int) screen.getJournalSlideOffset();
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        int safeAlpha = (int) (255 * screen.getEffectiveAlpha() * inheritedOpacity);
        int theme = screen.getThemeColor();
        float targetTabX = 0;
        float currentTabX = x;
        float targetTabW = 0;

        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            int tabWidth = tabWidth(tab);
            if (tab == screen.getCurrentTab()) {
                targetTabX = currentTabX;
                targetTabW = tabWidth;
            }
            currentTabX += tabWidth + 4;
        }

        if (tabWidthAnim <= 0.1f) {
            tabSlideAnim = targetTabX;
            tabWidthAnim = targetTabW;
        }
        float lerpFactor = Math.min(1.0f, screen.getDt() * 15f);
        tabSlideAnim += (targetTabX - tabSlideAnim) * lerpFactor;
        tabWidthAnim += (targetTabW - tabWidthAnim) * lerpFactor;

        currentTabX = x;
        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            String label = tabLabel(tab);
            int tabWidth = tabWidth(tab);
            boolean hovered = context.mouseX() >= currentTabX && context.mouseX() <= currentTabX + tabWidth && context.mouseY() >= y && context.mouseY() <= y + height;
            int textColor = tab == screen.getCurrentTab()
                    ? ArcDrawUtil.withAlpha(0xFFFFFF, safeAlpha)
                    : hovered ? ArcDrawUtil.withAlpha(0xDDDDDD, safeAlpha) : ArcDrawUtil.withAlpha(0x888888, safeAlpha);
            if (safeAlpha > 8) {
                graphics.drawString(screen.getFont(), label, (int) currentTabX + 8, y + (height - screen.getFont().lineHeight) / 2, textColor, true);
            }
            currentTabX += tabWidth + 4;
        }

        if ((int) (255 * screen.getEffectiveAlpha()) > 8) {
            graphics.fill((int) tabSlideAnim, y + height - 2, (int) (tabSlideAnim + tabWidthAnim), y + height, ArcDrawUtil.withAlpha(theme, (int) (255 * screen.getEffectiveAlpha())));
        }
    }

    @Override
    public boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (button != 0 || screen.isJournalClosing()) return false;
        int tabX = x;
        for (JournalTypes.Tab tab : JournalTypes.Tab.values()) {
            int tabWidth = tabWidth(tab);
            if (mouseX >= tabX && mouseX <= tabX + tabWidth && mouseY >= y && mouseY <= y + height) {
                if (screen.getCurrentTab() != tab) {
                    screen.setCurrentTab(tab);
                    screen.playClick();
                }
                return true;
            }
            tabX += tabWidth + 4;
        }
        return false;
    }

    private String tabLabel(JournalTypes.Tab tab) {
        return Component.translatable(switch (tab) {
            case ACTIVE -> "arc_quest.gui.journal.tab.active";
            case COMPLETED -> "arc_quest.gui.journal.tab.completed";
            case FAILED -> "arc_quest.gui.journal.tab.failed";
        }).getString();
    }

    private int tabWidth(JournalTypes.Tab tab) {
        return screen.getFont().width(tabLabel(tab)) + 16;
    }
}
