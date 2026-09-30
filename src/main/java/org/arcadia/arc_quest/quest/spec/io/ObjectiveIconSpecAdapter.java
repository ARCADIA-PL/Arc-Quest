package org.arcadia.arc_quest.quest.spec.io;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** Canonical JSON for an icon. Validation also runs when compiling client presentation data. */
public final class ObjectiveIconSpecAdapter extends TypeAdapter<ObjectiveIconSpec> {
    @Override
    public ObjectiveIconSpec read(JsonReader in) throws IOException {
        String path = in.getPath();
        try {
            JsonElement node = JsonParser.parseReader(in);
            if (node.isJsonNull()) return ObjectiveIcons.auto();
            if (!node.isJsonObject()) throw new IllegalArgumentException("icon must be an object");
            JsonObject object = node.getAsJsonObject();
            fields(object, Set.of("type", "texture", "region", "provider"), "icon");
            String type = string(object, "type");
            ObjectiveIconSpec.Mode mode = switch (type) {
                case "arc_quest:auto" -> ObjectiveIconSpec.Mode.AUTO;
                case "arc_quest:none" -> ObjectiveIconSpec.Mode.NONE;
                case "arc_quest:texture" -> ObjectiveIconSpec.Mode.TEXTURE;
                case "arc_quest:provider" -> ObjectiveIconSpec.Mode.PROVIDER;
                default -> throw new IllegalArgumentException("Unsupported icon.type: " + type);
            };
            ObjectiveIconSpec.Region region = null;
            if (present(object, "region")) {
                if (!object.get("region").isJsonObject()) throw new IllegalArgumentException("icon.region must be an object");
                JsonObject r = object.getAsJsonObject("region");
                fields(r, Set.of("x", "y", "width", "height"), "icon.region");
                region = new ObjectiveIconSpec.Region(integer(r, "x"), integer(r, "y"),
                        integer(r, "width"), integer(r, "height"));
            }
            return ObjectiveIcons.normalize(new ObjectiveIconSpec(mode, resource(object, "texture"),
                    region, resource(object, "provider")));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JsonParseException(path + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void write(JsonWriter out, ObjectiveIconSpec value) throws IOException {
        ObjectiveIconSpec icon = ObjectiveIcons.normalize(value);
        // Gson's normal field writer omits nulls, so default AUTO needs no JSON field.
        if (icon.mode() == ObjectiveIconSpec.Mode.AUTO) { out.nullValue(); return; }
        out.beginObject();
        out.name("type").value("arc_quest:" + icon.mode().name().toLowerCase(Locale.ROOT));
        if (icon.texture() != null) out.name("texture").value(icon.texture().toString());
        if (icon.provider() != null) out.name("provider").value(icon.provider().toString());
        if (icon.region() != null) {
            var r = icon.region();
            out.name("region").beginObject();
            out.name("x").value(r.x());
            out.name("y").value(r.y());
            out.name("width").value(r.width());
            out.name("height").value(r.height());
            out.endObject();
        }
        out.endObject();
    }

    private static boolean present(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull();
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("icon." + name + " must be a string");
        }
        return value.getAsString();
    }

    private static ResourceLocation resource(JsonObject object, String name) {
        if (!present(object, name)) return null;
        String raw = string(object, name);
        ResourceLocation id = raw.isBlank() ? null : ResourceLocation.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid icon." + name + ": " + raw);
        return id;
    }

    private static int integer(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("icon.region." + name + " must be an integer");
        }
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("icon.region." + name + " must be a 32-bit integer", e);
        }
    }

    private static void fields(JsonObject object, Set<String> allowed, String path) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unsupported field " + path + "." + key);
        }
    }
}
