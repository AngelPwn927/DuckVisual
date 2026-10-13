#version 150

// Duck Visual colour grading: saturation, vibrance (boosts dull colours more than vivid ones,
// so skin and the sky don't burn out), contrast and brightness.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Saturation;
uniform float Vibrance;
uniform float Contrast;
uniform float Brightness;

in vec2 texCoord0;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec3 c = texture(Sampler0, texCoord0).rgb;

    c = mix(vec3(dot(c, LUMA)), c, Saturation);

    float chroma = max(c.r, max(c.g, c.b)) - min(c.r, min(c.g, c.b));
    c = mix(vec3(dot(c, LUMA)), c, 1.0 + Vibrance * (1.0 - clamp(chroma, 0.0, 1.0)));

    c = (c - 0.5) * Contrast + 0.5;
    c *= Brightness;

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0) * ColorModulator;
}
