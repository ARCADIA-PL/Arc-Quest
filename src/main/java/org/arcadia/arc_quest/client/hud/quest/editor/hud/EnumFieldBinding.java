package org.arcadia.arc_quest.client.hud.quest.editor.hud;

import net.minecraft.client.gui.components.EditBox;

import java.util.List;

public class EnumFieldBinding {
    private final EnumSelectWidget widget;
    private final EditBox input;

    public EnumFieldBinding(EnumSelectWidget widget, EditBox input) {
        this.widget = widget;
        this.input = input;
    }

    public void configure(String label, List<String> options, String currentValue) {
        widget.label = label == null ? "" : label;
        widget.setOptions(options);
        widget.value = currentValue == null ? "" : currentValue;
        input.setValue(widget.value);
        input.setEditable(false);
    }

    public boolean onClick(double mx, double my) {
        if (!widget.mouseClicked(mx, my)) return false;
        input.setValue(widget.value == null ? "" : widget.value);
        return true;
    }
}
