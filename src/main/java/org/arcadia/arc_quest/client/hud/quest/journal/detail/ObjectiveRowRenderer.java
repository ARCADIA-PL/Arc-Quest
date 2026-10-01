package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.quest.icon.*;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalMarqueeTextRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalTooltipRequest;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import java.util.ArrayList;
import java.util.List;

/** Both phase presentations use this layout, frame selection and interaction path. */
final class ObjectiveRowRenderer {
    private ObjectiveRowRenderer() {}
    static ObjectiveRowLayout layout(QuestJournalScreen screen, ObjectiveIconContext context, int width, boolean compact) {
        boolean hasIcon = screen.getObjectiveIcons().resolve(context).available();
        var initial = ObjectiveRowLayout.measure(width, screen.getFont().lineHeight, 1,
                countWidth(screen, context), hasIcon, compact);
        int lines = compact ? 1 : HudRenderUtil.wrapText(text(context), initial.textWidth(), screen.getFont()).size();
        return ObjectiveRowLayout.measure(width, screen.getFont().lineHeight, lines,
                countWidth(screen, context), hasIcon, compact);
    }
    private static boolean showCount(ObjectiveIconContext context) {
        return context.objective().getType().isCounting() && context.requiredCount() > 1;
    }
    private static int countWidth(QuestJournalScreen screen, ObjectiveIconContext context) {
        return showCount(context) ? (int) Math.ceil(screen.getFont().width(context.progress() + " / " + context.requiredCount()) * 0.7f) : 0;
    }
    private static String text(ObjectiveIconContext context) {
        return (context.progress() >= context.requiredCount() ? "✔ " : "○ ") + context.objective().getDisplayText().getString();
    }
    static Result render(QuestJournalScreen screen, GuiGraphics graphics, ObjectiveIconContext context,
                         Area area, boolean compact, boolean interactive, float ratio, int theme, int alpha,
                         JournalMarqueeTextRenderer.ScissorController scissor) {
        var objective = context.objective();
        if (objective.isHidden()) return new Result(0, false, false);
        var font = screen.getFont();
        var layout = layout(screen, context, area.width, compact);
        boolean visible = area.absX < area.clipX2 && area.absX + area.width > area.clipX1
                && area.absY < area.clipY2 && area.absY + layout.height() > area.clipY1;
        if (!visible || alpha <= 4) return new Result(layout.height(), false, false);
        boolean hovered = interactive && area.containsMouse(layout.height());
        boolean iconHovered = hovered && layout.iconSize() > 0
                && area.mouseX < area.absX + layout.iconSize() && area.mouseY < area.absY + layout.iconSize();
        var selection = screen.getObjectiveIcons().select(context, hovered, interactive);
        boolean focused = interactive && screen.getObjectiveIcons().isFocused(context.key());
        boolean complete = context.progress() >= context.requiredCount();
        boolean canSubmit = interactive && !complete && ObjectiveType.OFFER.equals(objective.getType())
                && screen.getCurrentTab() == JournalTypes.Tab.ACTIVE;
        int color = complete ? 0x88FF88 : hovered && canSubmit ? theme : 0xDDDDDD;
        String display = text(context);
        if (layout.iconSize() > 0) {
            float hover = screen.getObjectiveIcons().hoverAmount(context, iconHovered || focused, screen.getDt());
            int edge = HudAnimUtil.withAlpha(HudAnimUtil.lerpColor(0xFFFFFF, theme, hover),
                    Math.round(alpha * (1f / 7f + hover * (0.32f - 1f / 7f))));
            int right = area.x + layout.iconSize(), bottom = area.y + layout.iconSize();
            graphics.fill(area.x, area.y, right, area.y + 1, edge);
            graphics.fill(area.x, bottom - 1, right, bottom, edge);
            graphics.fill(area.x, area.y + 1, area.x + 1, bottom - 1, edge);
            graphics.fill(right - 1, area.y + 1, right, bottom - 1, edge);
            selection.render(graphics, area.x + 2, area.y + 2, layout.iconSize() - 4, alpha / 255f);
        }
        if (compact) {
            JournalMarqueeTextRenderer.drawString(graphics, font, display,
                    area.x + layout.textX(), area.y, layout.textWidth(), HudAnimUtil.withAlpha(color, alpha), false,
                    area.absX + layout.textX(), area.absY, area.clipX1, area.clipY1, area.clipX2, area.clipY2, scissor);
        } else {
            List<String> lines = HudRenderUtil.wrapText(display, layout.textWidth(), font);
            int lineY = area.y;
            int maxLines = Math.max(1, (layout.progressY() - 4) / (font.lineHeight + 1));
            for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), area.x + layout.textX(), lineY, HudAnimUtil.withAlpha(color, alpha), false);
                lineY += font.lineHeight + 1;
            }
        }
        int barX = area.x + layout.textX(), barY = area.y + layout.progressY();
        graphics.fill(barX, barY, barX + layout.barWidth(), barY + 2, HudAnimUtil.withAlpha(0xFFFFFF, alpha / 7));
        int fill = (int) (layout.barWidth() * Math.max(0, Math.min(1, ratio)));
        if (fill > 0) graphics.fill(barX, barY, barX + fill, barY + 2, HudAnimUtil.withAlpha(complete ? 0x66FF66 : theme, alpha));
        if (showCount(context)) {
            graphics.pose().pushPose();
            graphics.pose().translate(area.x + layout.countX(), barY - 2, 0);
            String count = context.progress() + " / " + context.requiredCount();
            float countScale = Math.min(0.7f, (float) Math.max(1, area.width - layout.countX()) / Math.max(1, font.width(count)));
            graphics.pose().scale(countScale, countScale, 1);
            graphics.drawString(font, count, 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, alpha), false);
            graphics.pose().popPose();
        }
        if (interactive) {
            JeiScreenIngredients.objective(screen, graphics, context, area.x, area.y, area.width, layout.height());
            if (layout.iconSize() > 0) {
                JeiScreenIngredients.objectiveCandidate(screen, graphics, context, selection,
                        area.x, area.y, layout.iconSize(), layout.iconSize());
                float scale = screen.getUiScale();
                screen.getObjectiveIcons().recordFocus(context.key(), Math.max(area.absX, area.clipX1) * scale,
                        Math.max(area.absY, area.clipY1) * scale,
                        Math.min(area.absX + layout.iconSize(), area.clipX2) * scale,
                        Math.min(area.absY + layout.iconSize(), area.clipY2) * scale, context.generation());
            }
        }
        if (focused || hovered && !screen.getObjectiveIcons().hasFocus()) {
            requestTooltip(screen, context, selection, iconHovered || focused);
        }
        if (canSubmit && hovered) screen.requestPointerCursor();
        return new Result(layout.height(), hovered, canSubmit);
    }

    static void requestTooltip(QuestJournalScreen screen, ObjectiveIconContext context, IconFrameSelection selection, boolean onIcon) {
        List<Component> extra = new ArrayList<>();
        var objective = context.objective();
        int targetCount = screen.getObjectiveIcons().targetCount(context);
        ItemStack stack = selection.isItem() ? selection.stack()
                : targetCount == 1 && !objective.hasTargetTag() ? screen.getObjectiveIcons().singleTarget(context) : ItemStack.EMPTY;
        String identity = context.key() + "/" + selection.candidateKey() + "/" + context.generation();
        if (onIcon) {
            if (objective.hasTargetTag()) {
                Component tag = objective.getTargetTagTranslationKey() == null
                        ? Component.literal(String.valueOf(objective.getTargetTagId()))
                        : Component.translatable(objective.getTargetTagTranslationKey());
                extra.add(Component.translatable("arc_quest.gui.objective.icon.tag_short", tag)
                        .withStyle(ChatFormatting.GRAY));
            } else if (stack.isEmpty()) {
                var entity = ObjectiveType.KILL.equals(objective.getType())
                        ? ForgeRegistries.ENTITY_TYPES.getValue(objective.getTargetId()) : null;
                extra.add(entity == null ? objective.getDisplayText() : entity.getDescription());
            }
            screen.requestTooltip(new JournalTooltipRequest(identity, stack, extra));
            return;
        }
        // Text/overview hover explains the objective once; item inspection belongs to the icon.
        extra.add(objective.getDisplayText());
        if (ObjectiveType.OFFER.equals(objective.getType()) && context.progress() < context.requiredCount()
                && screen.getCurrentTab() == JournalTypes.Tab.ACTIVE)
            extra.add(Component.translatable("arc_quest.gui.journal.label.click_to_submit").withStyle(ChatFormatting.GRAY));
        screen.requestTooltip(new JournalTooltipRequest(identity, ItemStack.EMPTY, extra));
    }
    record Result(int height, boolean hovered, boolean canSubmit) {}
    record Area(int x, int y, int width, int absX, int absY, int mouseX, int mouseY,
                int clipX1, int clipY1, int clipX2, int clipY2) {
        boolean containsMouse(int height) {
            return mouseX >= absX && mouseX < absX + width && mouseY >= absY && mouseY < absY + height
                    && mouseX >= clipX1 && mouseX < clipX2 && mouseY >= clipY1 && mouseY < clipY2;
        }
    }
}
