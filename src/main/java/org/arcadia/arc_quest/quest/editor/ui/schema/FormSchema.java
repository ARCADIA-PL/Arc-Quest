package org.arcadia.arc_quest.quest.editor.ui.schema;

import java.util.ArrayList;
import java.util.List;

public class FormSchema {
    public String formId = "";
    public String title = "";
    public String objectType = "";
    public List<FieldSchema> fields = new ArrayList<>();
    public List<String> layoutHints = new ArrayList<>();
}
