#version 150

// macOS-like frosted glass: the blurred frame behind the panel, a bit more saturated
// ("vibrancy"), tinted with the vertex colour (alpha = tint strength).
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Vibrancy;

in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec3 b = texture(Sampler0, texCoord0).rgb;
    float l = dot(b, vec3(0.299, 0.587, 0.114));
    b = clamp(mix(vec3(l), b, Vibrancy), 0.0, 1.0);
    fragColor = vec4(mix(b, vertexColor.rgb, vertexColor.a), 1.0) * ColorModulator;
}
