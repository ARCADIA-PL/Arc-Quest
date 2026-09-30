package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.ArrayList;

/** Resource-pack AUTO override. Invalid overrides are retained as NONE by the loader. */
record EntityPortraitRule(Kind kind, ResourceLocation texture, TexturePortraitDefinition.Rect region,
                          TexturePortraitDefinition portrait) {
    enum Kind { NONE, TEXTURE, ENTITY_TEXTURE_PORTRAIT }
    static final EntityPortraitRule NONE = new EntityPortraitRule(Kind.NONE, null, null, null);

    static EntityPortraitRule parse(JsonElement json) {
        JsonObject object = object(json, "entity portrait");
        String type = string(object, "type");
        return switch (type) {
            case "arc_quest:none" -> NONE;
            case "arc_quest:texture" -> new EntityPortraitRule(Kind.TEXTURE,
                    resource(object, "texture"), optional(object, "region")
                    ? rect(object.get("region"), "region") : null, null);
            case "arc_quest:entity_texture_portrait" -> new EntityPortraitRule(Kind.ENTITY_TEXTURE_PORTRAIT,
                    null, null, texturePortrait(object));
            default -> throw new IllegalArgumentException("Unsupported entity portrait type: " + type);
        };
    }

    static TexturePortraitDefinition texturePortrait(JsonObject object) {
        ResourceLocation texture = resource(object, "texture");
        var reference = size(object.get("referenceSize"), "referenceSize");
        var canvas = size(object.get("canvasSize"), "canvasSize");
        JsonElement raw = object.get("layers");
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().isEmpty()
                || raw.getAsJsonArray().size() > TexturePortraitDefinition.MAX_LAYERS)
            throw new IllegalArgumentException("Portrait layers must be a nonempty array of at most 32 layers");
        var layers = new ArrayList<TexturePortraitDefinition.Layer>();
        for (JsonElement element : raw.getAsJsonArray()) {
            JsonObject layer = object(element, "layer");
            layers.add(new TexturePortraitDefinition.Layer(rect(layer.get("region"), "region"),
                    rect(layer.get("destination"), "destination"), bool(layer, "flipX"),
                    bool(layer, "flipY"), optional(layer, "tint") ? tint(layer.get("tint")) : 0xFFFFFFFF));
        }
        return new TexturePortraitDefinition(texture, reference, canvas, layers);
    }

    private static TexturePortraitDefinition.Size size(JsonElement value, String name) {
        JsonObject o = object(value, name);
        return new TexturePortraitDefinition.Size(integer(o, "width"), integer(o, "height"));
    }
    private static TexturePortraitDefinition.Rect rect(JsonElement value, String name) {
        JsonObject o = object(value, name);
        return new TexturePortraitDefinition.Rect(integer(o, "x"), integer(o, "y"),
                integer(o, "width"), integer(o, "height"));
    }
    private static JsonObject object(JsonElement value, String name) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        return value.getAsJsonObject();
    }
    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(name + " must be a string");
        return value.getAsString();
    }
    private static ResourceLocation resource(JsonObject object, String name) {
        ResourceLocation id = ResourceLocation.tryParse(string(object, name));
        if (id == null) throw new IllegalArgumentException(name + " must be a resource location");
        return id;
    }
    private static int integer(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(name + " must be an integer");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException(name + " must be an in-range integer", ex); }
    }
    private static boolean bool(JsonObject object, String name) {
        if (!optional(object, name)) return false;
        JsonElement value = object.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(name + " must be boolean");
        return value.getAsBoolean();
    }
    private static boolean optional(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull();
    }
    private static int tint(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String hex = value.getAsString();
            if (!hex.matches("#[0-9a-fA-F]{8}")) throw new IllegalArgumentException("tint must be #AARRGGBB");
            return (int) Long.parseLong(hex.substring(1), 16);
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                BigInteger bits = value.getAsBigDecimal().toBigIntegerExact();
                if (bits.signum() >= 0 && bits.bitLength() <= 32) return bits.intValue();
            } catch (ArithmeticException ignored) { }
        }
        throw new IllegalArgumentException("tint must be unsigned 32-bit ARGB or #AARRGGBB");
    }
}
