#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec2 OutlineStep;
uniform float OutlineOpacity;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec4 item = texture(Sampler0, texCoord0);
    float outerAlpha = item.a;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            outerAlpha = max(outerAlpha, texture(Sampler0, texCoord0 + vec2(x, y) * OutlineStep).a);
        }
    }
    // The native item has premultiplied RGB. Add white only to the newly expanded
    // silhouette, preserving interior colors and the item's own transparency.
    float edgeAlpha = max(0.0, outerAlpha - item.a) * OutlineOpacity;
    fragColor = (item + vec4(vec3(edgeAlpha), edgeAlpha)) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}
