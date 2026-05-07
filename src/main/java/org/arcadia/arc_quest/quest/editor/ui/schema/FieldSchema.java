package org.arcadia.arc_quest.quest.editor.ui.schema;

import java.util.ArrayList;
import java.util.List;

public class FieldSchema {
    public String key = "";
    public String label = "";
    public FieldType type = FieldType.TEXT;
    public String path = "";
    public String defaultValue = "";
    public boolean required = false;
    public String uiWidgetHint = "";
    public List<String> enumOptions = new ArrayList<>();
    public List<String> validatorRefs = new ArrayList<>();
    public String placeholder = "";
    public String helpText = "";
}
