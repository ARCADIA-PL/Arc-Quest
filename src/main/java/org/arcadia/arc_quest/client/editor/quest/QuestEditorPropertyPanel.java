package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class QuestEditorPropertyPanel {
    private static final int WIDTH = 330;
    private static final int ROW_HEIGHT = 22;
    private final Deque<Node> stack = new ArrayDeque<>();
    private Field editingField;
    private Object editingOwner;
    private List<Object> editingList;
    private int editingIndex = -1;
    private java.util.Map<Object, Object> editingMap;
    private Object editingMapKey;
    private String input = "";
    private int scroll;
    private boolean questMode;

    int reservedWidth(HudRect workspace) { return Math.min(WIDTH, Math.max(180, workspace.width() * 55 / 100)); }

    void reset(QuestEditorDocumentController controller, PhaseSpec phase) {
        stack.clear();
        stack.push(new Node(questMode ? "QUEST" : "PHASE", questMode ? controller.document() : phase, null));
        editingField = null;
        scroll = 0;
    }

    void render(GuiGraphics graphics, Font font, HudRect workspace, QuestEditorDocumentController controller,
                PhaseSpec phase, int mouseX, int mouseY) {
        ensureRoot(controller, phase);
        HudRect panel = bounds(workspace);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), HudAnimUtil.withAlpha(QuestEditorTheme.SURFACE, 244));
        frame(graphics, panel, HudAnimUtil.withAlpha(QuestEditorTheme.BORDER, 220));
        tab(graphics, font, panel.x() + 8, panel.y() + 7, 62, "QUEST", questMode, mouseX, mouseY);
        tab(graphics, font, panel.x() + 74, panel.y() + 7, 62, "PHASE", !questMode, mouseX, mouseY);
        if (stack.size() > 1) graphics.drawString(font, "< BACK", panel.x() + 144, panel.y() + 12, QuestEditorTheme.TEXT_SECONDARY, false);
        Node node = stack.peek();
        graphics.drawString(font, node == null ? "" : node.name, panel.x() + 8, panel.y() + 35, QuestEditorTheme.TEXT_PRIMARY, false);
        List<Row> rows = rows(node);
        int y = panel.y() + 52 - scroll;
        graphics.enableScissor(panel.x() + 1, panel.y() + 49, panel.right() - 1, panel.bottom() - 1);
        for (Row row : rows) {
            if (y + ROW_HEIGHT >= panel.y() + 49 && y < panel.bottom()) renderRow(graphics, font, panel.x() + 7, y, panel.width() - 14, row);
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
        int errors = controller.issues().size();
        if (errors > 0) graphics.drawString(font, errors + " validation issue(s)", panel.x() + 155, panel.y() + 12,
                controller.hasErrors() ? QuestEditorTheme.DANGER : QuestEditorTheme.TEXT_MUTED, false);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button, HudRect workspace,
                         QuestEditorDocumentController controller, PhaseSpec phase) {
        HudRect panel = bounds(workspace);
        if (!panel.contains(mouseX, mouseY)) return false;
        if (mouseY < panel.y() + 30) {
            if (mouseX < panel.x() + 70) questMode = true;
            else if (mouseX < panel.x() + 138) questMode = false;
            else if (stack.size() > 1) stack.pop();
            reset(controller, phase);
            return true;
        }
        Node node = stack.peek();
        List<Row> rows = rows(node);
        int index = (int) ((mouseY - (panel.y() + 52) + scroll) / ROW_HEIGHT);
        if (index < 0 || index >= rows.size()) return true;
        Row row = rows.get(index);
        if (row.field != null) editField(row, controller);
        else if (row.owner instanceof List<?> rawList) editListRow(row, rawList, button, controller);
        else if (row.owner instanceof java.util.Map<?, ?> rawMap) editMapRow(row, rawMap, button, controller);
        else if (row.value != null) stack.push(new Node(row.label, row.value, row.type));
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta, HudRect workspace) {
        if (!bounds(workspace).contains(mouseX, mouseY)) return false;
        scroll = Math.max(0, scroll - (int) Math.round(delta * 24));
        return true;
    }

    boolean keyPressed(int keyCode, QuestEditorDocumentController controller) {
        if (editingField == null && editingList == null && editingMap == null) return false;
        if (keyCode == 257 || keyCode == 335) { commit(controller); return true; }
        if (keyCode == 256) { editingField = null; return true; }
        if (keyCode == 259 && !input.isEmpty()) { input = input.substring(0, input.length() - 1); return true; }
        return true;
    }

    boolean charTyped(char character) {
        if ((editingField == null && editingList == null && editingMap == null) || Character.isISOControl(character) || input.length() >= 4096)
            return editingField != null || editingList != null || editingMap != null;
        input += character;
        return true;
    }

    private void editField(Row row, QuestEditorDocumentController controller) {
        Class<?> type = row.field.getType();
        try {
            if (type == boolean.class || type == Boolean.class) {
                controller.mutate(spec -> set(row.field, row.owner, !Boolean.TRUE.equals(row.value)));
            } else if (type.isEnum()) {
                Object[] values = type.getEnumConstants();
                int index = row.value == null ? -1 : java.util.Arrays.asList(values).indexOf(row.value);
                controller.mutate(spec -> set(row.field, row.owner, values[(index + 1) % values.length]));
            } else if (isScalar(type)) {
                editingField = row.field;
                editingOwner = row.owner;
                input = row.value == null ? "" : String.valueOf(row.value);
            } else if (row.value != null) {
                stack.push(new Node(row.label, row.value, row.field.getGenericType()));
            } else {
                Object created = type.getDeclaredConstructor().newInstance();
                controller.mutate(spec -> set(row.field, row.owner, created));
                stack.push(new Node(row.label, created, row.field.getGenericType()));
            }
        } catch (Exception ignored) { }
    }

    private void commit(QuestEditorDocumentController controller) {
        Field field = editingField; Object owner = editingOwner; String value = input;
        if (editingMap != null) {
            Object key = editingMapKey;
            Object old = editingMap.get(key);
            Class<?> type = old == null ? String.class : old.getClass();
            controller.mutate(spec -> editingMap.put(key, parse(type, value)));
        } else if (editingList != null) {
            int index = editingIndex;
            Class<?> type = editingList.get(index).getClass();
            controller.mutate(spec -> editingList.set(index, parse(type, value)));
        } else controller.mutate(spec -> set(field, owner, parse(field.getType(), value)));
        editingField = null;
        editingList = null;
        editingIndex = -1;
        editingMap = null;
        editingMapKey = null;
    }

    private List<Row> rows(Node node) {
        List<Row> rows = new ArrayList<>();
        if (node == null || node.value == null) return rows;
        if (node.value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) rows.add(new Row("[" + i + "]", null, list, list.get(i), node.type));
            rows.add(new Row("+ ADD", null, list, null, node.type));
            return rows;
        }
        if (node.value instanceof java.util.Map<?, ?> map) {
            map.forEach((key, value) -> rows.add(new Row(String.valueOf(key), null, map, value, node.type)));
            rows.add(new Row("+ ADD", null, map, null, node.type));
            return rows;
        }
        for (Field field : node.value.getClass().getFields()) {
            try { rows.add(new Row(field.getName(), field, node.value, field.get(node.value), field.getGenericType())); }
            catch (IllegalAccessException ignored) { }
        }
        return rows;
    }

    private void renderRow(GuiGraphics graphics, Font font, int x, int y, int width, Row row) {
        graphics.fill(x, y, x + width, y + ROW_HEIGHT - 2, HudAnimUtil.withAlpha(QuestEditorTheme.SURFACE_HOVER, 150));
        graphics.drawString(font, row.label, x + 5, y + 7, QuestEditorTheme.TEXT_SECONDARY, false);
        boolean listEditing = editingList == row.owner && row.label.equals("[" + editingIndex + "]");
        boolean mapEditing = editingMap == row.owner && editingMapKey != null
                && String.valueOf(editingMapKey).equals(row.label);
        String value = (editingField != null && editingField == row.field && editingOwner == row.owner) || listEditing || mapEditing
                ? input + "_" : display(row.value);
        graphics.drawString(font, font.plainSubstrByWidth(value, width - 132), x + 126, y + 7,
                editingField == row.field ? QuestEditorTheme.SELECTED : QuestEditorTheme.TEXT_PRIMARY, false);
    }

    @SuppressWarnings("unchecked")
    private void editListRow(Row row, List<?> rawList, int button, QuestEditorDocumentController controller) {
        List<Object> list = (List<Object>) rawList;
        int index = row.label.startsWith("[") ? Integer.parseInt(row.label.substring(1, row.label.length() - 1)) : -1;
        if (index < 0) {
            Object created = createListElement(row.type);
            if (created != null) controller.mutate(spec -> list.add(created));
            return;
        }
        if (button == 1) { controller.mutate(spec -> list.remove(index)); return; }
        if (button == 2 && index > 0) { controller.mutate(spec -> java.util.Collections.swap(list, index, index - 1)); return; }
        Object value = list.get(index);
        if (value == null || isScalar(value.getClass())) {
            editingField = null; editingOwner = null; editingList = list; editingIndex = index;
            input = value == null ? "" : String.valueOf(value);
        } else stack.push(new Node(row.label, value, row.type));
    }

    private static Object createListElement(java.lang.reflect.Type type) {
        try {
            if (!(type instanceof ParameterizedType parameterized)) return null;
            java.lang.reflect.Type elementType = parameterized.getActualTypeArguments()[0];
            if (elementType == String.class) return "";
            if (elementType instanceof Class<?> elementClass) return elementClass.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) { }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void editMapRow(Row row, java.util.Map<?, ?> rawMap, int button, QuestEditorDocumentController controller) {
        java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) rawMap;
        if ("+ ADD".equals(row.label)) {
            String key = "entry_" + (map.size() + 1);
            Object value = createMapValue(row.type);
            controller.mutate(spec -> map.put(key, value));
            return;
        }
        Object key = map.keySet().stream().filter(candidate -> String.valueOf(candidate).equals(row.label)).findFirst().orElse(row.label);
        if (button == 1) { controller.mutate(spec -> map.remove(key)); return; }
        Object value = map.get(key);
        if (value == null || isScalar(value.getClass())) {
            editingField = null; editingOwner = null; editingMap = map; editingMapKey = key;
            input = value == null ? "" : String.valueOf(value);
        } else stack.push(new Node(row.label, value, row.type));
    }

    private static Object createMapValue(java.lang.reflect.Type type) {
        try {
            if (!(type instanceof ParameterizedType parameterized)) return "";
            java.lang.reflect.Type valueType = parameterized.getActualTypeArguments()[1];
            if (valueType == String.class) return "";
            if (valueType instanceof Class<?> valueClass) return valueClass.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) { }
        return "";
    }

    private void ensureRoot(QuestEditorDocumentController controller, PhaseSpec phase) {
        if (stack.isEmpty() || (stack.size() == 1 && stack.peek().value != (questMode ? controller.document() : phase))) reset(controller, phase);
    }

    private static boolean isScalar(Class<?> type) { return type == String.class || type.isPrimitive() || Number.class.isAssignableFrom(type); }
    private static Object parse(Class<?> type, String value) {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == float.class || type == Float.class) return Float.parseFloat(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        return value;
    }
    private static void set(Field field, Object owner, Object value) { try { field.set(owner, value); } catch (IllegalAccessException ignored) { } }
    private static String display(Object value) {
        if (value == null) return "+ create";
        if (value instanceof List<?> list) return list.size() + " entries >";
        if (value instanceof java.util.Map<?,?> map) return map.size() + " entries >";
        if (isScalar(value.getClass()) || value instanceof Enum<?> || value instanceof Boolean) return String.valueOf(value);
        return value.getClass().getSimpleName() + " >";
    }
    private HudRect bounds(HudRect workspace) { int width = reservedWidth(workspace); return new HudRect(workspace.right() - width, workspace.y(), width, workspace.height()); }
    private static void frame(GuiGraphics graphics, HudRect rect, int color) { graphics.fill(rect.x(), rect.y(), rect.right(), rect.y()+1,color); graphics.fill(rect.x(),rect.bottom()-1,rect.right(),rect.bottom(),color); graphics.fill(rect.x(),rect.y(),rect.x()+1,rect.bottom(),color); graphics.fill(rect.right()-1,rect.y(),rect.right(),rect.bottom(),color); }
    private static void tab(GuiGraphics graphics, Font font, int x, int y, int width, String text, boolean active, int mx, int my) { graphics.fill(x,y,x+width,y+20,HudAnimUtil.withAlpha(active?QuestEditorTheme.SELECTED:QuestEditorTheme.SURFACE_HOVER,active?75:150)); graphics.drawCenteredString(font,text,x+width/2,y+6,active?QuestEditorTheme.TEXT_PRIMARY:QuestEditorTheme.TEXT_MUTED); }
    private record Node(String name, Object value, java.lang.reflect.Type type) { }
    private record Row(String label, Field field, Object owner, Object value, java.lang.reflect.Type type) { }
}
