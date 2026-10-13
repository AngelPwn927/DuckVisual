#version 150

// 9-tap gaussian in 5 fetches (bilinear taps between texels). Direction = step in UV per tap.
uniform sampler2D Sampler0;
uniform vec2 Direction;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, texCoord0).rgb * 0.2270270;
    c += (texture(Sampler0, texCoord0 + Direction * 1.3846154).rgb + texture(Sampler0, texCoord0 - Direction * 1.3846154).rgb) * 0.3162162;
    c += (texture(Sampler0, texCoord0 + Direction * 3.2307692).rgb + texture(Sampler0, texCoord0 - Direction * 3.2307692).rgb) * 0.0702703;
    fragColor = vec4(c, 1.0);
}
