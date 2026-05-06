package org.arcadia.arc_quest.client.hud.quest.editor;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.quest.editor.model.EditablePhase;
import org.arcadia.arc_quest.quest.editor.service.EditableValidationIssue;

import java.util.ArrayList;
import java.util.List;

public class QuestEditorSidebar {
    private final QuestEditorScreen screen;
    private final List<IssueHitBox> issueHitBoxes = new ArrayList<>();
    private int issueListStart = 0;
    private boolean errorsOnly = false;
    private int issuesHeaderX = 0;
    private int issuesHeaderY = 0;
    private int issuesHeaderW = 0;
    private int issuesHeaderH = 0;
    private int issuesListBottomY = 0;

    public QuestEditorSidebar(QuestEditorScreen screen) {
        this.screen = screen;
    }

    public void render(GuiGraphics g, int x, int y, int width, int height, int mouseX, int mouseY) {
        issueHitBoxes.clear();
        HudRenderUtil.drawGlassPanel(g, x, y, width, height, 0x0A0D12, 180, screen.getThemeColor(), 180, 3);
        g.drawString(screen.getUiFont(), "PHASES", x + 8, y + 8, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
        int rowY = y + 24;
        for (EditablePhase phase : screen.getController().phases()) {
            boolean selected = screen.getController().selectedPhaseNodeIds().contains(phase.nodeId);
            int bg = selected ? HudAnimUtil.withAlpha(screen.getThemeColor(), 90) : HudAnimUtil.withAlpha(0x151A22, 140);
            g.fill(x + 6, rowY, x + width - 6, rowY + 24, bg);
            String title = phase.displayName == null ? phase.phaseId : phase.displayName.value;
            if (title == null || title.isBlank()) title = phase.phaseId;
            g.drawString(screen.getUiFont(), title, x + 12, rowY + 5, HudAnimUtil.withAlpha(0xFFFFFF, 255), false);
            g.drawString(screen.getUiFont(), phase.phaseId, x + 12, rowY + 14, HudAnimUtil.withAlpha(screen.getController().isUnreachable(phase.nodeId) ? 0xFF8888 : 0x888888, 255), false);
            rowY += 28;
            if (rowY > y + height - 160) break;
        }

        rowY += 8;
        String modeText = errorsOnly ? "ISSUES [ERR]" : "ISSUES [ALL]";
        issuesHeaderX = x + 6;
        issuesHeaderY = rowY - 2;
        issuesHeaderW = width - 12;
        issuesHeaderH = 16;
        g.fill(issuesHeaderX, issuesHeaderY, issuesHeaderX + issuesHeaderW, issuesHeaderY + issuesHeaderH, HudAnimUtil.withAlpha(0x10161E, 120));
        g.drawString(screen.getUiFont(), modeText, x + 8, rowY, HudAnimUtil.withAlpha(0xFFFFFF, 255), true);
        rowY += 16;

        List<EditableValidationIssue> filtered = filteredIssues();
        int visibleRows = Math.max(1, (y + height - 24 - rowY) / 20);
        int maxStart = Math.max(0, filtered.size() - visibleRows);
        if (issueListStart > maxStart) issueListStart = maxStart;
        issuesListBottomY = rowY + visibleRows * 20;

        int count = 0;
        for (int i = issueListStart; i < filtered.size() && count < visibleRows; i++) {
            EditableValidationIssue issue = filtered.get(i);
            int color = issue.severity().name().equals("ERROR") ? 0xFF8888 : 0xFFCC66;
            String text = issue.path() + " : " + issue.message();
            if (text.length() > 44) text = text.substring(0, 44) + "...";
            int itemX = x + 6;
            int itemY = rowY;
            int itemW = width - 12;
            int itemH = 18;
            boolean hovered = mouseX >= itemX && mouseY >= itemY && mouseX <= itemX + itemW && mouseY <= itemY + itemH;
            g.fill(itemX, itemY, itemX + itemW, itemY + itemH, HudAnimUtil.withAlpha(hovered ? 0x243447 : 0x151A22, hovered ? 210 : 155));
            g.drawString(screen.getUiFont(), issue.severity().name().substring(0, 1), x + 10, rowY + 5, HudAnimUtil.withAlpha(color, 255), false);
            g.drawString(screen.getUiFont(), text, x + 20, rowY + 5, HudAnimUtil.withAlpha(0xDDDDDD, 255), false);
            issueHitBoxes.add(new IssueHitBox(itemX, itemY, itemW, itemH, issue));
            if (hovered) {
                renderIssueTooltip(g, mouseX, mouseY, issue, x + width);
            }
            rowY += 20;
            count++;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseX >= issuesHeaderX && mouseY >= issuesHeaderY && mouseX <= issuesHeaderX + issuesHeaderW && mouseY <= issuesHeaderY + issuesHeaderH) {
            errorsOnly = !errorsOnly;
            issueListStart = 0;
            return true;
        }
        for (IssueHitBox hitBox : issueHitBoxes) {
            if (hitBox.contains(mouseX, mouseY)) {
                return screen.getController().selectByValidationIssue(hitBox.issue());
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < issuesHeaderX || mouseX > issuesHeaderX + issuesHeaderW) return false;
        if (mouseY < issuesHeaderY || mouseY > issuesListBottomY) return false;
        List<EditableValidationIssue> filtered = filteredIssues();
        if (filtered.isEmpty()) return false;
        issueListStart = Math.max(0, Math.min(filtered.size() - 1, issueListStart + (delta > 0 ? -1 : 1)));
        return true;
    }

    private List<EditableValidationIssue> filteredIssues() {
        List<EditableValidationIssue> all = screen.getController().issues();
        if (!errorsOnly) return all;
        List<EditableValidationIssue> filtered = new ArrayList<>();
        for (EditableValidationIssue issue : all) {
            if (issue.severity().name().equals("ERROR")) filtered.add(issue);
        }
        return filtered;
    }

    private void renderIssueTooltip(GuiGraphics g, int mouseX, int mouseY, EditableValidationIssue issue, int maxRight) {
        String t1 = issue.severity().name() + "  " + issue.path();
        String t2 = issue.message();
        int w = Math.min(260, Math.max(screen.getUiFont().width(t1), screen.getUiFont().width(t2)) + 12);
        int h = 26;
        int x = mouseX + 10;
        int y = mouseY + 8;
        if (x + w > maxRight) x = mouseX - w - 10;
        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x0B1016, 230));
        g.fill(x, y, x + w, y + 1, HudAnimUtil.withAlpha(0x66CCFF, 210));
        g.drawString(screen.getUiFont(), t1, x + 6, y + 5, HudAnimUtil.withAlpha(0xFFFFFF, 255), false);
        g.drawString(screen.getUiFont(), t2, x + 6, y + 15, HudAnimUtil.withAlpha(0xDDDDDD, 255), false);
    }

    private record IssueHitBox(int x, int y, int width, int height, EditableValidationIssue issue) {
        private boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx <= x + width && my <= y + height;
        }
    }
}
